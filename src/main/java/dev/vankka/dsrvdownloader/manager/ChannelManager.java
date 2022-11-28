package dev.vankka.dsrvdownloader.manager;

import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.discord.DiscordWebhook;
import dev.vankka.dsrvdownloader.model.channel.ReleaseChannel;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import dev.vankka.dsrvdownloader.model.channel.WorkflowChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChannelManager {

    private final List<VersionChannel> versionChannels = new ArrayList<>();
    private final AtomicBoolean refreshVersionChannels = new AtomicBoolean(false);
    private final ConfigManager configManager;
    private final DiscordWebhook discordWebhook;

    @Autowired
    public ChannelManager(ConfigManager configManager, DiscordWebhook discordWebhook) {
        this.configManager = configManager;
        this.discordWebhook = discordWebhook;
        reloadVersionChannels();
    }

    public void reloadVersionChannels() {
        List<VersionChannel> newChannels = new ArrayList<>();
        for (VersionChannelConfig channelConfig : configManager.config().versionChannels) {
            VersionChannel channel;
            switch (channelConfig.type) {
                case RELEASE:
                    channel = new ReleaseChannel(configManager, discordWebhook, channelConfig);
                    break;
                default:
                case WORKFLOW:
                    channel = new WorkflowChannel(configManager, discordWebhook, channelConfig);
                    break;
            }
            newChannels.add(channel);
        }

        refreshVersionChannels.set(true);
        versionChannels.clear();
        versionChannels.addAll(newChannels);

        refreshVersionChannels.set(false);
        synchronized (refreshVersionChannels) {
            refreshVersionChannels.notifyAll();
        }
    }

    public Optional<VersionChannel> getChannel(String repoOwner, String repoName, String name) {
        if (refreshVersionChannels.get()) {
            synchronized (refreshVersionChannels) {
                try {
                    refreshVersionChannels.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        for (VersionChannel versionChannel : versionChannels) {
            VersionChannelConfig config = versionChannel.getConfig();
            if (config.repoOwner.equalsIgnoreCase(repoOwner)
                    && config.repoName.equalsIgnoreCase(repoName)
                    && config.name.equalsIgnoreCase(name)) {
                return Optional.of(versionChannel);
            }
        }
        return Optional.empty();
    }

    public List<VersionChannel> versionChannels() {
        return versionChannels;
    }
}
