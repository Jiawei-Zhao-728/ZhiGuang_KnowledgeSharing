package com.tongji.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
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
    void consumesWhitelistEntryWithAtomicGetAndDelete() {
        when(valueOperations.getAndDelete("auth:rt:42:token-id")).thenReturn("1");

        assertThat(store.consumeToken(42L, "token-id")).isTrue();

        verify(valueOperations).getAndDelete("auth:rt:42:token-id");
    }

    @Test
    void rejectsTokenWhenAtomicConsumeFindsNoEntry() {
        when(valueOperations.getAndDelete("auth:rt:42:token-id")).thenReturn(null);

        assertThat(store.consumeToken(42L, "token-id")).isFalse();
    }
}
