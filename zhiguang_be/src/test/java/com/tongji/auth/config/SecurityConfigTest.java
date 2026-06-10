package com.tongji.auth.config;

import com.tongji.auth.token.AccessTokenJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedController.class)
@Import({SecurityConfig.class, AccessTokenJwtAuthenticationConverter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void protectedEndpointAcceptsAccessToken() throws Exception {
        when(jwtDecoder.decode("access-token")).thenReturn(jwtWithTokenType("access"));

        mockMvc.perform(get("/secure")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void protectedEndpointRejectsRefreshToken() throws Exception {
        when(jwtDecoder.decode("refresh-token")).thenReturn(jwtWithTokenType("refresh"));

        mockMvc.perform(get("/secure")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer refresh-token"))
                .andExpect(status().isUnauthorized());
    }

    private static Jwt jwtWithTokenType(String tokenType) {
        return new Jwt(
                tokenType + "-token",
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-10T00:15:00Z"),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "123",
                        "uid", 123L,
                        "token_type", tokenType
                )
        );
    }

    @RestController
    static class ProtectedController {

        @GetMapping("/secure")
        String secure() {
            return "ok";
        }
    }
}
