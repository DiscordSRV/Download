package dev.vankka.dsrvdownloader.model.channel;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.VersionChannelConfig;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class WorkflowChannel extends AbstractVersionChannel {

    public WorkflowChannel(Downloader downloader, VersionChannelConfig config) {
        super(downloader, config);
    }

    private void updateCommits() {
        Request request = new Request.Builder()
                .url(baseRepoUrl() + "/commits")
                .get().build();

        try (Response response = downloader.httpClient().newCall(request).execute()) {
            System.out.println(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int versionsBehind(String comparedTo) {
        return 0;
    }

    @Override
    public void receiveWebhook(String event, JsonNode node) {

    }
}
