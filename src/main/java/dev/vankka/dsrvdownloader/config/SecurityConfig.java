package dev.vankka.dsrvdownloader.config;

public record SecurityConfig(
        String versionIdentifier,
        boolean vulnerability,
        String securityFailReason
) {}
