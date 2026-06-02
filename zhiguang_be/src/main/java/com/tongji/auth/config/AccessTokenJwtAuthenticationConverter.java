package com.tongji.auth.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Converts resource-server JWTs only when they are access tokens.
 *
 * <p>Refresh tokens are valid JWTs for the refresh endpoint, but must not be
 * accepted as bearer credentials for protected APIs.</p>
 */
public class AccessTokenJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
            throw new BadCredentialsException("Bearer token must be an access token");
        }
        return delegate.convert(jwt);
    }
}
