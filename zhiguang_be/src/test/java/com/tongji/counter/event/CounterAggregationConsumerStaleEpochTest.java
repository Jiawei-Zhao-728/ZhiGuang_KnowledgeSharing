package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CounterAggregationConsumerStaleEpochTest {

    @Test
    void isStaleEpochWhenEventEpochBehindRebuildEpoch() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(CounterKeys.epochKey("knowpost", "42"))).thenReturn("3");

        CounterAggregationConsumer consumer = new CounterAggregationConsumer(new ObjectMapper(), redis);
        CounterEvent stale = CounterEvent.of("knowpost", "42", "like", CounterSchema.IDX_LIKE, 7L, 1, 2L);
        CounterEvent current = CounterEvent.of("knowpost", "42", "like", CounterSchema.IDX_LIKE, 7L, 1, 3L);

        assertTrue(consumer.isStaleEpoch(stale));
        assertFalse(consumer.isStaleEpoch(current));
    }

    @Test
    void onMessageSkipsHashIncrementForStaleEpochAndAcks() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(values.get(CounterKeys.epochKey("knowpost", "42"))).thenReturn("5");

        CounterAggregationConsumer consumer = new CounterAggregationConsumer(new ObjectMapper(), redis);
        Acknowledgment ack = mock(Acknowledgment.class);

        CounterEvent stale = CounterEvent.of("knowpost", "42", "like", CounterSchema.IDX_LIKE, 9L, 1, 4L);
        String json = new ObjectMapper().writeValueAsString(stale);

        consumer.onMessage(json, ack);

        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
        verify(ack).acknowledge();
    }

    @Test
    void onMessagePersistsDeltaWhenEpochIsCurrent() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(values.get(CounterKeys.epochKey("knowpost", "42"))).thenReturn("5");

        CounterAggregationConsumer consumer = new CounterAggregationConsumer(new ObjectMapper(), redis);
        Acknowledgment ack = mock(Acknowledgment.class);

        CounterEvent fresh = CounterEvent.of("knowpost", "42", "like", CounterSchema.IDX_LIKE, 9L, 1, 5L);
        String json = new ObjectMapper().writeValueAsString(fresh);
        String aggKey = CounterKeys.aggKey("knowpost", "42");

        consumer.onMessage(json, ack);

        verify(hashOperations).increment(eq(aggKey), eq(String.valueOf(CounterSchema.IDX_LIKE)), eq(1L));
        verify(ack).acknowledge();
    }
}
