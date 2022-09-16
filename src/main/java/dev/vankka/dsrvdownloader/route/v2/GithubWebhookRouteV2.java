package dev.vankka.dsrvdownloader.route.v2;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.RepoConfig;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class GithubWebhookRouteV2 implements Handler {

    private final Downloader downloader;

    public GithubWebhookRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String repoOwner = ctx.pathParam("repoOwner");
        String repoName = ctx.pathParam("repoName");
        String route = ctx.pathParam("route");

        RepoConfig repoConfig = null;
        for (RepoConfig repo : downloader.config().repos) {
            if (repo.repoOwner.equalsIgnoreCase(repoOwner) && repo.repoName.equalsIgnoreCase(repoName)) {
                repoConfig = repo;
                break;
            }
        }
        if (repoConfig == null || !repoConfig.githubWebhookPath.equals(route)) {
            throw new ForbiddenResponse();
        }

        byte[] body = ctx.bodyAsBytes();
        byte[] bytes = hmac256(repoConfig.githubWebhookSecret.getBytes(StandardCharsets.UTF_8), body);
        if (bytes == null) {
            throw new ForbiddenResponse();
        }

        String signature = "sha256=" + hex(bytes);
        if (!signature.equals(ctx.header("X-Hub-Signature-256"))) {
            throw new ForbiddenResponse();
        }

        String event = ctx.header("X-GitHub-Event");
        if (event == null) {
            throw new BadRequestResponse();
        }

        JsonNode node = downloader.objectMapper().readTree(body);
        for (VersionChannel versionChannel : downloader.versionChannels()) {
            VersionChannelConfig config = versionChannel.config();
            if (config.repoOwner.equalsIgnoreCase(repoOwner) && config.repoName.equalsIgnoreCase(repoName)) {
                versionChannel.receiveWebhook(event, node);
            }
        }
    }

    private byte[] hmac256(byte[] secretKey, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec sks = new SecretKeySpec(secretKey, "HmacSHA256");
            mac.init(sks);
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
