package dev.vankka.dsrvdownloader.util;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

public final class RequestSourceUtil {

    private RequestSourceUtil() {}

    public static String getRequestSource(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotEmpty(forwardedFor)) {
            return forwardedFor;
        }

        return request.getRemoteAddr();
    }
}
