package dev.vankka.dsrvdownloader.model;

import java.util.List;

public record VersionResponse(
        String latest_base_url,
        List<Version> versions
) {
    public record Version(
            String identifier,
            String description,
            List<Artifact> artifacts
    ) {}

    public record Artifact(
            String file_name,
            long size,
            String download_url,
            Hashes hashes
    ) {}

    public record Hashes(
            String sha256
    ) {}
}
