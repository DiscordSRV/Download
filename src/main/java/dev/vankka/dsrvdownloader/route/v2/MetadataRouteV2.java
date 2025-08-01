package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.config.VersionArtifactConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.model.ChannelResponse;
import dev.vankka.dsrvdownloader.model.ErrorModel;
import dev.vankka.dsrvdownloader.model.MetadataResponse;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.swagger.annotations.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@Api(tags = "v2")
public class MetadataRouteV2 {

    private final ChannelManager channelManager;

    public MetadataRouteV2(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @GetMapping(
            path = "/v2/{repoOwner}/{repoName}/{releaseChannel}/metadata",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @CrossOrigin
    @ApiOperation(value = "Metadata", notes = "Metadata")
    @ApiResponses({
            @ApiResponse(code = 200 /* OK */, message = "Success", response = MetadataResponse.class),
            @ApiResponse(code = 400 /* Bad Request */, message = "Bad Request", response = ErrorModel.class)
    })
    public MetadataResponse handle(
            @PathVariable @ApiParam(example = "DiscordSRV") String repoOwner,
            @PathVariable @ApiParam(example = "DiscordSRV") String repoName,
            @PathVariable @ApiParam(example = "release") String releaseChannel
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        Map<String, Map<String, Object>> artifactMetadata = new HashMap<>();
        for (VersionArtifactConfig artifact : channel.getConfig().artifacts()) {
            Map<String, Object> metadata = artifact.metadata();
            if (metadata != null) {
                artifactMetadata.put(artifact.identifier(), metadata);
            }
        }

        Map<String, Object> channelMetadata = channel.getConfig().metadata();
        if (channelMetadata == null) {
            channelMetadata = Collections.emptyMap();
        }

        return new MetadataResponse(channelMetadata, artifactMetadata);
    }
}
