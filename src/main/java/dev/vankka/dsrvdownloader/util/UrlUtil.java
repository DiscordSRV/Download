package dev.vankka.dsrvdownloader.util;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import javax.servlet.http.HttpServletRequest;

public final class UrlUtil {

    private UrlUtil() {}

    public static String getUrl(HttpServletRequest request) {
        ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromContextPath(request);
        UriComponents components = builder.build();
        if ("http".equals(components.getScheme()) && "localhost".equals(components.getHost())) {
            builder.scheme("https");
        }

        return builder.build().toUriString();
    }
}
