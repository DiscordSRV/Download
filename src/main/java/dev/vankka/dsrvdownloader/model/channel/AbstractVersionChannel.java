package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.route.v2.DownloadRouteV2;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractVersionChannel implements VersionChannel {

    protected final Downloader downloader;
    protected final VersionChannelConfig config;
    protected final Map<String, Version> versions;
    protected String versionResponse;

    public AbstractVersionChannel(Downloader downloader, VersionChannelConfig config) {
        this.downloader = downloader;
        this.config = config;
        this.versions = new LinkedHashMap<>();
    }

    protected String describe() {
        return config.name;
    }

    protected String repo() {
        return config.repoOwner + "/" + config.repoName;
    }

    protected String baseRepoUrl() {
        return Downloader.GITHUB_URL + "/repos/" + repo();
    }

    protected Path store() throws IOException {
        Path path = Paths.get("storage", config.name);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    protected void updateVersionResponse() {
        String url = downloader.config().apiUrl + "/v2/" + repo() + "/" + config.name + "/download/";

        ObjectNode response = downloader.objectMapper().createObjectNode();
        response.put("latest_url", url + LATEST_IDENTIFIER);

        ArrayNode versions = response.putArray("versions");
        for (Map.Entry<String, Version> entry : this.versions.entrySet()) {
            String identifier = entry.getKey();
            Version version = entry.getValue();
            if (version.getExpiry() != null) {
                continue;
            }

            ObjectNode objectNode = versions.addObject();
            objectNode.put("identifier", identifier);
            objectNode.put("file_name", version.getName());
            objectNode.put("size", version.getSize());
            objectNode.put("download_url", url + identifier);
        }

        versionResponse = response.toString();
    }

    @Override
    public VersionChannelConfig config() {
        return config;
    }

    @Override
    public Map<String, Version> versions() {
        return Collections.unmodifiableMap(versions);
    }

    @Override
    public String versionResponse() {
        return versionResponse;
    }
}
