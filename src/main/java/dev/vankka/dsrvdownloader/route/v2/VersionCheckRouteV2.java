package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.model.VersionCheck;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VersionCheckRouteV2 {

    private final ChannelManager channelManager;

    public VersionCheckRouteV2(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @GetMapping(
            path = "/v2/{repoOwner}/{repoName}/{releaseChannel}/version-check/{identifier}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public VersionCheck handle(
            @PathVariable String repoOwner,
            @PathVariable String repoName,
            @PathVariable String releaseChannel,
            @PathVariable String identifier
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        return channel.checkVersion(identifier);
    }
}
