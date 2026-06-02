package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenJwtAuthenticationConverterTest {

    private final AccessTokenJwtAuthenticationConverter converter = new AccessTokenJwtAuthenticationConverter();

    @Test
    void acceptsAccessToken() {
        Jwt jwt = jwtWithTokenType("access");

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(jwt);
    }

    @Test
    void rejectsRefreshToken() {
        Jwt jwt = jwtWithTokenType("refresh");

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bearer token must be an access token");
    }

    @Test
    void rejectsTokenWithoutTokenType() {
        Jwt jwt = Jwt.withTokenValue("token")
                .headers(headers -> headers.putAll(Map.of("alg", "RS256")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("123")
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bearer token must be an access token");
    }

    private static Jwt jwtWithTokenType(String tokenType) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("token")
                .headers(headers -> headers.putAll(Map.of("alg", "RS256")))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .subject("123")
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .build();
    }
}
