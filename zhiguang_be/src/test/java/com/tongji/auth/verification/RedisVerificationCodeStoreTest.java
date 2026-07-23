package com.tongji.auth.verification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RedisVerificationCodeStoreTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void verifyUsesOneAtomicRedisScriptAndConsumesMatchingCode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisVerificationCodeStore store = new RedisVerificationCodeStore(redis);
        String key = "auth:code:LOGIN:user@example.com";

        when(redis.execute(
                org.mockito.ArgumentMatchers.<RedisScript<String>>any(),
                eq(List.of(key)),
                eq("123456")
        )).thenReturn("SUCCESS:2:5");

        VerificationCheckResult result = store.verify("LOGIN", "user@example.com", "123456");

        assertThat(result.status()).isEqualTo(VerificationCodeStatus.SUCCESS);
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.maxAttempts()).isEqualTo(5);

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        verify(redis).execute(script.capture(), eq(List.of(key)), eq("123456"));
        assertThat(script.getValue().getResultType()).isEqualTo(String.class);
        verifyNoMoreInteractions(redis);
    }

    @Test
    void verifyReturnsAtomicAttemptLimitResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisVerificationCodeStore store = new RedisVerificationCodeStore(redis);
        String key = "auth:code:RESET_PASSWORD:13800138000";

        when(redis.execute(
                org.mockito.ArgumentMatchers.<RedisScript<String>>any(),
                eq(List.of(key)),
                eq("000000")
        )).thenReturn("TOO_MANY_ATTEMPTS:5:5");

        VerificationCheckResult result = store.verify("RESET_PASSWORD", "13800138000", "000000");

        assertThat(result.status()).isEqualTo(VerificationCodeStatus.TOO_MANY_ATTEMPTS);
        assertThat(result.attempts()).isEqualTo(5);
        assertThat(result.maxAttempts()).isEqualTo(5);
        verify(redis).execute(
                org.mockito.ArgumentMatchers.<RedisScript<String>>any(),
                eq(List.of(key)),
                eq("000000")
        );
        verifyNoMoreInteractions(redis);
    }
}
