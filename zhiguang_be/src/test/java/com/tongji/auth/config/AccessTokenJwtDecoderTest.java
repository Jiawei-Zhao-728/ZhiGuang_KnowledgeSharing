package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenJwtDecoderTest {

    @Test
    void returnsDecodedJwtWhenTokenTypeIsAccess() {
        JwtDecoder delegate = mock(JwtDecoder.class);
        Jwt accessJwt = jwtWithTokenType("access");
        when(delegate.decode("access-token")).thenReturn(accessJwt);

        AccessTokenJwtDecoder decoder = new AccessTokenJwtDecoder(delegate);

        assertThat(decoder.decode("access-token")).isSameAs(accessJwt);
    }

    @Test
    void rejectsRefreshTokenForApiAuthentication() {
        JwtDecoder delegate = mock(JwtDecoder.class);
        when(delegate.decode("refresh-token")).thenReturn(jwtWithTokenType("refresh"));

        AccessTokenJwtDecoder decoder = new AccessTokenJwtDecoder(delegate);

        assertThatThrownBy(() -> decoder.decode("refresh-token"))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("Only access tokens");
    }

    @Test
    void rejectsTokenWithoutAccessTypeForApiAuthentication() {
        JwtDecoder delegate = mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "123")
                .build();
        when(delegate.decode("token")).thenReturn(jwt);

        AccessTokenJwtDecoder decoder = new AccessTokenJwtDecoder(delegate);

        assertThatThrownBy(() -> decoder.decode("token"))
                .isInstanceOf(BadJwtException.class);
    }

    private static Jwt jwtWithTokenType(String tokenType) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "123")
                .claim("token_type", tokenType)
                .build();
    }
}
