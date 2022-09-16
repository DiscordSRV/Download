package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class VersionsRouteV2 implements Handler {

    private final Downloader downloader;

    public VersionsRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        VersionChannel channel = downloader.getChannel(ctx);

        ctx.contentType("application/json");
        ctx.result(channel.versionResponse());
    }
}
