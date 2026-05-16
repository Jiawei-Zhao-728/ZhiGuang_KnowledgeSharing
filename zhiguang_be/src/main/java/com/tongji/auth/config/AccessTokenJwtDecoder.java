package com.tongji.auth.config;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Resource-server decoder wrapper that prevents refresh JWTs from authenticating API calls.
 */
final class AccessTokenJwtDecoder implements JwtDecoder {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtDecoder delegate;

    AccessTokenJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) {
        Jwt jwt = delegate.decode(token);
        if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
            throw new BadJwtException("Only access tokens may authenticate API requests");
        }
        return jwt;
    }
}
