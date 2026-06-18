package com.tongji.auth.token;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Ensures protected resource requests are authenticated with access tokens only.
 */
public class AccessTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final OAuth2Error INVALID_TOKEN_TYPE = new OAuth2Error(
            "invalid_token",
            "JWT token_type must be access",
            null
    );

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if ("access".equals(token.getClaimAsString(CLAIM_TOKEN_TYPE))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN_TYPE);
    }
}
