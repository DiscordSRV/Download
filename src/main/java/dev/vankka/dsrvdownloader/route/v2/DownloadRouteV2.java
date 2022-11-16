package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.Artifact;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.Map;

@RestController
public class DownloadRouteV2 {

    private final Downloader downloader;

    public DownloadRouteV2(Downloader downloader) {
        this.downloader = downloader;
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
            HttpServletRequest request
    ) throws Exception {
        VersionChannel channel = downloader.getChannel(repoOwner, repoName, releaseChannel)
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

        byte[] content = artifact.getContent();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + artifact.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(artifact.getSize())
                .body(
                        new InputStreamResource(
                                content != null
                                    ? new ByteArrayInputStream(content)
                                    : Files.newInputStream(artifact.getFile())
                        )
                );
    }
}
