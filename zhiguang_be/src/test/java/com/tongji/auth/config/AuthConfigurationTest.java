package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthConfigurationTest {

    @Test
    void accessTokenDecoderAcceptsAccessTokens() {
        JwtDecoder rawDecoder = mock(JwtDecoder.class);
        Jwt accessJwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .claim("token_type", "access")
                .build();
        when(rawDecoder.decode("access-token")).thenReturn(accessJwt);

        JwtDecoder decoder = new AuthConfiguration(new AuthProperties()).accessTokenJwtDecoder(rawDecoder);

        assertThat(decoder.decode("access-token")).isSameAs(accessJwt);
    }

    @Test
    void accessTokenDecoderRejectsRefreshTokens() {
        JwtDecoder rawDecoder = mock(JwtDecoder.class);
        Jwt refreshJwt = Jwt.withTokenValue("refresh-token")
                .header("alg", "RS256")
                .claim("token_type", "refresh")
                .build();
        when(rawDecoder.decode("refresh-token")).thenReturn(refreshJwt);

        JwtDecoder decoder = new AuthConfiguration(new AuthProperties()).accessTokenJwtDecoder(rawDecoder);

        assertThatThrownBy(() -> decoder.decode("refresh-token"))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Only access tokens are accepted");
    }
}
