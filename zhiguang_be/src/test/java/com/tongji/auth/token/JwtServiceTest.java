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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setIssuer("test-issuer");
        properties.getJwt().setPrivateKey(resource(privatePem(keyPair)));
        properties.getJwt().setPublicKey(resource(publicPem(keyPair)));
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

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static ByteArrayResource resource(String pem) {
        return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
    }

    private static String privatePem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String publicPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }
}
