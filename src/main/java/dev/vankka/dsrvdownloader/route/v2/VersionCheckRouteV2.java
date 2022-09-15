package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class VersionCheckRouteV2 implements Handler {

    private final Downloader downloader;

    public VersionCheckRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        VersionChannel channel = downloader.getConfig(ctx);

        String commitOrRelease = ctx.pathParam("commitOrRelease");


    }
}
