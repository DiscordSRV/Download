package dev.vankka.dsrvdownloader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vankka.dsrvdownloader.config.Config;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import dev.vankka.dsrvdownloader.model.channel.WorkflowChannel;
import dev.vankka.dsrvdownloader.model.channel.ReleaseChannel;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class Downloader {

    public static Logger LOGGER = LoggerFactory.getLogger("Downloader");
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public static String GITHUB_URL = "https://api.github.com";

    private static Config reloadConfig() throws IOException, NumberFormatException {
        Config config = OBJECT_MAPPER.readValue(new File("config.json"), Config.class);

        String port = System.getenv("DOWNLOADER_PORT");
        if (StringUtils.isNotEmpty(port)) {
            config.port = Integer.parseInt(port);
        }

        return config;
    }

    public static void main(String[] args) throws IOException {
        Config conf = reloadConfig();

        SpringApplication application = new SpringApplication(Downloader.class);
        application.setDefaultProperties(Collections.singletonMap("server.port", conf.port));
        application.run(args);
    }

    private Config config;
    private final OkHttpClient httpClient;
    private final List<VersionChannel> versionChannels = new ArrayList<>();
    private final AtomicBoolean refreshVersionChannels = new AtomicBoolean(false);

    public Downloader() throws IOException {
        this.config = reloadConfig();

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

        reloadVersionChannels();
    }

    public void reloadVersionChannels() {
        List<VersionChannel> newChannels = new ArrayList<>();
        for (VersionChannelConfig channelConfig : config.versionChannels) {
            VersionChannel channel;
            switch (channelConfig.type) {
                case RELEASE:
                    channel = new ReleaseChannel(this, channelConfig);
                    break;
                default:
                case WORKFLOW:
                    channel = new WorkflowChannel(this, channelConfig);
                    break;
            }
            newChannels.add(channel);
        }

        refreshVersionChannels.set(true);
        versionChannels.clear();
        versionChannels.addAll(newChannels);

        refreshVersionChannels.set(false);
        synchronized (refreshVersionChannels) {
            refreshVersionChannels.notifyAll();
        }
    }

    public Optional<VersionChannel> getChannel(String repoOwner, String repoName, String name) {
        if (refreshVersionChannels.get()) {
            synchronized (refreshVersionChannels) {
                try {
                    refreshVersionChannels.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

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

    public Config config() {
        return config;
    }

    public List<VersionChannel> versionChannels() {
        return versionChannels;
    }

    public OkHttpClient httpClient() {
        return httpClient;
    }
}
