package com.tongji.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisRefreshTokenStore(redisTemplate);
    }

    @Test
    void revokeAllIncrementsEpochInsteadOfKeysScanDelete() {
        store.revokeAll(42L);

        verify(valueOperations).increment("auth:rt:epoch:42");
        verify(redisTemplate, never()).keys(anyString());
        verify(redisTemplate, never()).delete(anyCollection());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void storeTokenWritesCurrentEpochValue() {
        when(valueOperations.get("auth:rt:epoch:7")).thenReturn("3");

        store.storeToken(7L, "jti-1", Duration.ofMinutes(5));

        verify(valueOperations).set("auth:rt:7:jti-1", "3", Duration.ofMinutes(5));
    }

    @Test
    void storeTokenWithExplicitEpochPreservesValidatedGeneration() {
        store.storeToken(7L, "new-jti", Duration.ofMinutes(5), 2L);

        verify(valueOperations).set("auth:rt:7:new-jti", "2", Duration.ofMinutes(5));
    }

    @Test
    void isTokenValidRejectsStaleEpochAfterRevokeAll() {
        when(valueOperations.get("auth:rt:7:jti-1")).thenReturn("2");
        when(valueOperations.get("auth:rt:epoch:7")).thenReturn("3");

        assertThat(store.isTokenValid(7L, "jti-1")).isFalse();
    }

    @Test
    void isTokenValidAcceptsCurrentEpoch() {
        when(valueOperations.get("auth:rt:7:jti-1")).thenReturn("3");
        when(valueOperations.get("auth:rt:epoch:7")).thenReturn("3");

        assertThat(store.isTokenValid(7L, "jti-1")).isTrue();
    }

    @Test
    void consumeTokenUsesAtomicScriptAgainstTokenAndEpochKeys() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(3L);

        OptionalLong epoch = store.consumeToken(7L, "jti-1");

        assertThat(epoch).isPresent().hasValue(3L);

        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactly("auth:rt:7:jti-1", "auth:rt:epoch:7");
    }

    @Test
    void consumeTokenReturnsEmptyWhenScriptRejects() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(null);

        assertThat(store.consumeToken(7L, "missing")).isEmpty();
    }
}
