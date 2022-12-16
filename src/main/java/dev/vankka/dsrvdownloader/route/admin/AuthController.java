package dev.vankka.dsrvdownloader.route.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.AuthConfig;
import dev.vankka.dsrvdownloader.manager.AuthManager;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import dev.vankka.dsrvdownloader.util.HttpContentUtil;
import dev.vankka.dsrvdownloader.util.RequestSourceUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

@RestController
public class AuthController {

    private final AuthManager authManager;
    private final ConfigManager configManager;
    private final OkHttpClient httpClient = new OkHttpClient();

    // 10 requests / 5 minutes
    @SuppressWarnings("unchecked")
    private final CaffeineProxyManager<String> authRateLimit = new CaffeineProxyManager<>(
            (Caffeine<String, RemoteBucketState>) (Object) Caffeine.newBuilder(),
            Duration.ofMinutes(1));
    private final Supplier<BucketConfiguration> authRateLimitSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(10, Duration.ofMinutes(5))).build();

    public AuthController(AuthManager authManager, ConfigManager configManager) {
        this.authManager = authManager;
        this.configManager = configManager;
    }

    private String getRedirectUri(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString() + TOKEN_PATH;
    }

    private String cookie(String state) {
        return "state=" + state + ";Secure;HttpOnly;Path=" + TOKEN_PATH + ";SameSite=Lax";
    }

    @GetMapping(path = "/admin/login")
    public View login(HttpServletRequest request, HttpServletResponse response) {
        AuthConfig authConfig = configManager.authConfig();
        String state = authManager.createToken();

        String url = Objects.requireNonNull(HttpUrl.parse("https://discord.com/oauth2/authorize"))
                .newBuilder()
                .setQueryParameter("client_id", authConfig.clientId)
                .setQueryParameter("scope", "identify")
                .addQueryParameter("redirect_uri", getRedirectUri(request))
                .addQueryParameter("response_type", "code")
                .addQueryParameter("state", state)
                .build().url().toString();

        response.addHeader("Set-Cookie", cookie(state) + ";Max-Age=60");
        return new RedirectView(url);
    }

    private static final String TOKEN_PATH = "/admin/login/token";
    @GetMapping(path = TOKEN_PATH)
    public Object authorize(
            @RequestParam(name = "code") String code,
            @RequestParam(name = "state") String state,
            @CookieValue(name = "state") String stateCookie,
            HttpServletRequest request,
            HttpServletResponse servletResponse
    ) throws IOException {
        String requester = RequestSourceUtil.getRequestSource(request);

        BucketProxy bucket = authRateLimit.builder().build(requester, authRateLimitSupplier);
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }

        if (!state.equals(stateCookie)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        servletResponse.addHeader("Set-Cookie", cookie("") + ";Max-Age=0");

        AuthConfig authConfig = configManager.authConfig();
        String redirectUri = getRedirectUri(request);

        Request codeRequest = new Request.Builder().url("https://discord.com/api/oauth2/token")
                .post(new MultipartBody.Builder()
                              .addFormDataPart("client_id", authConfig.clientId)
                              .addFormDataPart("client_secret", authConfig.clientSecret)
                              .addFormDataPart("grant_type", "authorization_code")
                              .addFormDataPart("code", code)
                              .addFormDataPart("redirect_uri", redirectUri)
                              .setType(MultipartBody.FORM)
                              .build())
                .build();

        String accessToken;
        String scope;
        try (Response response = httpClient.newCall(codeRequest).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) {
                Downloader.LOGGER.debug("Discord auth fail: " + HttpContentUtil.prettify(response, responseBody));
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }

            JsonNode node = Downloader.OBJECT_MAPPER.readTree(responseBody.byteStream());
            accessToken = node.get("access_token").asText();
            scope = node.get("scope").asText();
        }

        try {
            if (!scope.contains("identify")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "identify is required");
            }

            Request userRequest = new Request.Builder()
                    .url("https://discord.com/api/users/@me")
                    .header("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(userRequest).execute()) {
                ResponseBody responseBody;
                if (!response.isSuccessful() || (responseBody = response.body()) == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
                }

                JsonNode node = Downloader.OBJECT_MAPPER.readTree(responseBody.byteStream());
                String id = node.get("id").asText();

                if (!authConfig.discordUserIds.contains(id)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
            }
        } finally {
            Request revokeRequest = new Request.Builder()
                    .url("https://discord.com/api/oauth2/token/revoke")
                    .post(new MultipartBody.Builder()
                                  .addFormDataPart("token", accessToken)
                                  .addFormDataPart("client_id", authConfig.clientId)
                                  .addFormDataPart("client_secret", authConfig.clientSecret)
                                  .setType(MultipartBody.FORM)
                                  .build())
                    .build();

            try {
                httpClient.newCall(revokeRequest).execute().close();
            } catch (IOException e) {
                Downloader.LOGGER.error("Failed to revoke Discord token", e);
            }
        }

        String token = authManager.createToken();
        authManager.putToken(token);
        return ResponseEntity.ok(token);
    }
}
