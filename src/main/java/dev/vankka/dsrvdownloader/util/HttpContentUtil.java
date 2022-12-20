package dev.vankka.dsrvdownloader.util;

import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.io.IOException;

public final class HttpContentUtil {

    private HttpContentUtil() {}

    public static String status(int status) {
        HttpStatus httpStatus = null;
        try {
            httpStatus = HttpStatus.valueOf(status);
        } catch (Throwable ignored) {}
        return status + (httpStatus != null ? " " + httpStatus.getReasonPhrase() : "");
    }

    public static String prettify(Response response, @Nullable ResponseBody body) throws IOException {
        int status = response.code();
        if (body == null) {
            return status(status) + " (no response body)";
        }

        String bodyString = body.string().trim();
        if (status != HttpStatus.TOO_MANY_REQUESTS.value()
                && (bodyString.startsWith("<!DOCTYPE html>") || bodyString.startsWith("<html"))) {
            return status(status) + ": <html body>";
        }

        return status(status) + ": " + bodyString;
    }
}
