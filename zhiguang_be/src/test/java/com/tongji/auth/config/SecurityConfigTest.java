package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
            new SecurityConfig().accessTokenAuthenticationConverter();

    @Test
    void accessTokenAuthenticatesApiRequest() {
        AbstractAuthenticationToken authentication = converter.convert(jwtWithTokenType("access"));

        assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequest() {
        assertThatThrownBy(() -> converter.convert(jwtWithTokenType("refresh")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private Jwt jwtWithTokenType(String tokenType) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("123")
                .claim("token_type", tokenType)
                .claim("uid", 123L)
                .build();
    }
}
