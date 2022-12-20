package dev.vankka.dsrvdownloader.config;

import java.util.List;

public record Config(
        List<VersionChannelConfig> versionChannels,
        String githubWebhookPath,
        String githubWebhookSecret,
        String githubToken,
        String discordWebhookUrl
) {}
