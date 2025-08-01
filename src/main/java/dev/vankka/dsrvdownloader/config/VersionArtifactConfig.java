package dev.vankka.dsrvdownloader.config;

import java.util.Map;

public record VersionArtifactConfig(
        String identifier,
        String archiveNameFormat,
        String fileNameFormat,

        Map<String, Object> metadata
) {}
