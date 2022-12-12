package dev.vankka.dsrvdownloader.route.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vankka.dsrvdownloader.Downloader;
import dev.vankka.dsrvdownloader.config.AuthConfig;
import dev.vankka.dsrvdownloader.config.Config;
import dev.vankka.dsrvdownloader.manager.ChannelManager;
import dev.vankka.dsrvdownloader.manager.ConfigManager;
import dev.vankka.dsrvdownloader.util.HttpContentUtil;
import dev.vankka.dsrvdownloader.util.RequestSourceUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import okhttp3.ResponseBody;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;

@RestController
public class AdminController {

    private final ConfigManager configManager;
    private final ChannelManager channelManager;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final SecureRandom secureRandom = new SecureRandom();

    // 10 requests / 5 minutes
    @SuppressWarnings("unchecked")
    private final CaffeineProxyManager<String> authRateLimit = new CaffeineProxyManager<>(
            (Caffeine<String, RemoteBucketState>) (Object) Caffeine.newBuilder(),
            Duration.ofMinutes(1)
    );
    private final Supplier<BucketConfiguration> authRateLimitSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(10, Duration.ofMinutes(5))).build();

    private final Cache<String, Boolean> tokens = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).build();

    public AdminController(ConfigManager configManager, ChannelManager channelManager) {
        this.configManager = configManager;
        this.channelManager = channelManager;
    }

    private String getRedirectUri(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString()
                + "/admin/token";
    }

    private void checkAuthorization(String authorization) {
        if (StringUtils.isBlank(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (authorization.startsWith("Bearer") && authorization.contains(" ")) {
            authorization = authorization.substring(authorization.indexOf(" ") + 1);
        }
        if (StringUtils.isBlank(authorization) || tokens.getIfPresent(authorization) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    private String cookie(String state) {
        return "state=" + state + ";Secure;HttpOnly;Path=/admin/token;SameSite=Lax";
    }

    @GetMapping(path = "/admin/login")
    public View login(HttpServletRequest request, HttpServletResponse response) {
        AuthConfig authConfig = configManager.authConfig();
        String state = createToken();

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

    @GetMapping(path = "/admin/token")
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

        String token = createToken();
        tokens.put(token, false);
        return ResponseEntity.ok(token);
    }

    @GetMapping(path = "/admin/config")
    public ResponseEntity<?> getConfig(@RequestHeader("Authorization") String authorization) {
        checkAuthorization(authorization);

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Downloader.OBJECT_MAPPER.writeValueAsString(configManager.config()));
        } catch (Throwable e) {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Failed to write config\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    @PostMapping(path = "/admin/config")
    @ResponseStatus(code = HttpStatus.OK, reason = "Success")
    public void updateConfig(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Config config
    ) {
        checkAuthorization(authorization);

        try {
            configManager.replaceConfig(config);
        } catch (Throwable e) {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Failed to save/load config\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    @PostMapping(path = "/admin/reload-channels")
    @ResponseStatus(code = HttpStatus.OK, reason = "Reloaded")
    public void reloadChannels(@RequestHeader("Authorization") String authorization) {
        checkAuthorization(authorization);
        try {
            channelManager.reloadVersionChannels();
        } catch (Throwable e) {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Failed to reload channels\n" + ExceptionUtils.getStackTrace(e));
        }
    }
}
