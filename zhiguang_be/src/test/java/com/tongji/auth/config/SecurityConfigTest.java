package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void accessTokenCanAuthenticateApiRequests() {
        Jwt jwt = jwtWithType("access");

        assertThat(SecurityConfig.accessTokenAuthenticationConverter().convert(jwt)).isNotNull();
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequests() {
        Jwt jwt = jwtWithType("refresh");

        assertThatThrownBy(() -> SecurityConfig.accessTokenAuthenticationConverter().convert(jwt))
                .isInstanceOf(BadCredentialsException.class);
    }

    private Jwt jwtWithType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenType + "-token")
                .header("alg", "RS256")
                .subject("123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .build();
    }
}
