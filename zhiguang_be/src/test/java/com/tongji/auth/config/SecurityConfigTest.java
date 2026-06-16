package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void accessTokenConverterAcceptsAccessTokens() {
        var converter = securityConfig.accessTokenJwtAuthenticationConverter();

        var authentication = converter.convert(jwtWithTokenType("access"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(Jwt.class);
    }

    @Test
    void accessTokenConverterRejectsRefreshTokens() {
        var converter = securityConfig.accessTokenJwtAuthenticationConverter();

        assertThatThrownBy(() -> converter.convert(jwtWithTokenType("refresh")))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("access token");
    }

    private static Jwt jwtWithTokenType(String tokenType) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                Map.of("alg", "RS256"),
                Map.of("sub", "123", "uid", 123L, "token_type", tokenType)
        );
    }
}
