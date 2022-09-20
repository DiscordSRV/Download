package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class VersionsRouteV2 {

    private final Downloader downloader;

    public VersionsRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @GetMapping("/v2/{repoOwner}/{repoName}/{releaseChannel}/versions")
    public ResponseEntity<?> handle(
            @PathVariable String repoOwner,
            @PathVariable String repoName,
            @PathVariable String releaseChannel
    ) {
        VersionChannel channel = downloader.getChannel(repoOwner, repoName, releaseChannel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown repository or channel"));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(channel.versionResponse());
    }
}
