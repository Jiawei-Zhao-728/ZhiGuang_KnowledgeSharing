package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CounterAggregationConsumerTest {

    @Test
    void flushAppliesAndDrainsDeltaInOneAtomicScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOperations);

        String entityType = "knowpost";
        String entityId = "42";
        String aggKey = CounterKeys.aggKey(entityType, entityId);
        String countKey = CounterKeys.sdsKey(entityType, entityId);
        String field = String.valueOf(CounterSchema.IDX_LIKE);

        when(redis.keys("agg:" + CounterSchema.SCHEMA_ID + ":*")).thenReturn(Set.of(aggKey));
        when(hashOperations.entries(aggKey)).thenReturn(Map.of(field, "3"));

        CounterAggregationConsumer consumer =
                new CounterAggregationConsumer(new ObjectMapper(), redis);

        consumer.flush();

        verify(redis, times(1)).execute(
                any(RedisScript.class),
                eq(List.of(countKey, aggKey)),
                eq(String.valueOf(CounterSchema.SCHEMA_LEN)),
                eq(String.valueOf(CounterSchema.FIELD_SIZE)),
                eq(field),
                eq(field));
        verify(redis, never()).delete(aggKey);
    }
}
