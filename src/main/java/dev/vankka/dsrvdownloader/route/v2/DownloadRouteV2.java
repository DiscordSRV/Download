package dev.vankka.dsrvdownloader.route.v2;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.manager.StatsManager;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.ErrorModel;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import dev.vankka.dsrvdownloader.util.RequestSourceUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import io.swagger.annotations.*;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.function.Supplier;

@RestController
@Api(tags = "v2")
public class DownloadRouteV2 {

    private final ChannelManager channelManager;
    private final StatsManager statsManager;

    // 10 downloads per 1 minute
    @SuppressWarnings("unchecked")
    private final CaffeineProxyManager<String> downloadRateLimit = new CaffeineProxyManager<>(
            (Caffeine<String, RemoteBucketState>) (Object) Caffeine.newBuilder(),
            Duration.ofMinutes(1)
    );
    private final Supplier<BucketConfiguration> rateLimitSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(10, Duration.ofMinutes(1))).build();

    public DownloadRouteV2(ChannelManager channelManager, StatsManager statsManager) {
        this.channelManager = channelManager;
        this.statsManager = statsManager;
    }

    @RequestMapping(
            path = "/v2/{repoOwner}/{repoName}/{releaseChannel}/download/{identifier}/{artifactIdentifier}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            method = {RequestMethod.GET, RequestMethod.POST}
    )
    @ApiOperation(value = "Download", notes = "Download a version")
    @ApiResponses({
            @ApiResponse(code = 200 /* OK */, message = "Success"),
            @ApiResponse(code = 302 /* Found */, message = "Redirect"),
            @ApiResponse(code = 307 /* Temporary Redirect */, message = "Redirect"),
            @ApiResponse(code = 400 /* Bad Request */, message = "Bad Request", response = ErrorModel.class)
    })
    public Object handle(
            @PathVariable @ApiParam(example = "DiscordSRV") String repoOwner,
            @PathVariable @ApiParam(example = "DiscordSRV") String repoName,
            @PathVariable @ApiParam(example = "release") String releaseChannel,
            @PathVariable @ApiParam(example = "latest") String identifier,
            @PathVariable @ApiParam(example = "jar") String artifactIdentifier,
            @RequestParam(name = "preferRedirect", defaultValue = "true") boolean preferRedirect,
            @RequestHeader(name = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        boolean isRedirect = false;

        Version version;
        if (identifier.equalsIgnoreCase(VersionChannel.LATEST_IDENTIFIER)) {
            version = channel.latestVersion();
            if (version == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No versions available for this channel");
            }

            isRedirect = true;
        } else {
            version = channel.versionsByIdentifier().get(identifier);
        }
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version not found");
        }

        Artifact artifact = version.getArtifactsByFileName().get(artifactIdentifier);
        if (artifact == null) {
            isRedirect = true;
            artifact = version.getArtifactsByIdentifier().get(artifactIdentifier);
        }
        if (artifact == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact not found");
        }

        if (isRedirect && preferRedirect) {
            String url = channel.getUrl(request) + "/download/" + version.getIdentifier() + "/" + artifact.getFileName();
            return new RedirectView(url);
        }

        String requester = RequestSourceUtil.getRequestSource(request);
        BucketProxy bucket = downloadRateLimit.builder().build(requester, rateLimitSupplier);

        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }

        byte[] content = artifact.getContent();
        response.addHeader("Content-Disposition", "attachment; filename=\"" + artifact.getFileName() + "\"");
        response.addHeader("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setContentLengthLong(artifact.getSize());
        response.setStatus(200);

        try (OutputStream outputStream = new BufferedOutputStream(response.getOutputStream())) {
            try (InputStream inputStream = new BufferedInputStream(
                    content != null ? new ByteArrayInputStream(content) : Files.newInputStream(artifact.getFile())
            )) {
                byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                int amount;
                while ((amount = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, amount);
                }
            }

            statsManager.increment(channel, userAgent, artifact.getIdentifier(), version.getIdentifier());
        } catch (IOException ignored) {}
        return null;
    }
}
