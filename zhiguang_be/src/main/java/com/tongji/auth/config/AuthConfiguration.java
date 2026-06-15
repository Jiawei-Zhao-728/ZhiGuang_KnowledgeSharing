package com.tongji.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

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

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

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
     * 创建 JWT 解码器。
     *
     * <p>读取 RSA 公钥并构造基于 Nimbus 的 {@link JwtDecoder}。</p>
     *
     * @return 基于 RSA 公钥的 {@link JwtDecoder}。
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return buildJwtDecoder();
    }

    /**
     * 创建仅供资源服务器 Bearer 鉴权使用的访问令牌解码器。
     *
     * <p>刷新令牌也由本服务签发并可被通用解码器解析，但不能作为 API Bearer Token 使用。</p>
     *
     * @return 仅接受 access token 的 {@link JwtDecoder}。
     */
    @Bean
    public JwtDecoder accessTokenJwtDecoder() {
        NimbusJwtDecoder decoder = buildJwtDecoder();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                accessTokenValidator()
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private NimbusJwtDecoder buildJwtDecoder() {
        AuthProperties.Jwt jwtProps = properties.getJwt();
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    private OAuth2TokenValidator<Jwt> accessTokenValidator() {
        return jwt -> {
            if (ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(CLAIM_TOKEN_TYPE))) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Bearer authentication requires an access token",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}
