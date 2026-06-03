package com.tongji.auth.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenJwtAuthenticationConverterTest {

    private final AccessTokenJwtAuthenticationConverter converter = new AccessTokenJwtAuthenticationConverter();

    @Test
    void convertsAccessToken() {
        assertThat(converter.convert(jwt("access"))).isNotNull();
    }

    @Test
    void rejectsRefreshTokenAsBearerCredential() {
        assertThatThrownBy(() -> converter.convert(jwt("refresh")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Only access tokens may be used as bearer credentials");
    }

    private Jwt jwt(String tokenType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + tokenType)
                .header("alg", "RS256")
                .subject("123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("uid", 123L)
                .claim("token_type", tokenType)
                .build();
    }
}
