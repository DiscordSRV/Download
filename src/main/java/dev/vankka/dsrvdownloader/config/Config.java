package dev.vankka.dsrvdownloader.config;

import java.util.List;

public record Config(
        List<VersionChannelConfig> versionChannels,
        List<GithubWebhookConfig> githubWebhooks,
        String githubToken,
        String discordWebhookUrl,
        String rootRedirectUrl
) {}
