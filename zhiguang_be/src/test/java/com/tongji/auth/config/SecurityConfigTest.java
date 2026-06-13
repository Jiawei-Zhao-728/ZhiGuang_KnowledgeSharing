package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
            new SecurityConfig().accessTokenJwtAuthenticationConverter();

    @Test
    void accessTokenCanAuthenticateBearerRequest() {
        AbstractAuthenticationToken authentication = converter.convert(jwtWithType("access"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void refreshTokenCannotAuthenticateBearerRequest() {
        Jwt refreshJwt = jwtWithType("refresh");

        assertThatThrownBy(() -> converter.convert(refreshJwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("access token");
    }

    private Jwt jwtWithType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenType + "-token")
                .header("alg", "RS256")
                .subject("123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("token_type", tokenType)
                .claim("uid", 123L)
                .build();
    }
}
