package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.model.ErrorModel;
import dev.vankka.dsrvdownloader.model.VersionResponse;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.swagger.annotations.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;

@RestController
@Api(tags = "v2")
public class VersionsRouteV2 {

    private final ChannelManager channelManager;

    public VersionsRouteV2(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @GetMapping(
            path = "/v2/{repoOwner}/{repoName}/{releaseChannel}/versions",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @ApiOperation(value = "Versions", notes = "Versions")
    @ApiResponses({
            @ApiResponse(code = 200 /* OK */, message = "Success", response = VersionResponse.class),
            @ApiResponse(code = 400 /* Bad Request */, message = "Bad Request", response = ErrorModel.class)
    })
    public VersionResponse handle(
            @PathVariable @ApiParam(example = "DiscordSRV") String repoOwner,
            @PathVariable @ApiParam(example = "DiscordSRV") String repoName,
            @PathVariable @ApiParam(example = "release") String releaseChannel,
            @RequestParam(name = "preferIdentifier", defaultValue = "false") boolean preferIdentifier,
            HttpServletRequest request
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        return channel.versionResponse(request, preferIdentifier);
    }
}
