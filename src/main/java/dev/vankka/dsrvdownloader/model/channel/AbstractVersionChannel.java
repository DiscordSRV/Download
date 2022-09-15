package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Version;
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

        Path path = Paths.get("/home/vankka/mctest/paper1.18/plugins/DiscordSRV-Bukkit-2.0.0-SNAPSHOT.jar");
        try {
            versions.put("test", new Version(path.getFileName().toString(), Files.size(path), path, Files.readAllBytes(path)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        updateVersionResponse();
//        try {
//            updateCommits();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

    private void updateVersionResponse() {
        ArrayNode array = downloader.objectMapper().createArrayNode();
        for (Map.Entry<String, Version> entry : versions.entrySet()) {
            Version version = entry.getValue();
            if (version.getExpiry() != null) {
                continue;
            }

            ObjectNode objectNode = array.addObject();
            objectNode.put("identifier", entry.getKey());
            objectNode.put("name", version.getName());
            objectNode.put("size", version.getSize());
        }
        versionResponse = array.toString();
    }

    private void updateCommits() throws IOException {
        Request request = new Request.Builder()
                .url(Downloader.GITHUB_URL + "/repos/" + config.repoOwner + "/" + config.repoName + "/commits")
                .get().build();

        try (Response response = downloader.httpClient().newCall(request).execute()) {
            System.out.println(response.body().string());
        }
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
