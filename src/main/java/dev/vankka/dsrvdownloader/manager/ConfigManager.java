package dev.vankka.dsrvdownloader.manager;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.Config;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class ConfigManager {

    private Config config;
    private OkHttpClient httpClient;

    public ConfigManager() throws IOException {
        reloadConfig();
    }

    public void reload() {
        try {
            this.config = reloadConfig();
        } catch (IOException e) {
            Downloader.LOGGER.error("Failed to load configuration", e);
        }
    }

    private Config reloadConfig() throws IOException, NumberFormatException {
        config = Downloader.OBJECT_MAPPER.readValue(new File("config.json"), Config.class);
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

    public OkHttpClient httpClient() {
        return httpClient;
    }
}
