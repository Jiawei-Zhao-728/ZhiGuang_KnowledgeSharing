package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CounterAggregationConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StringRedisTemplate redis;
    private HashOperations<String, Object, Object> hashOps;
    private CounterAggregationConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        consumer = new CounterAggregationConsumer(objectMapper, redis);
    }

    @Test
    void ofAssignsEventId() {
        CounterEvent event = CounterEvent.of("knowpost", "42", "like", CounterSchema.IDX_LIKE, 7L, 1);
        assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    void redeliveredEventIsAppliedOnceThenAcked() throws Exception {
        CounterEvent event = CounterEvent.of("knowpost", "42", "like", CounterSchema.IDX_LIKE, 7L, 1);
        String payload = objectMapper.writeValueAsString(event);
        Acknowledgment ack = mock(Acknowledgment.class);

        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any())).thenReturn(1L, 0L);

        consumer.onMessage(payload, ack);
        consumer.onMessage(payload, ack);

        String dedupKey = CounterKeys.aggDedupKey(event.getEventId());
        String aggKey = CounterKeys.aggKey("knowpost", "42");
        verify(redis, times(2)).execute(
                any(DefaultRedisScript.class),
                eq(List.of(dedupKey, aggKey)),
                eq(String.valueOf(CounterSchema.IDX_LIKE)),
                eq("1"),
                eq(String.valueOf(CounterAggregationConsumer.DEDUP_TTL_SECONDS))
        );
        verify(ack, times(2)).acknowledge();
        verify(hashOps, never()).increment(anyString(), any(), anyLong());
    }

    @Test
    void legacyEventWithoutIdStillIncrementsHash() throws Exception {
        CounterEvent event = new CounterEvent();
        event.setEntityType("knowpost");
        event.setEntityId("42");
        event.setMetric("like");
        event.setIdx(CounterSchema.IDX_LIKE);
        event.setUserId(7L);
        event.setDelta(1);
        String payload = objectMapper.writeValueAsString(event);
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onMessage(payload, ack);

        verify(hashOps).increment(CounterKeys.aggKey("knowpost", "42"), String.valueOf(CounterSchema.IDX_LIKE), 1L);
        verify(ack).acknowledge();
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any());
    }
}
