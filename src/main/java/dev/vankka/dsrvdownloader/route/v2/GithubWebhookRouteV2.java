package dev.vankka.dsrvdownloader.route.v2;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.RepoConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import dev.vankka.dsrvdownloader.util.Hex;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ConfigManager configManager;
    private final ChannelManager channelManager;

    @Autowired
    public GithubWebhookRouteV2(ConfigManager configManager, ChannelManager channelManager) {
        this.configManager = configManager;
        this.channelManager = channelManager;
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
        for (RepoConfig repo : configManager.config().repos) {
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

        String requiredSignature = "sha256=" + Hex.toHexString(bytes);
        if (!requiredSignature.equals(signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        JsonNode node = Downloader.OBJECT_MAPPER.readTree(body);
        for (VersionChannel versionChannel : channelManager.versionChannels()) {
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

}
