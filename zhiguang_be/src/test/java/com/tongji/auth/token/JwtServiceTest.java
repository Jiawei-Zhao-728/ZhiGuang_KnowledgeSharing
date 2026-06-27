package com.tongji.auth.token;

import com.tongji.auth.config.AuthConfiguration;
import com.tongji.auth.config.AuthProperties;
import com.tongji.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @TempDir
    private Path tempDir;

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path privateKey = tempDir.resolve("private.pem");
        Path publicKey = tempDir.resolve("public.pem");
        Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), StandardCharsets.UTF_8);
        Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.UTF_8);

        AuthProperties properties = new AuthProperties();
        properties.getJwt().setIssuer("test-issuer");
        properties.getJwt().setPrivateKey(new FileSystemResource(privateKey));
        properties.getJwt().setPublicKey(new FileSystemResource(publicKey));
        AuthConfiguration configuration = new AuthConfiguration(properties);
        JwtEncoder encoder = configuration.jwtEncoder();
        JwtDecoder decoder = configuration.jwtDecoder();
        jwtService = new JwtService(encoder, decoder, properties);
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String pem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
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
}
