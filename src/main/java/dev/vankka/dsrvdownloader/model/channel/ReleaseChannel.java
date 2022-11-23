package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionArtifactConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.github.Release;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ReleaseChannel extends AbstractVersionChannel {

    private static final int RELEASES_PER_PAGE = 100;

    private List<Release> releases;

    public ReleaseChannel(Downloader downloader, VersionChannelConfig config) {
        super(downloader, config);
        updateReleases();
        if (releases == null || releases.isEmpty()) {
            return;
        }
        loadFiles();
        cleanupDirectory();
    }

    private void updateReleases() {
        releases = new ArrayList<>();

        int page = 1;
        while (true) {
            Request request = new Request.Builder()
                    .url(baseRepoApiUrl() + "/releases?page=" + page + "&per_page=" + RELEASES_PER_PAGE)
                    .get().build();

            try (Response response = downloader.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get releases for " + describe() + " (" + request.url() + "): "
                                    + (body != null ? body.string() : "(No body)"));
                    return;
                }

                List<Release> currentReleases = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), new TypeReference<>(){});
                releases.addAll(currentReleases);

                if (currentReleases.size() < RELEASES_PER_PAGE) {
                    break;
                }
            } catch (IOException e) {
                Downloader.LOGGER.error("Failed to get releases for repository " + repo(), e);
                return;
            }

            page++;
        }
    }

    private void includeRelease(Release release, boolean inMemory) {
        try {
            Path store = store();

            Map<String, Release.Asset> assets = new HashMap<>();

            for (Release.Asset releaseAsset : release.assets) {
                for (VersionArtifactConfig artifact : config.artifacts) {
                    if (Pattern.compile(artifact.fileNameFormat).matcher(releaseAsset.name).matches()) {
                        assets.put(artifact.identifier, releaseAsset);
                    }
                }
            }
            if (assets.isEmpty()) {
                Downloader.LOGGER.error(
                        "Failed to find any files matching for release " + release.tag_name + " for " + describe());
                return;
            }

            Map<String, Artifact> artifacts = new LinkedHashMap<>();
            for (Map.Entry<String, Release.Asset> entry : assets.entrySet()) {
                String artifactId = entry.getKey();
                Release.Asset asset = entry.getValue();

                String fileName = asset.name;
                Path file = store.resolve(fileName);

                byte[] bytes = null;
                if (!Files.exists(file)) {
                    Request request = new Request.Builder()
                            .url(asset.browser_download_url)
                            .get().build();

                    try (Response response = downloader.httpClient().newCall(request).execute()) {
                        ResponseBody body = response.body();
                        if (!response.isSuccessful() || body == null) {
                            Downloader.LOGGER.error(
                                    "Failed to download " + fileName + " for " + describe() + " (" + request.url() + "): "
                                            + response.code() + ": " + (body != null ? body.string() : "(No body)"));
                            return;
                        }

                        Files.createFile(file);

                        if (inMemory) {
                            bytes = body.bytes();
                            Files.write(file, bytes);
                        } else {
                            IOUtils.copy(body.byteStream(), Files.newOutputStream(file));
                        }
                    }
                } else if (inMemory) {
                    bytes = Files.readAllBytes(file);
                }

                artifacts.put(artifactId, new Artifact(fileName, file, null, bytes));
            }

            putVersion(new Version(release.tag_name, artifacts));
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to load release " + release.tag_name + " for " + describe(), e);
        }
    }

    private void loadFiles() {
        int max = Math.min(config.versionsToKeep, releases.size());
        for (int i = 0; i < max; i++) {
            includeRelease(releases.get(i), config.versionsToKeepInMemory > i);
        }
    }

    @Override
    public int versionsBehind(String comparedTo, Consumer<String> versionConsumer) {
        for (int i = 0; i < releases.size(); i++) {
            String tagName = releases.get(i).tag_name;
            versionConsumer.accept(tagName);
            if (tagName.equals(comparedTo)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected String amountType() {
        return "versions";
    }

    @Override
    public void receiveWebhook(String event, JsonNode node) {
        if (!event.equals("release") || !node.get("action").asText().equals("published")) {
            return;
        }

        Release release;
        try {
            release = Downloader.OBJECT_MAPPER.readValue(node.get("release").toString(), Release.class);
        } catch (JsonProcessingException e) {
            Downloader.LOGGER.error("Failed to parse release json", e);
            return;
        }

        includeRelease(release, config.versionsToKeepInMemory >= 1);
        releases.add(0, release);

        int versionsToKeep = config.versionsToKeep;
        if (releases.size() > versionsToKeep) {
            Release remove = releases.remove(versionsToKeep - 1);
            if (remove == null) {
                return;
            }

            Version version = versions.remove(remove.tag_name);
            if (version == null) {
                return;
            }

            version.expireIn(System.currentTimeMillis() + EXPIRE_AFTER);
        }
    }
}
