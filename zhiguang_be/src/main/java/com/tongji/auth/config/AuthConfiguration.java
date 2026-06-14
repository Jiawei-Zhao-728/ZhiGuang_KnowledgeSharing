package com.tongji.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 认证相关 Bean 配置。
 * <p>
 * - `PasswordEncoder`：根据配置的 BCrypt 强度创建；
 * - `JwtEncoder/Decoder`：读取配置中的 RSA 私钥/公钥并构造 Nimbus 实现；
 * - JWK 使用 `keyId` 标识，供下游验证与密钥轮换。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class AuthConfiguration {

    private final AuthProperties properties;

    /**
     * 创建密码编码器（BCrypt）。
     *
     * @return 使用配置的强度构造的 {@link PasswordEncoder}。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(properties.getPassword().getBcryptStrength());
    }

    /**
     * 创建 JWT 编码器。
     *
     * <p>读取 RSA 私钥/公钥并构造 JWK，使用 Nimbus 实现生成 {@link JwtEncoder}。</p>
     *
     * @return 基于 RSA JWK 的 {@link JwtEncoder}。
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        AuthProperties.Jwt jwtProps = properties.getJwt();
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(jwtProps.getPrivateKey());
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());
        RSAKey jwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(jwtProps.getKeyId())
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 创建供 Spring Security Resource Server 使用的 Access Token 解码器。
     *
     * <p>读取 RSA 公钥并构造基于 Nimbus 的 {@link JwtDecoder}，额外要求
     * `token_type=access`，避免 Refresh Token 被当作 Bearer 凭证访问受保护 API。</p>
     *
     * @return 只接受 Access Token 的 {@link JwtDecoder}。
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = buildJwtDecoder();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                accessTokenValidator()
        ));
        return decoder;
    }

    /**
     * 创建通用 JWT 解码器，供业务层解析 Refresh Token 使用。
     *
     * @return 不限制 `token_type` 的 {@link JwtDecoder}。
     */
    @Bean
    public JwtDecoder tokenJwtDecoder() {
        return buildJwtDecoder();
    }

    private NimbusJwtDecoder buildJwtDecoder() {
        AuthProperties.Jwt jwtProps = properties.getJwt();
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private OAuth2TokenValidator<Jwt> accessTokenValidator() {
        return jwt -> {
            Object tokenType = jwt.getClaims().get("token_type");
            if ("access".equals(tokenType)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Bearer token must be an access token",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}
