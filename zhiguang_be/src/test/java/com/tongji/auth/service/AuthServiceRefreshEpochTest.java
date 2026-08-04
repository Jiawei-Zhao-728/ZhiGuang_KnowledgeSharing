package com.tongji.auth.service;

import com.tongji.auth.api.dto.TokenRefreshRequest;
import com.tongji.auth.api.dto.TokenResponse;
import com.tongji.auth.audit.LoginLogService;
import com.tongji.auth.config.AuthProperties;
import com.tongji.auth.token.JwtService;
import com.tongji.auth.token.RefreshTokenStore;
import com.tongji.auth.token.TokenPair;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import com.tongji.user.domain.User;
import com.tongji.user.service.UserService;
import com.tongji.auth.verification.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshEpochTest {

    @Mock private UserService userService;
    @Mock private VerificationService verificationService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private LoginLogService loginLogService;
    @Mock private AuthProperties authProperties;
    @Mock private Jwt refreshJwt;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userService,
                verificationService,
                passwordEncoder,
                jwtService,
                refreshTokenStore,
                loginLogService,
                authProperties
        );
    }

    @Test
    void refreshConsumesTokenAndStoresSuccessorWithValidatedEpoch() {
        when(jwtService.decode("refresh-token")).thenReturn(refreshJwt);
        when(jwtService.extractTokenType(refreshJwt)).thenReturn("refresh");
        when(jwtService.extractUserId(refreshJwt)).thenReturn(9L);
        when(jwtService.extractTokenId(refreshJwt)).thenReturn("old-jti");
        when(refreshTokenStore.consumeToken(9L, "old-jti")).thenReturn(OptionalLong.of(4L));

        User user = User.builder().id(9L).nickname("u").build();
        when(userService.findById(9L)).thenReturn(Optional.of(user));

        Instant accessExp = Instant.now().plusSeconds(900);
        Instant refreshExp = Instant.now().plus(Duration.ofDays(7));
        TokenPair pair = new TokenPair("access", accessExp, "new-refresh", refreshExp, "new-jti");
        when(jwtService.issueTokenPair(user)).thenReturn(pair);

        TokenResponse response = authService.refresh(new TokenRefreshRequest("refresh-token"));

        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenStore).consumeToken(9L, "old-jti");
        verify(refreshTokenStore).storeToken(eq(9L), eq("new-jti"), any(Duration.class), eq(4L));
        verify(refreshTokenStore, never()).storeToken(eq(9L), eq("new-jti"), any(Duration.class));
        verify(refreshTokenStore, never()).revokeToken(anyLong(), any());
    }

    @Test
    void refreshRejectsWhenConsumeFailsAfterRevokeAll() {
        when(jwtService.decode("refresh-token")).thenReturn(refreshJwt);
        when(jwtService.extractTokenType(refreshJwt)).thenReturn("refresh");
        when(jwtService.extractUserId(refreshJwt)).thenReturn(9L);
        when(jwtService.extractTokenId(refreshJwt)).thenReturn("old-jti");
        when(refreshTokenStore.consumeToken(9L, "old-jti")).thenReturn(OptionalLong.empty());

        assertThatThrownBy(() -> authService.refresh(new TokenRefreshRequest("refresh-token")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));

        verify(refreshTokenStore, never()).storeToken(anyLong(), any(), any());
        verify(refreshTokenStore, never()).storeToken(anyLong(), any(), any(), anyLong());
    }
}
