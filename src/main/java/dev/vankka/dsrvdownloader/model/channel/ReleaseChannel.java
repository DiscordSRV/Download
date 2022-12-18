package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionArtifactConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.discord.DiscordWebhook;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.exception.InclusionException;
import dev.vankka.dsrvdownloader.model.github.Release;
import dev.vankka.dsrvdownloader.util.HttpContentUtil;
import dev.vankka.dsrvdownloader.util.IO;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.tomcat.util.buf.HexUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ReleaseChannel extends AbstractVersionChannel {

    private static final int RELEASES_PER_PAGE = 100;

    private List<Release> releases;

    public ReleaseChannel(ConfigManager configManager, DiscordWebhook discordWebhook, VersionChannelConfig config) {
        super(configManager, discordWebhook, config);
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

            try (Response response = configManager.httpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    Downloader.LOGGER.error(
                            "Failed to get releases for " + describe() + " (" + request.url() + "): "
                                    + HttpContentUtil.prettify(response, body));
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

    private void includeRelease(Release release, boolean inMemory, boolean newVersion)
            throws IOException, RuntimeException, InclusionException, DigestException, NoSuchAlgorithmException {
        Path store = store().resolve(release.tag_name);

        Map<String, Release.Asset> assets = new HashMap<>();

        for (Release.Asset releaseAsset : release.assets) {
            for (VersionArtifactConfig artifact : config.artifacts) {
                if (Pattern.compile(artifact.fileNameFormat).matcher(releaseAsset.name).matches()) {
                    assets.put(artifact.identifier, releaseAsset);
                }
            }
        }
        if (assets.isEmpty()) {
            throw new InclusionException("Failed to find any files matching for release");
        }

        if (!Files.exists(store)) {
            Files.createDirectory(store);
        }

        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        for (Map.Entry<String, Release.Asset> entry : assets.entrySet()) {
            String artifactId = entry.getKey();
            Release.Asset asset = entry.getValue();

            String fileName = asset.name;
            Path file = store.resolve(fileName);

            byte[] bytes = null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (!Files.exists(file)) {
                Request request = new Request.Builder()
                        .url(asset.browser_download_url)
                        .get().build();

                try (Response response = configManager.httpClient().newCall(request).execute()) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new InclusionException(
                                "Failed to download " + fileName,
                                request.url() + " => " + HttpContentUtil.prettify(response, body));
                    }

                    Files.createFile(file);

                    try (IO io = new IO(body.byteStream())
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
                }
            } else if (inMemory) {
                try (IO io = new IO(Files.newInputStream(file))
                        .withDigest(digest)
                        .withOutputStream(Files.newOutputStream(file))
                ) {
                    io.stream();
                }
            }

            artifacts.put(
                    artifactId,
                    new Artifact(
                            artifactId,
                            fileName,
                            file,
                            null,
                            bytes,
                            HexUtils.toHexString(digest.digest())
                    )
            );
        }

        putVersion(new Version(release.tag_name, release.name, artifacts), newVersion);
    }

    private void loadFiles() {
        int max = Math.min(config.versionsToKeep, releases.size());
        for (int i = 0; i < max; i++) {
            Release release = releases.get(i);
            try {
                includeRelease(release, config.versionsToKeepInMemory > i, false);
            } catch (IOException | InclusionException | DigestException | NoSuchAlgorithmException e) {
                setLastDiscordMessage(release.tag_name, "[Boot] Failed to load release [`" + describe() + "`]", ExceptionUtils.getStackTrace(e));
            }
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

    private static final Set<String> ACCEPTABLE_ACTIONS = new HashSet<>(Arrays.asList("created", "released"));
    @Override
    public void receiveWebhook(String event, JsonNode node) {
        if (!event.equals("release")) {
            return;
        }

        Release release;
        try {
            release = Downloader.OBJECT_MAPPER.readValue(node.get("release").toString(), Release.class);
        } catch (JsonProcessingException e) {
            Downloader.LOGGER.error("Failed to parse release json", e);
            return;
        }

        String action = node.get("action").asText();
        if (!ACCEPTABLE_ACTIONS.contains(action)) {
            return;
        }

        if (!action.equals("released")) {
            waiting(release.tag_name, release.name, "for [release](" + release.html_url + ") to publish");
            return;
        }

        processing(release.tag_name, release.name);
        releases.add(0, release);

        try {
            try {
                includeRelease(release, config.versionsToKeepInMemory >= 1, true);
            } catch (IOException | DigestException | NoSuchAlgorithmException e) {
                throw new InclusionException(e);
            }

            success(release.tag_name, release.name);
        } catch (InclusionException e) {
            failed(release.tag_name, release.name, e.getMessage(), e.getLonger());
        }

        expireOldestVersion();
    }
}
