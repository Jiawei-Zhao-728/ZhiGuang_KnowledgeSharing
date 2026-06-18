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
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtDecoder accessTokenJwtDecoder;

    @BeforeEach
    void setUp() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setIssuer("test-issuer");
        KeyPair keyPair = generateRsaKeyPair();
        properties.getJwt().setPrivateKey(pemResource("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        properties.getJwt().setPublicKey(pemResource("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        AuthConfiguration configuration = new AuthConfiguration(properties);
        JwtEncoder encoder = configuration.jwtEncoder();
        JwtDecoder decoder = configuration.jwtDecoder();
        accessTokenJwtDecoder = configuration.accessTokenJwtDecoder();
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

    @Test
    void accessTokenDecoderRejectsRefreshTokens() {
        User user = User.builder()
                .id(123L)
                .nickname("tester")
                .build();

        TokenPair tokenPair = jwtService.issueTokenPair(user);

        Jwt accessJwt = accessTokenJwtDecoder.decode(tokenPair.accessToken());
        assertThat(jwtService.extractTokenType(accessJwt)).isEqualTo("access");

        assertThatThrownBy(() -> accessTokenJwtDecoder.decode(tokenPair.refreshToken()))
                .isInstanceOf(JwtException.class);

        Jwt refreshJwt = jwtService.decode(tokenPair.refreshToken());
        assertThat(jwtService.extractTokenType(refreshJwt)).isEqualTo("refresh");
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static ByteArrayResource pemResource(String type, byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded);
        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
    }
}
