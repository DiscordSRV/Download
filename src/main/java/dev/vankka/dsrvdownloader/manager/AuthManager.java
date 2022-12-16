package dev.vankka.dsrvdownloader.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vankka.dsrvdownloader.util.AuthFilter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class AuthManager implements AuthenticationManager {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Cache<String, Boolean> tokens = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).build();

    public void putToken(String token) {
        this.tokens.put(token, false);
    }

    public String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Bean
    public SecurityFilterChain authChain(HttpSecurity security) throws Exception {
        AuthFilter filter = new AuthFilter();
        filter.setAuthenticationManager(this);
        return security
                .csrf().disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilter(filter)
                .authorizeHttpRequests(authorize -> authorize
                        .antMatchers("/admin/login/**").permitAll()
                        .antMatchers("/admin/**").authenticated()
                )
                .build();
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String authorization = (String) authentication.getPrincipal();
        if (StringUtils.isEmpty(authorization)) {
            throw new BadCredentialsException("No authorization");
        }
        if (authorization.startsWith("Bearer") && authorization.contains(" ")) {
            authorization = authorization.substring(authorization.indexOf(" ") + 1);
        }

        if (StringUtils.isBlank(authorization) || tokens.getIfPresent(authorization) == null) {
            throw new BadCredentialsException("Bad authorization");
        }

        authentication.setAuthenticated(true);
        return authentication;
    }
}
