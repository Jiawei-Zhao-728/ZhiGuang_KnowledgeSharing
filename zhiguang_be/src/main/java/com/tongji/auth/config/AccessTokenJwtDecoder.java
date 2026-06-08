package com.tongji.auth.config;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Resource-server decoder wrapper that prevents refresh JWTs from acting as API Bearer credentials.
 */
public class AccessTokenJwtDecoder implements JwtDecoder {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtDecoder delegate;

    public AccessTokenJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Jwt jwt = delegate.decode(token);
        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new BadJwtException("Only access tokens are accepted as Bearer credentials");
        }
        return jwt;
    }
}
