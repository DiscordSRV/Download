package dev.vankka.dsrvdownloader.config;

import java.util.List;

public record AuthConfig(
        List<String> discordUserIds,
        String clientId,
        String clientSecret
) {}
