package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.WorkflowFileMetadata;
import dev.vankka.dsrvdownloader.model.github.Workflow;
import dev.vankka.dsrvdownloader.model.github.WorkflowPaging;
import dev.vankka.dsrvdownloader.model.github.WorkflowRun;
import dev.vankka.dsrvdownloader.model.github.WorkflowRunPaging;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                    .url(baseRepoApiUrl() + "/workflows?per_page=" + WORKFLOWS_PER_PAGE + "&page=" + (++page))
                    .get().build();

            try (Response response = downloader.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflows for " + describe() + ": "
                                    + (body != null ? body.string() : "(No body)"));
                    return;
                }

                WorkflowPaging workflowPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowPaging.class);
                if (workflowPaging.total_count < WORKFLOWS_PER_PAGE) {
                    break;
                }
                for (Workflow wf : workflowPaging.workflows) {
                    if (wf.path.endsWith(config.workflowFile)) {
                        workflow = wf;
                        break;
                    }
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
                                 + "/actions/workflows/" + Long.toUnsignedString(workflow.id)
                                 + "?status=success&event=push&branch=" + config.branch
                                 + "&per_page=" + WORKFLOWS_RUNS_PER_PAGE + "&page=" + (i + 1))
                    .get().build();

            try (Response response = downloader.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow runs for " + describe() + ": "
                                    + (body != null ? body.string() : "(No body)"));
                    return;
                }

                WorkflowRunPaging workflowRunPaging = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), WorkflowRunPaging.class);
                if (workflowRunPaging == null) {
                    Downloader.LOGGER.error(
                            "Failed to get workflow runs for " + describe() + ": Failed to parse json: " + body.string());
                    return;
                }

                workflowRuns.addAll(workflowRunPaging.workflows_runs);
                if (workflowRunPaging.workflows_runs.size() < WORKFLOWS_RUNS_PER_PAGE) {
                    break;
                }
            } catch (IOException e) {
                Downloader.LOGGER.error("Failed to get releases for  " + describe(), e);
            }
        }
    }

    private void loadFilesAndCleanupStore() {
        Map<String, Path> nonMetaPaths = new LinkedHashMap<>();
        Map<String, Path> metaPaths = new HashMap<>();
        try {
            Path store = store();

            try (Stream<Path> files = Files.list(store)) {
                files.forEach(path -> {
                    String name = path.getFileName().toString();
                    if (name.endsWith(METADATA_EXTENSION)) {
                        metaPaths.put(name.substring(0, name.length() - METADATA_EXTENSION.length()), path);
                    } else {
                        nonMetaPaths.put(name, path);
                    }
                });
            }

            for (Map.Entry<String, Path> entry : metaPaths.entrySet()) {
                if (!nonMetaPaths.containsKey(entry.getKey())) {
                    // Orphan metadata file
                    Files.delete(entry.getValue());
                }
            }

            Map<String, Triple<String, Path, Path>> versions = new LinkedHashMap<>();
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
                versions.put(metadata.hash, Triple.of(fileName, file, metaFile));
            }

            for (int i = 0; i < Math.min(workflowRuns.size(), config.versionsToKeep); i++) {
                WorkflowRun run = workflowRuns.get(i);
                Triple<String, Path, Path> diskVersion = versions.remove(run.head_sha);
                if (diskVersion != null) {
                    String fileName = diskVersion.getLeft();
                    Path folder = diskVersion.getMiddle();
                    Path metaFile = diskVersion.getRight();

                    // TODO

                    Map<String, Artifact> artifacts = new HashMap<>();
                    try (Stream<Path> files = Files.list(folder)) {
                        for (Path file : files.collect(Collectors.toList())) {
                            byte[] bytes = null;
                            if (i < config.versionsToKeepInMemory) {
                                bytes = Files.readAllBytes(file);
                            }
                        }
                    }

                    // TODO
                    //putVersion(run.head_sha, fileName, file, bytes);
                    continue;
                }

                Request request = new Request.Builder()
                        .url(baseRepoApiUrl() + "/actions/runs/" + Long.toUnsignedString(run.id) + "/artifacts")
                        .get().build();

                try (Response response = downloader.httpClient().newCall(request).execute()) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        Downloader.LOGGER.error(
                                "Failed to get workflow artifacts for " + describe() + " " + run.head_sha + ": "
                                        + (body != null ? body.string() : "(No body)"));
                        return;
                    }

                    // TODO: dl
                }
            }

            for (Triple<String, Path, Path> value : versions.values()) {
                Files.delete(value.getMiddle());
                Files.delete(value.getRight());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    }
}
