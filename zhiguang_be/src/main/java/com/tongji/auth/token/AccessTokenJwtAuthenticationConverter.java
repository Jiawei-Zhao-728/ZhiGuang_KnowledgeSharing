package com.tongji.auth.token;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Converts only access JWTs into authenticated API principals.
 */
@Component
public class AccessTokenJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final OAuth2Error INVALID_TOKEN_ERROR = new OAuth2Error(
            "invalid_token",
            "Only access tokens are accepted for API authentication",
            null
    );

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
            throw new OAuth2AuthenticationException(INVALID_TOKEN_ERROR);
        }
        return delegate.convert(jwt);
    }
}
