package dev.vankka.dsrvdownloader.route.v2;

import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.manager.StatsManager;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import dev.vankka.dsrvdownloader.util.RequestSourceUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@RestController
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
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public Object handle(
            @PathVariable String repoOwner,
            @PathVariable String repoName,
            @PathVariable String releaseChannel,
            @PathVariable String identifier,
            @PathVariable String artifactIdentifier,
            @RequestParam(name = "preferRedirect", defaultValue = "true") boolean preferRedirect,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        VersionChannel channel = channelManager.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown repository or channel"));

        Map<String, Version> versions = channel.versionsByIdentifier();

        boolean isRedirect = false;

        Version version;
        if (identifier.equalsIgnoreCase(VersionChannel.LATEST_IDENTIFIER)) {
            if (versions.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No versions available for this channel");
            }

            version = versions.values().iterator().next();
            isRedirect = true;
        } else {
            version = versions.get(identifier);
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

            statsManager.increment(channel, artifact.getIdentifier(), version.getIdentifier());
        } catch (IOException ignored) {}
        return null;
    }
}
