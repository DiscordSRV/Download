package dev.vankka.dsrvdownloader.config;

import java.util.List;

public class Config {

    public List<VersionChannelConfig> versionChannels;
    public List<RepoConfig> repos;
    public String githubToken;
    public String discordWebhookUrl;

}
