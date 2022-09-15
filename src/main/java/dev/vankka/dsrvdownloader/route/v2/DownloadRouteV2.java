package dev.vankka.dsrvdownloader.route.v2;

import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.model.Version;
import dev.vankka.dsrvdownloader.model.channel.VersionChannel;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class DownloadRouteV2 implements Handler {

    private final Downloader downloader;

    public DownloadRouteV2(Downloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        VersionChannel channel = downloader.getConfig(ctx);

        String identifier = ctx.pathParam("commitOrRelease");
        Version version = channel.versions().get(identifier);
        if (version == null) {
            throw new NotFoundResponse();
        }

        ctx.header("Content-Disposition", "attachment; filename=\"" + version.getName() + "\"");
        ctx.header("Content-Type", "application/java-archive");
        ctx.header("Content-Length", String.valueOf(version.getSize()));

        byte[] content = version.getContent();

        try (InputStream inputStream =
                     content != null
                        ? new ByteArrayInputStream(content)
                        : Files.newInputStream(version.getFile())
        ) {
            try (OutputStream outputStream = ctx.res.getOutputStream()) {
                IOUtils.copy(inputStream, outputStream);
            }
        }
    }
}
