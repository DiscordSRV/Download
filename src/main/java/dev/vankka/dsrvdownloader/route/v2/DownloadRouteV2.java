package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.Map;

@RestController
public class DownloadRouteV2 {

    private final Downloader downloader;

    public DownloadRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @GetMapping("/v2/{repoOwner}/{repoName}/{releaseChannel}/download/{identifier}")
    public ResponseEntity<?> handle(
            @PathVariable String repoOwner,
            @PathVariable String repoName,
            @PathVariable String releaseChannel,
            @PathVariable String identifier
    ) throws Exception {
        VersionChannel channel = downloader.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown repository or channel"));

        Map<String, Version> versions = channel.versions();

        Version version;
        if (identifier.equalsIgnoreCase(VersionChannel.LATEST_IDENTIFIER)) {
            if (versions.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No versions available for this channel");
            }

            version = versions.values().iterator().next();
        } else {
            version = versions.get(identifier);
        }
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found");
        }

        byte[] content = version.getContent();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + version.getName() + "\"")
                .contentType(MediaType.valueOf("application/java-archive"))
                .contentLength(version.getSize())
                .body(
                        new InputStreamResource(
                                content != null
                                    ? new ByteArrayInputStream(content)
                                    : Files.newInputStream(version.getFile())
                        )
                );
    }
}
