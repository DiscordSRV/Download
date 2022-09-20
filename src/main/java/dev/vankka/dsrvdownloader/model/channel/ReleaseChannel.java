package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.github.Release;
import dev.vankka.dsrvdownloader.model.Version;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class ReleaseChannel extends AbstractVersionChannel {

    private List<Release> releases;

    public ReleaseChannel(Downloader downloader, VersionChannelConfig config) {
        super(downloader, config);
        updateReleases();
        loadFiles();
        cleanupDirectory();
    }

    private void updateReleases() {
        Request request = new Request.Builder()
                .url(baseRepoUrl() + "/releases")
                .get().build();

        try (Response response = downloader.httpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return;
            }

            ResponseBody body = response.body();
            if (body != null) {
                releases = Downloader.OBJECT_MAPPER.readValue(body.byteStream(), new TypeReference<>(){});
            }
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to get releases for repository " + repo(), e);
        }
    }

    private void includeRelease(Release release, boolean inMemory) {
        Path store;
        try {
            store = store();

            Pattern pattern = Pattern.compile(config.fileNameFormat);
            Release.Asset asset = null;
            for (Release.Asset releaseAsset : release.assets) {
                if (pattern.matcher(releaseAsset.name).matches()) {
                    asset = releaseAsset;
                    break;
                }
            }
            if (asset == null) {
                Downloader.LOGGER.error(
                        "Failed to find a file matching " + pattern.pattern()
                                + " for release " + release.tag_name + " for " + describe());
                return;
            }

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
                                "Failed to download " + fileName + " from " + describe() + ": "
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

            long size = bytes != null ? bytes.length : Files.size(file);
            versions.put(release.tag_name, new Version(fileName, size, file, null, bytes));
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to load release " + release.tag_name + " for " + describe(), e);
        }
    }

    private void loadFiles() {
        int max = Math.min(config.versionsToKeep, releases.size());
        for (int i = 0; i < max; i++) {
            includeRelease(releases.get(i), config.versionToKeepInMemory > i);
        }
        updateVersionResponse();
    }

    @Override
    public int versionsBehind(String comparedTo) throws IllegalArgumentException {
        for (int i = 0; i < releases.size(); i++) {
            if (releases.get(i).tag_name.equals(comparedTo)) {
                return i;
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void receiveWebhook(String event, JsonNode node) {

    }
}
