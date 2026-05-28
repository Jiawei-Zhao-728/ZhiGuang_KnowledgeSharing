package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigTest {

    @Test
    void bearerAuthenticationRejectsRefreshTokens() {
        Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
                new SecurityConfig().accessTokenAuthenticationConverter();

        Jwt refresh = jwtWithTokenType("refresh");

        assertThrows(OAuth2AuthenticationException.class, () -> converter.convert(refresh));
    }

    @Test
    void bearerAuthenticationAcceptsAccessTokens() {
        Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
                new SecurityConfig().accessTokenAuthenticationConverter();

        Jwt access = jwtWithTokenType("access");

        assertDoesNotThrow(() -> converter.convert(access));
    }

    private Jwt jwtWithTokenType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + tokenType)
                .header("alg", "none")
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("uid", 1L)
                .claim("token_type", tokenType)
                .build();
    }
}
