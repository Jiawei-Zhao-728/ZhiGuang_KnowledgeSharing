package com.tongji.auth.token;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * JWT decoder wrapper used by the resource server authentication path.
 * Refresh tokens are valid JWTs too, but they must never become API bearer credentials.
 */
public class AccessTokenJwtDecoder implements JwtDecoder {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtDecoder delegate;

    public AccessTokenJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) {
        Jwt jwt = delegate.decode(token);
        String tokenType = jwt.getClaimAsString(TOKEN_TYPE_CLAIM);
        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new BadJwtException("Only access tokens may authenticate API requests");
        }
        return jwt;
    }
}
