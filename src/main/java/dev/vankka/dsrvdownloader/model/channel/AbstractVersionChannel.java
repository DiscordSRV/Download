package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractVersionChannel implements VersionChannel {

    protected final Downloader downloader;
    protected final VersionChannelConfig config;
    protected final Map<String, Version> versions;

    public AbstractVersionChannel(Downloader downloader, VersionChannelConfig config) {
        this.downloader = downloader;
        this.config = config;
        this.versions = new LinkedHashMap<>();
    }

    protected void cleanupDirectory() {
        try {
            Path store = store();

            List<Path> toDelete;
            try (Stream<Path> pathStream = Files.list(store)) {
                toDelete = pathStream
                        .filter(path -> versions.values().stream()
                                .noneMatch(ver -> ver.getArtifactsByIdentifier().values()
                                            .stream()
                                            .anyMatch(art -> path.equals(art.getFile()) || path.equals(art.getMetaFile()))
                                )
                        )
                        .collect(Collectors.toList());
            }

            for (Path path : toDelete) {
                Files.delete(path);
            }
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to cleanup directory of " + describe(), e);
        }
    }

    protected String describe() {
        return repo() + ":" + config.name;
    }

    protected String repo() {
        return config.repoOwner + "/" + config.repoName;
    }

    protected String baseRepoApiUrl() {
        return Downloader.GITHUB_URL + "/repos/" + repo();
    }

    protected Path store() throws IOException {
        Path path = Paths.get("storage", config.repoOwner, config.repoName, config.name);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    protected void putVersion(Version version) {
        versions.put(version.getIdentifier(), version);
    }

    @Override
    public VersionChannelConfig getConfig() {
        return config;
    }

    @Override
    public Map<String, Version> versionsByIdentifier() {
        return Collections.unmodifiableMap(versions);
    }

    @Override
    public String getUrl(HttpServletRequest request) {
        boolean isHttps = request.isSecure();
        int port = request.getServerPort();
        if (port == 0) {
            port = 80;
        }
        String hostName = request.getHeader("X-Forwarded-Host");
        String host = request.getScheme()
                + "://" + (hostName != null ? hostName : request.getServerName())
                + (port == (isHttps ? 443 : 80) ? "" : ":" + port);

        return host + "/v2/" + repo() + "/" + config.name;
    }

    @Override
    public String versionResponse(HttpServletRequest request, boolean preferIdentifier) {
        String baseUrl = getUrl(request) + "/download/";

        ObjectNode response = Downloader.OBJECT_MAPPER.createObjectNode();
        ArrayNode versions = response.putArray("versions");
        for (Version version : this.versions.values()) {
            if (version.getExpiry() != null) {
                continue;
            }

            ObjectNode objectNode = versions.addObject();
            objectNode.put("identifier", version.getIdentifier());

            ObjectNode artifacts = objectNode.with("artifacts");

            for (Map.Entry<String, Artifact> artifactEntry : version.getArtifactsByIdentifier().entrySet()) {
                String artifactIdentifier = artifactEntry.getKey();
                Artifact artifact = artifactEntry.getValue();

                ObjectNode artifactNode = artifacts.with(artifactEntry.getKey());
                artifactNode.put("file_name", artifact.getFileName());
                artifactNode.put("size", artifact.getSize());
                artifactNode.put("download_url", baseUrl + version.getIdentifier() + "/"
                        + (preferIdentifier ? artifactIdentifier : artifact.getFileName()));
            }

        }

        return response.toString();
    }
}
