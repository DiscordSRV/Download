package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionArtifactConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.WorkflowFileMetadata;
import dev.vankka.dsrvdownloader.model.github.*;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WorkflowChannel extends AbstractVersionChannel {

    private static final String METADATA_EXTENSION = ".metadata";
    private static final int WORKFLOWS_PER_PAGE = 100;
    private static final int WORKFLOWS_RUNS_PER_PAGE = 100;

    private Workflow workflow;
    private List<WorkflowRun> workflowRuns;

    public WorkflowChannel(Downloader downloader, VersionChannelConfig config) {
        super(downloader, config);
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

            try (Response response = downloader.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflows for " + describe() + " (" + request.url() + "): "
                                    + (body != null ? body.string() : "(No body)"));
                    return;
                }

                WorkflowPaging workflowPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowPaging.class);
                for (Workflow wf : workflowPaging.workflows) {
                    if (wf.path.endsWith(config.workflowFile)) {
                        workflow = wf;
                        break;
                    }
                }
                if (workflowPaging.total_count < WORKFLOWS_PER_PAGE) {
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
                                 + "/actions/workflows/" + Long.toUnsignedString(workflow.id) + "/runs"
                                 + "?status=success&event=push&branch=" + config.branch
                                 + "&per_page=" + WORKFLOWS_RUNS_PER_PAGE + "&page=" + (i + 1))
                    .get().build();

            try (Response response = downloader.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow runs for " + describe() + " (" + request.url() + "): "
                                    + (body != null ? body.string() : "(No body)"));
                    return;
                }

                WorkflowRunPaging workflowRunPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowRunPaging.class);
                if (workflowRunPaging == null || workflowRunPaging.workflow_runs == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow runs for " + describe() + ": Failed to parse json");
                    return;
                }

                workflowRuns.addAll(workflowRunPaging.workflow_runs);
                if (workflowRunPaging.total_count < WORKFLOWS_RUNS_PER_PAGE) {
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
                                if (file.endsWith(METADATA_EXTENSION)) {
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

                        versions.put(hash, artifacts);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            for (int i = 0; i < Math.min(workflowRuns.size(), config.versionsToKeep); i++) {
                WorkflowRun run = workflowRuns.get(i);
                String hash = run.head_sha;

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

                        byte[] bytes = null;
                        if (i < config.versionsToKeepInMemory) {
                            bytes = Files.readAllBytes(file);
                        }

                        artifacts.put(artifactIdentifier, new Artifact(fileName, file, metaFile, bytes));
                    }

                    putVersion(new Version(hash, artifacts));
                    continue;
                }

                includeRun(run, i < config.versionsToKeepInMemory);
            }

            // Remove files that aren't needed (anymore)
            for (Map<String, Triple<String, Path, Path>> value : versions.values()) {
                for (Triple<String, Path, Path> triple : value.values()) {
                    Files.delete(triple.getMiddle());
                    Files.delete(triple.getRight());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void includeRun(WorkflowRun run, boolean inMemory) {
        String hash = run.head_sha;

        try {
            Path versionStore = store().resolve(hash);

            Request request = new Request.Builder()
                    .url(baseRepoApiUrl() + "/actions/runs/" + Long.toUnsignedString(run.id) + "/artifacts")
                    .get().build();

            WorkflowArtifactPaging artifactPaging;
            try (Response response = downloader.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow artifacts for " + describe() + " " + hash + " (" + request.url() + "): "
                                    + (body != null ? body.string() : "(No body)"));
                    return;
                }

                artifactPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowArtifactPaging.class);
            }

            if (artifactPaging == null) {
                Downloader.LOGGER.error("Failed to get workflow artifacts for " + describe()
                                                + " " + hash + ": failed to parse json");
                return;
            }

            Map<String, Artifact> artifactsByIdentifier = new LinkedHashMap<>();

            List<byte[]> zips = new ArrayList<>();

            for (WorkflowArtifact artifact : artifactPaging.artifacts) {
                boolean any = false;

                for (VersionArtifactConfig artifactConfig : config.artifacts) {
                    if (!artifact.expired && Pattern.compile(artifactConfig.archiveNameFormat).matcher(artifact.name).matches()) {
                        any = true;
                        break;
                    }
                }

                if (!any) {
                    continue;
                }

                Request downloadRequest = new Request.Builder()
                        .url(artifact.archive_download_url)
                        .get().build();

                try (Response downloadResponse = downloader.httpClient().newCall(downloadRequest).execute()) {
                    ResponseBody responseBody = downloadResponse.body();
                    if (!downloadResponse.isSuccessful() || responseBody == null) {
                        Downloader.LOGGER.error(
                                "Failed to download artifact " + artifact.name + " for " + describe() + " " + hash + ": "
                                        + (responseBody != null ? responseBody.string() : "(No body)"));
                        break;
                    }

                    zips.add(responseBody.bytes());
                } catch (IOException e) {
                    Downloader.LOGGER.error("Failed to download artifact " + artifact.name + " for " + describe() + " " + hash);
                }
            }

            List<VersionArtifactConfig> availableConfigs = new ArrayList<>(config.artifacts);
            for (byte[] zip : zips) {
                try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(zip))) {
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

                            byte[] bytes;
                            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                                IOUtils.copy(inputStream, outputStream);
                                bytes = outputStream.toByteArray();
                            }

                            Path file = versionStore.resolve(fileName);
                            Path metaFile = versionStore.resolve(fileName + METADATA_EXTENSION);

                            if (!Files.exists(versionStore)) {
                                Files.createDirectories(versionStore);
                            }

                            Files.write(file, bytes);
                            Downloader.OBJECT_MAPPER.writeValue(
                                    Files.newOutputStream(metaFile),
                                    new WorkflowFileMetadata(identifier)
                            );

                            artifactsByIdentifier.put(
                                    identifier,
                                    new Artifact(
                                            fileName,
                                            file,
                                            metaFile,
                                            inMemory ? bytes : null
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
                Downloader.LOGGER.error(
                        "Failed to find any files matching for workflow run " + hash + " for " + describe());
                return;
            }

            putVersion(new Version(hash, artifactsByIdentifier));
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to load workflow run " + hash + " for " + describe(), e);
        }
    }

    @Override
    public int versionsBehind(String comparedTo) throws IllegalArgumentException {
        for (int i = 0; i < workflowRuns.size(); i++) {
            if (workflowRuns.get(i).head_sha.equals(comparedTo)) {
                return i;
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void receiveWebhook(String event, JsonNode node) {
        if (!event.equals("workflow_run")
                || !node.get("action").asText().equals("completed")
                || workflow == null || node.get("workflow_run").get("workflow_id").asLong() != workflow.id) {
            return;
        }

        WorkflowRun workflowRun;
        try {
            workflowRun = Downloader.OBJECT_MAPPER.readValue(node.get("workflow_run").toString(), WorkflowRun.class);
        } catch (JsonProcessingException e) {
            Downloader.LOGGER.error("Failed to parse workflow run json", e);
            return;
        }
        if (!workflowRun.status.equals("success")
                || !workflowRun.head_branch.equals(config.branch)
                || !workflowRun.event.equals("push")) {
            return;
        }

        includeRun(workflowRun, config.versionsToKeepInMemory >= 1);
        workflowRuns.add(workflowRun);

        int versionsToKeep = config.versionsToKeep;
        if (versions.size() > versionsToKeep) {
            WorkflowRun remove = workflowRuns.get(versionsToKeep - 1);
            if (remove == null) {
                return;
            }

            Version version = versions.remove(remove.head_sha);
            if (version == null) {
                return;
            }

            version.expireIn(System.currentTimeMillis() + EXPIRE_AFTER);
        }
    }
}
