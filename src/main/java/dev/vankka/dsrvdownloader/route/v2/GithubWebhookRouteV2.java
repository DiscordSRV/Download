package dev.vankka.dsrvdownloader.route.v2;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.RepoConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@RestController
public class GithubWebhookRouteV2 {

    private final Downloader downloader;

    public GithubWebhookRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @PostMapping(
            path = "/v2/{repoOwner}/{repoName}/github-webhook/{route}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handle(
            @PathVariable String repoOwner,
            @PathVariable String repoName,
            @PathVariable String route,
            @RequestHeader(name = "X-Hub-Signature-256") String signature,
            @RequestHeader(name = "X-GitHub-Event") String event,
            @RequestBody byte[] body
    ) throws Exception {
        RepoConfig repoConfig = null;
        for (RepoConfig repo : downloader.config().repos) {
            if (repo.repoOwner.equalsIgnoreCase(repoOwner) && repo.repoName.equalsIgnoreCase(repoName)) {
                repoConfig = repo;
                break;
            }
        }
        if (repoConfig == null || !repoConfig.githubWebhookPath.equals(route)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (event == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        byte[] bytes = hmac256(repoConfig.githubWebhookSecret.getBytes(StandardCharsets.UTF_8), body);
        if (bytes == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        String requiredSignature = "sha256=" + hex(bytes);
        if (!requiredSignature.equals(signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        JsonNode node = Downloader.OBJECT_MAPPER.readTree(body);
        for (VersionChannel versionChannel : downloader.versionChannels()) {
            VersionChannelConfig config = versionChannel.getConfig();
            if (config.repoOwner.equalsIgnoreCase(repoOwner) && config.repoName.equalsIgnoreCase(repoName)) {
                versionChannel.receiveWebhook(event, node);
            }
        }
    }

    private byte[] hmac256(byte[] secretKey, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Downloader.LOGGER.error("Failed to get hmac for github webhook body", e);
            return null;
        }
    }

    private static final char[] HEX_CHARACTERS = "0123456789abcdef".toCharArray();
    private String hex(byte[] bytes) {
        try {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0, v; j < bytes.length; j++) {
                v = bytes[j] & 0xFF;
                hexChars[j * 2] = HEX_CHARACTERS[v >>> 4];
                hexChars[j * 2 + 1] = HEX_CHARACTERS[v & 0x0F];
            }
            return new String(hexChars);
        } catch (Exception e) {
            Downloader.LOGGER.error("Failed to convert byte[] to hex for github webhook signature", e);
            return null;
        }
    }
}
