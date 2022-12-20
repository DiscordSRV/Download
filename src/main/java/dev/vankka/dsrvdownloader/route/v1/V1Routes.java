package dev.vankka.dsrvdownloader.route.v1;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@RestController
@Deprecated
public class V1Routes {

    public V1Routes() throws IOException {
        try (Stream<Path> paths = Files.list(Paths.get("storage"))) {
            paths.forEach(path -> {
                if (path.getFileName().toString().endsWith(".jar")) {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    @PostMapping(path = "/github-webhook")
    @ResponseStatus(HttpStatus.GONE)
    @Deprecated
    public void githubWebhook() {
        throw new ResponseStatusException(HttpStatus.GONE);
    }

    @GetMapping(path = "/")
    @ResponseStatus(HttpStatus.FOUND)
    @Deprecated
    public View rootRedirect() {
        return new RedirectView("/release");
    }

    @GetMapping(path = "/{type}")
    @ResponseStatus(HttpStatus.FOUND)
    @Deprecated
    public Object getJar(@PathVariable String type) {
        if (type.equalsIgnoreCase("release")) {
            return new RedirectView("/v2/DiscordSRV/DiscordSRV/release/download/latest/jar");
        } else if (type.equalsIgnoreCase("snapshot")) {
            return new RedirectView("/v2/DiscordSRV/DiscordSRV/snapshot/download/latest/jar");
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

}
