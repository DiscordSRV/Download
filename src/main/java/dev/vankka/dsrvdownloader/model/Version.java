package dev.vankka.dsrvdownloader.model;

import java.util.HashMap;
import java.util.Map;

public class Version {

    private final String identifier;
    private final Map<String, Artifact> artifactsByIdentifier;
    private final Map<String, Artifact> artifactsByFileName;
    private Long expiry;

    public Version(String identifier, Map<String, Artifact> artifactsByIdentifier) {
        this.identifier = identifier;
        this.artifactsByIdentifier = artifactsByIdentifier;
        Map<String, Artifact> artifactsByFileName = new HashMap<>();
        for (Artifact artifact : artifactsByIdentifier.values()) {
            artifactsByFileName.put(artifact.getFileName(), artifact);
        }
        this.artifactsByFileName = artifactsByFileName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Map<String, Artifact> getArtifactsByIdentifier() {
        return artifactsByIdentifier;
    }

    public Map<String, Artifact> getArtifactsByFileName() {
        return artifactsByFileName;
    }

    public Long getExpiry() {
        return expiry;
    }

    public void expireIn(Long expiry) {
        this.expiry = expiry;
        artifactsByIdentifier.values().forEach(Artifact::removeFromMemory); // Don't keep expiring versions in memory
    }
}
