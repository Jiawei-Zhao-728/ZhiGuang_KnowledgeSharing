package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenJwtDecoderTest {

    @Test
    void acceptsAccessTokens() {
        Jwt accessJwt = jwt("access");
        JwtDecoder decoder = new AccessTokenJwtDecoder(token -> accessJwt);

        assertThat(decoder.decode("token")).isSameAs(accessJwt);
    }

    @Test
    void rejectsRefreshTokensAsBearerCredentials() {
        Jwt refreshJwt = jwt("refresh");
        JwtDecoder decoder = new AccessTokenJwtDecoder(token -> refreshJwt);

        assertThatThrownBy(() -> decoder.decode("token"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("Only access tokens");
    }

    private Jwt jwt(String tokenType) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("token_type", tokenType, "sub", "7")
        );
    }
}
