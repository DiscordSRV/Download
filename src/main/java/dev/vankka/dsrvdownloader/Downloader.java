package dev.vankka.dsrvdownloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vankka.dsrvdownloader.config.Config;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.channel.CommitChannel;
import dev.vankka.dsrvdownloader.model.channel.TagChannel;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import dev.vankka.dsrvdownloader.route.v2.DownloadRouteV2;
import dev.vankka.dsrvdownloader.route.v2.GithubWebhookRouteV2;
import dev.vankka.dsrvdownloader.route.v2.VersionCheckRouteV2;
import dev.vankka.dsrvdownloader.route.v2.VersionsRouteV2;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Downloader {

    public static Logger LOGGER = LoggerFactory.getLogger("Downloader");
    public static String GITHUB_URL = "https://api.github.com";

    public static void main(String[] args) throws IOException {
        new Downloader();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    private Config config;
    private final List<VersionChannel> versionChannels = new ArrayList<>();

    @SuppressWarnings("resource") // Javalin is AutoClosable for some stupid reason
    private Downloader() throws IOException {
        reloadConfig();

        httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Request.Builder builder = request.newBuilder()
                            .removeHeader("User-Agent")
                            .addHeader("User-Agent", "DiscordSRVDownloader/2");
                    if (request.url().host().equals("github.com")) {
                        // TODO: auth
                    }

                    return chain.proceed(builder.build());
                })
                .build();

        Javalin app = Javalin.create().start("0.0.0.0", config.port);
        app.get("/v2/{repoOwner}/{repoName}/{channelName}/versions", new VersionsRouteV2(this));
        app.get("/v2/{repoOwner}/{repoName}/{channelName}/download/{commitOrRelease}", new DownloadRouteV2(this));
        app.get("/v2/{repoOwner}/{repoName}/{channelName}/version-check/{commitOrRelease}", new VersionCheckRouteV2(this));
        app.get("/v2/{repoOwner}/{repoName}/github-webhook/{route}", new GithubWebhookRouteV2(this));
    }

    public void reloadConfig() throws IOException, NumberFormatException {
        this.config = objectMapper.readValue(new File("config.json"), Config.class);

        String port = System.getenv("DOWNLOADER_PORT");
        if (StringUtils.isNotEmpty(port)) {
            config.port = Integer.parseInt(port);
        }

        versionChannels.clear();
        for (VersionChannelConfig channelConfig : config.versionChannels) {
            VersionChannel channel;
            switch (channelConfig.type) {
                case TAG:
                    channel = new TagChannel(this, channelConfig);
                    break;
                default:
                case COMMIT:
                    channel = new CommitChannel(this, channelConfig);
                    break;
            }
            versionChannels.add(channel);
        }
    }

    public VersionChannel getConfig(Context ctx) {
        String repoOwner = ctx.pathParam("repoOwner");
        String repoName = ctx.pathParam("repoName");
        String name = ctx.pathParam("channelName");

        return getConfig(repoOwner, repoName, name)
                .orElseThrow(NotFoundResponse::new);
    }

    public Optional<VersionChannel> getConfig(String repoOwner, String repoName, String name) {
        for (VersionChannel versionChannel : versionChannels) {
            VersionChannelConfig config = versionChannel.config();
            if (config.repoOwner.equalsIgnoreCase(repoOwner)
                    && config.repoName.equalsIgnoreCase(repoName)
                    && config.name.equalsIgnoreCase(name)) {
                return Optional.of(versionChannel);
            }
        }
        return Optional.empty();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public OkHttpClient httpClient() {
        return httpClient;
    }
}
