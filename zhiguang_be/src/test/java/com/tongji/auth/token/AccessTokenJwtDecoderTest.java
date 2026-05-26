package com.tongji.auth.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenJwtDecoderTest {

    @Test
    void decodeReturnsAccessTokens() {
        Jwt accessJwt = jwtWithType("access");
        JwtDecoder delegate = mock(JwtDecoder.class);
        when(delegate.decode("access-token")).thenReturn(accessJwt);

        AccessTokenJwtDecoder decoder = new AccessTokenJwtDecoder(delegate);

        assertThat(decoder.decode("access-token")).isSameAs(accessJwt);
    }

    @Test
    void decodeRejectsRefreshTokensAsBearerCredentials() {
        JwtDecoder delegate = mock(JwtDecoder.class);
        when(delegate.decode("refresh-token")).thenReturn(jwtWithType("refresh"));

        AccessTokenJwtDecoder decoder = new AccessTokenJwtDecoder(delegate);

        assertThatThrownBy(() -> decoder.decode("refresh-token"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("access tokens");
    }

    private Jwt jwtWithType(String tokenType) {
        Instant now = Instant.parse("2026-05-26T00:00:00Z");
        return new Jwt(
                tokenType + "-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "123", "uid", 123L, "token_type", tokenType)
        );
    }
}
