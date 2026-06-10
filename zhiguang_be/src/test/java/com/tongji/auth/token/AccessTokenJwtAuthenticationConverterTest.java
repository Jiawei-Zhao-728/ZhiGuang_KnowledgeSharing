package com.tongji.auth.token;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenJwtAuthenticationConverterTest {

    private final AccessTokenJwtAuthenticationConverter converter = new AccessTokenJwtAuthenticationConverter();

    @Test
    void convertsAccessToken() {
        Jwt jwt = jwtWithTokenType("access");

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(jwt);
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    void rejectsRefreshToken() {
        Jwt jwt = jwtWithTokenType("refresh");

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Only access tokens are accepted");
    }

    @Test
    void rejectsTokenWithoutTypeClaim() {
        Jwt jwt = jwtWithClaims(Map.of("uid", 123L));

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Only access tokens are accepted");
    }

    private static Jwt jwtWithTokenType(String tokenType) {
        return jwtWithClaims(Map.of(
                "token_type", tokenType,
                "uid", 123L
        ));
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-10T00:15:00Z"),
                Map.of("alg", "RS256"),
                claims
        );
    }
}
