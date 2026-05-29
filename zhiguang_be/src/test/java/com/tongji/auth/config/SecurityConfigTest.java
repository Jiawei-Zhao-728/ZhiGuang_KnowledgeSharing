package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void protectedApiAuthenticationRejectsRefreshTokens() {
        SecurityConfig config = new SecurityConfig();
        Jwt refreshToken = jwtWithTokenType("refresh");

        assertThatThrownBy(() -> config.accessTokenAuthenticationConverter().convert(refreshToken))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void protectedApiAuthenticationAcceptsAccessTokens() {
        SecurityConfig config = new SecurityConfig();
        Jwt accessToken = jwtWithTokenType("access");

        assertThat(config.accessTokenAuthenticationConverter().convert(accessToken)).isNotNull();
    }

    private Jwt jwtWithTokenType(String tokenType) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "1", "token_type", tokenType));
    }
}
