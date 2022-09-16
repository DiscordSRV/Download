package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class VersionCheckRouteV2 implements Handler {

    private final Downloader downloader;

    public VersionCheckRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        VersionChannel channel = downloader.getChannel(ctx);
        String identifier = ctx.pathParam("identifier");

        try {
            int behind = channel.versionsBehind(identifier);

            ctx.result(String.valueOf(behind)); // TODO: more advanced response
        } catch (IllegalArgumentException ignored) {
            throw new BadRequestResponse();
        }
    }
}
