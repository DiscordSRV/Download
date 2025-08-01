package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.model.ChannelResponse;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@Api(tags = "v2")
public class ChannelsRouteV2 {

    private final ChannelManager channelManager;

    public ChannelsRouteV2(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @GetMapping(
            path = "/v2/channels",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @ApiOperation(value = "Channels", notes = "Channels")
    @ApiResponses({
            @ApiResponse(code = 200 /* OK */, message = "Success")
    })
    public List<ChannelResponse> handle() {
        List<ChannelResponse> channels = new ArrayList<>();
        for (VersionChannel channel : channelManager.versionChannels()) {
            VersionChannelConfig config = channel.getConfig();
            channels.add(new ChannelResponse(config.repoOwner(), config.repoName(), config.name()));
        }
        return channels;
    }
}
