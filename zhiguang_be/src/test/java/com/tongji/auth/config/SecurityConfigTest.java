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

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> converter =
            new SecurityConfig().accessTokenAuthenticationConverter();

    @Test
    void accessTokenCanAuthenticateResourceRequests() {
        AbstractAuthenticationToken authentication = converter.convert(jwtWithTokenType("access"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("123");
    }

    @Test
    void refreshTokenCannotAuthenticateResourceRequests() {
        assertThatThrownBy(() -> converter.convert(jwtWithTokenType("refresh")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Only access tokens");
    }

    @Test
    void missingTokenTypeCannotAuthenticateResourceRequests() {
        Jwt jwt = Jwt.withTokenValue("missing-token-type")
                .header("alg", "RS256")
                .subject("123")
                .claim("uid", 123L)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Only access tokens");
    }

    private Jwt jwtWithTokenType(String tokenType) {
        return Jwt.withTokenValue(tokenType + "-token")
                .header("alg", "RS256")
                .subject("123")
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
