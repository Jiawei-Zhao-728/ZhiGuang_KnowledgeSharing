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

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void accessTokenAuthenticationConverterAcceptsAccessTokens() {
        Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
                securityConfig.accessTokenAuthenticationConverter();

        AbstractAuthenticationToken authentication = converter.convert(jwtWithTokenType("access"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("123");
    }

    @Test
    void accessTokenAuthenticationConverterRejectsRefreshTokens() {
        Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
                securityConfig.accessTokenAuthenticationConverter();

        assertThatThrownBy(() -> converter.convert(jwtWithTokenType("refresh")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Bearer token must be an access token");
    }

    private Jwt jwtWithTokenType(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + tokenType)
                .header("alg", "RS256")
                .subject("123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("token_type", tokenType)
                .claim("uid", 123L)
                .build();
    }
}
