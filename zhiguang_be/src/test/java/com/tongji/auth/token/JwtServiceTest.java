package com.tongji.auth.token;

import com.tongji.auth.config.AuthConfiguration;
import com.tongji.auth.config.AuthProperties;
import com.tongji.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setIssuer("test-issuer");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        properties.getJwt().setPrivateKey(new ByteArrayResource(toPrivatePem((RSAPrivateKey) keyPair.getPrivate()).getBytes()));
        properties.getJwt().setPublicKey(new ByteArrayResource(toPublicPem((RSAPublicKey) keyPair.getPublic()).getBytes()));
        AuthConfiguration configuration = new AuthConfiguration(properties);
        JwtEncoder encoder = configuration.jwtEncoder();
        JwtDecoder decoder = configuration.jwtDecoder();
        jwtService = new JwtService(encoder, decoder, properties);
    }

    @Test
    void issueTokenPairAndDecode() {
        User user = User.builder()
                .id(123L)
                .nickname("tester")
                .build();

        TokenPair tokenPair = jwtService.issueTokenPair(user);

        assertThat(tokenPair.accessToken()).isNotBlank();
        assertThat(tokenPair.refreshToken()).isNotBlank();
        assertThat(tokenPair.refreshTokenId()).isNotBlank();

        Jwt accessJwt = jwtService.decode(tokenPair.accessToken());
        assertThat(jwtService.extractTokenType(accessJwt)).isEqualTo("access");
        assertThat(jwtService.extractUserId(accessJwt)).isEqualTo(123L);

        Jwt refreshJwt = jwtService.decode(tokenPair.refreshToken());
        assertThat(jwtService.extractTokenType(refreshJwt)).isEqualTo("refresh");
        assertThat(jwtService.extractUserId(refreshJwt)).isEqualTo(123L);
        assertThat(jwtService.extractTokenId(refreshJwt)).isEqualTo(tokenPair.refreshTokenId());
    }

    private static String toPrivatePem(RSAPrivateKey privateKey) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static String toPublicPem(RSAPublicKey publicKey) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }
}
