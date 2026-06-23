package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenJwtAuthenticationConverterTest {

    private final AccessTokenJwtAuthenticationConverter converter = new AccessTokenJwtAuthenticationConverter();

    @Test
    void acceptsAccessTokens() {
        Jwt jwt = jwtWithType("access");

        Authentication authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(jwt);
    }

    @Test
    void rejectsRefreshTokens() {
        Jwt jwt = jwtWithType("refresh");

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("access token");
    }

    private Jwt jwtWithType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + tokenType)
                .header("alg", "RS256")
                .subject("123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .build();
    }
}
