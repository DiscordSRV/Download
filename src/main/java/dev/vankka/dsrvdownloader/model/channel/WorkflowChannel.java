package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionArtifactConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.discord.DiscordWebhook;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.WorkflowFileMetadata;
import dev.vankka.dsrvdownloader.model.exception.InclusionException;
import dev.vankka.dsrvdownloader.model.github.*;
import dev.vankka.dsrvdownloader.util.HttpContentUtil;
import dev.vankka.dsrvdownloader.util.IO;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tomcat.util.buf.HexUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WorkflowChannel extends AbstractVersionChannel {

    private static final String METADATA_EXTENSION = ".metadata";
    private static final int WORKFLOWS_PER_PAGE = 100;
    private static final int WORKFLOWS_RUNS_PER_PAGE = 100;

    private static final int ARTIFACT_REATTEMPTS = 5;
    private static final long ARTIFACT_REATTEMPT_DELAY = TimeUnit.SECONDS.toMillis(5);

    private Workflow workflow;
    private List<WorkflowRun> workflowRuns;

    public WorkflowChannel(ConfigManager configManager, DiscordWebhook discordWebhook, VersionChannelConfig config) {
        super(configManager, discordWebhook, config);
        updateWorkflows();
        if (workflow == null || workflowRuns == null || workflowRuns.isEmpty()) {
            return;
        }
        loadFilesAndCleanupStore();
    }

    private void updateWorkflows() {
        int page = 0;
        while (workflow == null) {
            Request request = new Request.Builder()
                    .url(baseRepoApiUrl() + "/actions/workflows?per_page=" + WORKFLOWS_PER_PAGE + "&page=" + (++page))
                    .get().build();

            try (Response response = configManager.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflows for " + describe() + " (" + request.url() + "): "
                                    + HttpContentUtil.prettify(response, body));
                    return;
                }

                WorkflowPaging workflowPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowPaging.class);
                for (Workflow wf : workflowPaging.workflows()) {
                    if (wf.path().endsWith(config.workflowFile)) {
                        workflow = wf;
                        break;
                    }
                }
                if (workflowPaging.total_count() < WORKFLOWS_PER_PAGE) {
                    break;
                }
            } catch (IOException e) {
                Downloader.LOGGER.error("Failed to get workflows for " + describe(), e);
                return;
            }
        }

        if (workflow == null) {
            Downloader.LOGGER.error("Workflow not found for " + describe());
            return;
        }

        int pages = config.pagesOfRunsToKeep;
        workflowRuns = new ArrayList<>(pages * WORKFLOWS_RUNS_PER_PAGE);
        for (int i = 0; i < pages; i++) {
            Request request = new Request.Builder()
                    .url(baseRepoApiUrl()
                                 + "/actions/workflows/" + Long.toUnsignedString(workflow.id()) + "/runs"
                                 + "?status=success&event=push&branch=" + config.branch
                                 + "&per_page=" + WORKFLOWS_RUNS_PER_PAGE + "&page=" + (i + 1))
                    .get().build();

            try (Response response = configManager.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow runs for " + describe() + " (" + request.url() + "): "
                                    + HttpContentUtil.prettify(response, body));
                    return;
                }

                WorkflowRunPaging workflowRunPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowRunPaging.class);
                if (workflowRunPaging == null || workflowRunPaging.workflow_runs() == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow runs for " + describe() + ": Failed to parse json");
                    return;
                }

                workflowRuns.addAll(workflowRunPaging.workflow_runs());
                if (workflowRunPaging.total_count() < WORKFLOWS_RUNS_PER_PAGE) {
                    break;
                }
            } catch (IOException e) {
                Downloader.LOGGER.error("Failed to get releases for  " + describe(), e);
            }
        }
    }

    private void loadFilesAndCleanupStore() {
        try {
            Path store = store();

            Map<String, Map<String, Triple<String, Path, Path>>> versions = new HashMap<>();
            try (Stream<Path> files = Files.list(store)) {
                files.forEach(folder -> {
                    String hash = folder.getFileName().toString();

                    try {
                        Map<String, Path> nonMetaPaths = new HashMap<>();
                        Map<String, Path> metaPaths = new HashMap<>();

                        try (Stream<Path> folderFiles = Files.list(folder)) {
                            folderFiles.forEach(file -> {
                                String fileName = file.getFileName().toString();
                                if (fileName.endsWith(METADATA_EXTENSION)) {
                                    metaPaths.put(fileName.substring(0, fileName.length() - METADATA_EXTENSION.length()), file);
                                } else {
                                    nonMetaPaths.put(fileName, file);
                                }
                            });
                        }

                        for (Map.Entry<String, Path> entry : metaPaths.entrySet()) {
                            if (!nonMetaPaths.containsKey(entry.getKey())) {
                                // Orphan metadata file
                                Files.delete(entry.getValue());
                            }
                        }

                        Map<String, Triple<String, Path, Path>> artifacts = new LinkedHashMap<>();
                        for (Map.Entry<String, Path> entry : nonMetaPaths.entrySet()) {
                            String fileName = entry.getKey();
                            Path file = entry.getValue();
                            Path metaFile = metaPaths.get(fileName);
                            if (metaFile == null) {
                                // Orphan main file
                                Files.delete(file);
                                continue;
                            }

                            WorkflowFileMetadata metadata = Downloader.OBJECT_MAPPER.readValue(Files.newInputStream(metaFile), WorkflowFileMetadata.class);
                            artifacts.put(metadata.identifier, Triple.of(fileName, file, metaFile));
                        }

                        if (artifacts.isEmpty()) {
                            Files.delete(folder);
                            return;
                        }

                        versions.put(hash, artifacts);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            for (int i = 0; i < Math.min(workflowRuns.size(), config.versionsToKeep); i++) {
                WorkflowRun run = workflowRuns.get(i);
                String hash = run.head_sha();

                Map<String, Triple<String, Path, Path>> diskVersion = versions.remove(hash);
                if (diskVersion != null) {
                    Map<String, Artifact> artifacts = new HashMap<>();

                    for (VersionArtifactConfig artifactConfig : config.artifacts) {
                        String artifactIdentifier = artifactConfig.identifier;

                        Triple<String, Path, Path> artifact = diskVersion.remove(artifactIdentifier);
                        if (artifact == null) {
                            continue;
                        }

                        String fileName = artifact.getLeft();
                        Path file = artifact.getMiddle();
                        Path metaFile = artifact.getRight();

                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] bytes = null;
                        try (IO io = new IO(Files.newInputStream(file)).withDigest(digest)) {
                            if (i < config.versionsToKeepInMemory) {
                                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                                io.withOutputStream(byteStream).stream();

                                bytes = Files.readAllBytes(file);
                            } else {
                                io.stream();
                            }
                        }

                        artifacts.put(
                                artifactIdentifier,
                                new Artifact(
                                        artifactIdentifier,
                                        fileName,
                                        file,
                                        metaFile,
                                        bytes,
                                        HexUtils.toHexString(digest.digest())
                                )
                        );
                    }

                    Commit headCommit = run.head_commit();
                    putVersion(new Version(hash, headCommit != null ? headCommit.message() : null, artifacts), false);
                    continue;
                }

                try {
                    includeRun(run, i < config.versionsToKeepInMemory, false);
                } catch (IOException | InclusionException | DigestException | NoSuchAlgorithmException e) {
                    setLastDiscordMessage(run.head_sha(), "[Boot] Failed to load release [`" + describe() + "`]", ExceptionUtils.getStackTrace(e));
                }
            }

            // Remove files that aren't needed (anymore)
            for (Map<String, Triple<String, Path, Path>> value : versions.values()) {
                for (Triple<String, Path, Path> triple : value.values()) {
                    Path file = triple.getMiddle();
                    Path metaFile = triple.getRight();

                    Files.delete(file);
                    Files.delete(metaFile);

                    // folder containing the two files
                    Files.delete(file.getParent());
                }
            }
        } catch (IOException | NoSuchAlgorithmException | DigestException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("BusyWait")
    private void includeRun(WorkflowRun run, boolean inMemory, boolean newVersion)
            throws IOException, InclusionException, DigestException, NoSuchAlgorithmException {
        String hash = run.head_sha();
        Path versionStore = store().resolve(hash);

        Request request = new Request.Builder()
                .url(baseRepoApiUrl() + "/actions/runs/" + Long.toUnsignedString(run.id()) + "/artifacts")
                .get().build();

        WorkflowArtifactPaging artifactPaging = null;
        int attempts = 0;
        while ((artifactPaging == null || artifactPaging.artifacts().size() == 0) && attempts < ARTIFACT_REATTEMPTS) {
            try (Response response = configManager.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new InclusionException(
                            "Failed to get workflow artifacts",
                            request.url() + " => " + HttpContentUtil.prettify(response, body));
                }

                artifactPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowArtifactPaging.class);
            }

            if (artifactPaging == null || !newVersion) {
                // If we can't parse the json or this is an existing run, break out of the loop
                break;
            }

            if (artifactPaging.artifacts().size() == 0) {
                attempts++;
                Downloader.LOGGER.warn(
                        "Retrying getting artifacts for " + describe() + " run "
                                + Long.toUnsignedString(run.id()) + " (Attempts: " + attempts + ")");
                try {
                    Thread.sleep(ARTIFACT_REATTEMPT_DELAY);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (artifactPaging == null) {
            throw new InclusionException("Failed to page workflow artifacts: failed to parse json");
        }

        Map<String, Artifact> artifactsByIdentifier = new LinkedHashMap<>();
        List<byte[]> zips = new ArrayList<>();

        for (WorkflowArtifact artifact : artifactPaging.artifacts()) {
            boolean any = false;

            for (VersionArtifactConfig artifactConfig : config.artifacts) {
                if (!artifact.expired() && Pattern.compile(artifactConfig.archiveNameFormat).matcher(artifact.name()).matches()) {
                    any = true;
                    break;
                }
            }

            if (!any) {
                continue;
            }

            Request downloadRequest = new Request.Builder()
                    .url(artifact.archive_download_url())
                    .get().build();

            try (Response downloadResponse = configManager.httpClient().newCall(downloadRequest).execute()) {
                ResponseBody responseBody = downloadResponse.body();
                if (!downloadResponse.isSuccessful() || responseBody == null) {
                    throw new InclusionException(
                            "Failed to download artifact " + artifact.name(),
                            downloadRequest.url() + " => " + HttpContentUtil.prettify(downloadResponse, responseBody));
                }

                zips.add(responseBody.bytes());
            } catch (IOException e) {
                throw new InclusionException("Failed to download artifact " + artifact.name());
            }
        }

        List<VersionArtifactConfig> availableConfigs = new ArrayList<>(config.artifacts);
        for (byte[] zip : zips) {
            try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
                ZipEntry zipEntry;
                while ((zipEntry = inputStream.getNextEntry()) != null) {
                    Path resolvedPath = versionStore.resolve(zipEntry.getName()).normalize();
                    if (!resolvedPath.startsWith(versionStore)) {
                        throw new RuntimeException("Entry with an illegal path: " + zipEntry.getName());
                    }

                    String fileName = resolvedPath.getFileName().toString();

                    boolean found = false;
                    for (VersionArtifactConfig artifactConfig : availableConfigs) {
                        String identifier = artifactConfig.identifier;

                        if (!Pattern.compile(artifactConfig.fileNameFormat).matcher(fileName).matches()) {
                            continue;
                        }

                        Path file = versionStore.resolve(fileName);
                        Path metaFile = versionStore.resolve(fileName + METADATA_EXTENSION);

                        if (!Files.exists(versionStore)) {
                            Files.createDirectories(versionStore);
                        }

                        byte[] bytes = null;
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");

                        try (IO io = new IO(inputStream, false)
                                .withDigest(digest)
                                .withOutputStream(Files.newOutputStream(file))
                        ) {
                            if (inMemory) {
                                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                                io.withOutputStream(byteStream).stream();

                                bytes = byteStream.toByteArray();
                            } else {
                                io.stream();
                            }
                        }

                        Downloader.OBJECT_MAPPER.writeValue(
                                Files.newOutputStream(metaFile),
                                new WorkflowFileMetadata(identifier)
                        );

                        artifactsByIdentifier.put(
                                identifier,
                                new Artifact(
                                        identifier,
                                        fileName,
                                        file,
                                        metaFile,
                                        inMemory ? bytes : null,
                                        HexUtils.toHexString(digest.digest())
                                )
                        );

                        availableConfigs.remove(artifactConfig);
                        found = true;
                        break;
                    }

                    if (!found) {
                        Downloader.LOGGER.info("Found no use for file " + fileName + " for " + describe() + " " + hash);
                    }
                }
            }
        }
        if (artifactsByIdentifier.isEmpty()) {
            throw new InclusionException("Failed to find any files matching workflow run");
        }

        Commit headCommit = run.head_commit();
        putVersion(new Version(hash, headCommit != null ? headCommit.message() : null, artifactsByIdentifier), newVersion);
    }

    @Override
    public int versionsBehind(String comparedTo, Consumer<String> versionConsumer) {
        for (int i = 0; i < workflowRuns.size(); i++) {
            String headSha = workflowRuns.get(i).head_sha();
            versionConsumer.accept(headSha);
            if (headSha.equals(comparedTo)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected String amountType(int amount) {
        return amount == 1 ? "build" : "builds";
    }

    @Override
    public void receiveWebhook(String event, JsonNode node) {
        if (!event.equals("workflow_run")
                || workflow == null || node.get("workflow_run").get("workflow_id").asLong() != workflow.id()) {
            return;
        }

        WorkflowRun workflowRun;
        try {
            workflowRun = Downloader.OBJECT_MAPPER.readValue(node.get("workflow_run").toString(), WorkflowRun.class);
        } catch (JsonProcessingException e) {
            Downloader.LOGGER.error("Failed to parse workflow run json", e);
            return;
        }

        if (!workflowRun.head_branch().equals(config.branch) || !workflowRun.event().equals("push")) {
            return;
        }

        String id = workflowRun.head_sha();
        Commit headCommit = workflowRun.head_commit();
        String description = headCommit != null ? headCommit.message() : null;
        if (description != null && description.contains("\n")) {
            description = description.substring(0, description.indexOf("\n"));
        }

        String action = node.get("action").asText();
        if (!action.equals("completed")) {
            if (action.equals("requested")) {
                waiting(id, description, "[workflow](" + workflowRun.html_url() + ") to run");
            }
            return;
        }

        if (!workflowRun.conclusion().equals("success")) {
            failed(id, description, "[workflow](" + workflowRun.html_url() + ") failure");
            return;
        }

        processing(id, description);
        workflowRuns.add(0, workflowRun);

        try {
            try {
                includeRun(workflowRun, config.versionsToKeepInMemory >= 1, true);
            } catch (IOException | DigestException | NoSuchAlgorithmException e) {
                throw new InclusionException(e);
            }

            success(id, description);
        } catch (InclusionException e) {
            failed(id, description, e.getMessage(), e.getLonger());
        }

        expireOldestVersion();
    }
}
