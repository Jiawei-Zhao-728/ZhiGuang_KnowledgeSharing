package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final Converter<Jwt, ? extends Authentication> converter = new SecurityConfig().jwtAuthenticationConverter();

    @Test
    void jwtAuthenticationConverterAcceptsAccessTokens() {
        Authentication authentication = converter.convert(jwtWithType("access"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void jwtAuthenticationConverterRejectsRefreshTokens() {
        Jwt refreshToken = jwtWithType("refresh");

        assertThatThrownBy(() -> converter.convert(refreshToken))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Bearer token must be an access token");
    }

    private Jwt jwtWithType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + tokenType)
                .header("alg", "none")
                .subject("123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .build();
    }
}
