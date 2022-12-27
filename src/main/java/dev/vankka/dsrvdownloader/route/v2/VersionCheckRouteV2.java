package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.model.ErrorModel;
import dev.vankka.dsrvdownloader.model.VersionCheck;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.swagger.annotations.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Api(tags = "v2")
public class VersionCheckRouteV2 {

    private final ChannelManager channelManager;

    public VersionCheckRouteV2(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @GetMapping(
            path = "/v2/{repoOwner}/{repoName}/{releaseChannel}/version-check/{identifier}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @ApiOperation(value = "Version Check", notes = "Version Check")
    @ApiResponses({
            @ApiResponse(code = 200 /* OK */, message = "Success", response = VersionCheck.class),
            @ApiResponse(code = 400 /* Bad Request */, message = "Bad Request", response = ErrorModel.class)
    })
    public VersionCheck handle(
            @PathVariable @ApiParam(example = "DiscordSRV") String repoOwner,
            @PathVariable @ApiParam(example = "DiscordSRV") String repoName,
            @PathVariable @ApiParam(example = "release") String releaseChannel,
            @PathVariable @ApiParam(example = "v1.0.0") String identifier
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        return channel.checkVersion(identifier);
    }
}
