package dev.vankka.dsrvdownloader.model;

import java.util.Map;

public record ChannelResponse(
        String repoOwner,
        String repoName,
        String channelName
) {}
