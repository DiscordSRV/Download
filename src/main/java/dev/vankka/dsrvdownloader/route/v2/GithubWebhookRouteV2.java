package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class GithubWebhookRouteV2 implements Handler {

    private final Downloader downloader;

    public GithubWebhookRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String repoOwner = ctx.pathParam("repoOwner");
        String repoName = ctx.pathParam("repoName");
        String path = ctx.pathParam("path");

    }
}
