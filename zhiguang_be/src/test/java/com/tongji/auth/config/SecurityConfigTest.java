package com.tongji.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void jwtAuthenticationConverterAcceptsAccessTokensOnly() {
        Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
                new SecurityConfig().jwtAuthenticationConverter();

        AbstractAuthenticationToken authentication = converter.convert(jwtWithTokenType("access"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(Jwt.class);
        assertThatThrownBy(() -> converter.convert(jwtWithTokenType("refresh")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private Jwt jwtWithTokenType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenType + "-token")
                .header("alg", "none")
                .subject("123")
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
