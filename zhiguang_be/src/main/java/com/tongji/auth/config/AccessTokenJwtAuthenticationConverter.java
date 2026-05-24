package com.tongji.auth.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Resource-server converter that only accepts access tokens for API authentication.
 */
final class AccessTokenJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TOKEN_TYPE = "token_type";

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
            throw new BadCredentialsException("Bearer token must be an access token");
        }
        return delegate.convert(jwt);
    }
}
