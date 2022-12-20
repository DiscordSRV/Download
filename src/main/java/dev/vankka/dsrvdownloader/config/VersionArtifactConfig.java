package dev.vankka.dsrvdownloader.config;

public record VersionArtifactConfig(
        String identifier,
        String archiveNameFormat,
        String fileNameFormat
) {}
