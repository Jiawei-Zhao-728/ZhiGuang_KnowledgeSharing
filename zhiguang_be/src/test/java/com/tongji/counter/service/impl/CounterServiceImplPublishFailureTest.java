package com.tongji.counter.service.impl;

import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import com.tongji.counter.event.CounterEventProducer;
import com.tongji.counter.schema.BitmapShard;
import com.tongji.counter.schema.CounterKeys;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CounterServiceImplPublishFailureTest {

    @Test
    void likeCompensatesBitmapWhenKafkaPublishFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CounterEventProducer eventProducer = mock(CounterEventProducer.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        RedissonClient redisson = mock(RedissonClient.class);

        long userId = 99L;
        String entityType = "knowpost";
        String entityId = "42";
        String bmKey = CounterKeys.bitmapKey("like", entityType, entityId, BitmapShard.chunkOf(userId));
        String bit = String.valueOf(BitmapShard.bitOf(userId));

        when(redis.execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("add")))
                .thenReturn(1L);
        when(redis.execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("remove")))
                .thenReturn(1L);
        doThrow(new IllegalStateException("broker down"))
                .when(eventProducer).publish(any());

        CounterServiceImpl service = new CounterServiceImpl(redis, eventProducer, eventPublisher, redisson);

        assertThatThrownBy(() -> service.like(entityType, entityId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        verify(redis, times(1)).execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("add"));
        verify(redis, times(1)).execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("remove"));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unlikeCompensatesBitmapWhenKafkaPublishFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CounterEventProducer eventProducer = mock(CounterEventProducer.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        RedissonClient redisson = mock(RedissonClient.class);

        long userId = 99L;
        String entityType = "knowpost";
        String entityId = "42";
        String bmKey = CounterKeys.bitmapKey("like", entityType, entityId, BitmapShard.chunkOf(userId));
        String bit = String.valueOf(BitmapShard.bitOf(userId));

        when(redis.execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("remove")))
                .thenReturn(1L);
        when(redis.execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("add")))
                .thenReturn(1L);
        doThrow(new IllegalStateException("broker down"))
                .when(eventProducer).publish(any());

        CounterServiceImpl service = new CounterServiceImpl(redis, eventProducer, eventPublisher, redisson);

        assertThatThrownBy(() -> service.unlike(entityType, entityId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        verify(redis, times(1)).execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("remove"));
        verify(redis, times(1)).execute(any(RedisScript.class), eq(List.of(bmKey)), eq(bit), eq("add"));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
