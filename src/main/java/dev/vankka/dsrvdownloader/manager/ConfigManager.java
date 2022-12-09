package dev.vankka.dsrvdownloader.manager;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.AuthConfig;
import dev.vankka.dsrvdownloader.config.Config;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class ConfigManager {

    private final AuthConfig authConfig;
    private Config config;
    private OkHttpClient httpClient;

    private static InputStream stream(String filePath) throws IOException {
        return new BufferedInputStream(Files.newInputStream(Paths.get(filePath)));
    }

    public ConfigManager() throws IOException {
        authConfig = Downloader.OBJECT_MAPPER.readValue(stream("auth_config.json"), AuthConfig.class);
        reloadConfig();
    }

    public void reload() {
        try {
            this.config = reloadConfig();
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to load configuration", e);
        }
    }

    private Config reloadConfig() throws IOException {
        config = Downloader.OBJECT_MAPPER.readValue(stream("config.json"), Config.class);
        httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Request.Builder builder = request.newBuilder()
                            .removeHeader("User-Agent")
                            .addHeader("User-Agent", "DiscordSRVDownloader/2");

                    String host = request.url().host();
                    if ((host.equals("github.com") || host.equals("api.github.com"))
                            && StringUtils.isNotEmpty(config.githubToken)) {
                        builder.addHeader("Authorization", "Bearer " + config.githubToken);
                    }

                    return chain.proceed(builder.build());
                })
                .build();

        return config;
    }

    public Config config() {
        return config;
    }

    public AuthConfig authConfig() {
        return authConfig;
    }

    public OkHttpClient httpClient() {
        return httpClient;
    }
}
