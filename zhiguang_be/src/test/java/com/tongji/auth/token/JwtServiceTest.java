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
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private AuthConfiguration configuration;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setIssuer("test-issuer");
        KeyPair keyPair = generateKeyPair();
        properties.getJwt().setPrivateKey(new ByteArrayResource(toPrivatePem(keyPair).getBytes(StandardCharsets.UTF_8)));
        properties.getJwt().setPublicKey(new ByteArrayResource(toPublicPem(keyPair).getBytes(StandardCharsets.UTF_8)));
        configuration = new AuthConfiguration(properties);
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

    @Test
    void accessTokenDecoderRejectsRefreshTokens() {
        User user = User.builder()
                .id(123L)
                .nickname("tester")
                .build();

        TokenPair tokenPair = jwtService.issueTokenPair(user);
        JwtDecoder accessTokenDecoder = configuration.accessTokenJwtDecoder();

        assertThat(accessTokenDecoder.decode(tokenPair.accessToken()).getSubject()).isEqualTo("123");
        assertThatThrownBy(() -> accessTokenDecoder.decode(tokenPair.refreshToken()))
                .isInstanceOf(JwtException.class);
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPrivatePem(KeyPair keyPair) {
        return toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String toPublicPem(KeyPair keyPair) {
        return toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String toPem(String label, byte[] derBytes) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(derBytes)
                + "\n-----END " + label + "-----\n";
    }
}
