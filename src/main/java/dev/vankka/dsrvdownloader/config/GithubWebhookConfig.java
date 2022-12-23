package dev.vankka.dsrvdownloader.config;

public record GithubWebhookConfig(
        String path,
        String secret
) {}
