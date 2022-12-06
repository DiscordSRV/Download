package dev.vankka.dsrvdownloader.route.v2;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;

@RestController
public class VersionsRouteV2 {

    private final ChannelManager channelManager;

    public VersionsRouteV2(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @GetMapping(
            path = "/v2/{repoOwner}/{repoName}/{releaseChannel}/versions",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ObjectNode handle(
            @PathVariable String repoOwner,
            @PathVariable String repoName,
            @PathVariable String releaseChannel,
            @RequestParam(name = "preferIdentifier", defaultValue = "false") boolean preferIdentifier,
            HttpServletRequest request
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        return channel.versionResponse(request, preferIdentifier);
    }
}
