package com.tongji.counter.service.impl;

import com.tongji.counter.event.CounterEventProducer;
import com.tongji.counter.schema.CounterSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterServiceGetCountsBatchTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private CounterEventProducer eventProducer;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RedissonClient redisson;

    private CounterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CounterServiceImpl(redis, eventProducer, eventPublisher, redisson);
    }

    @Test
    void getCountsBatchReadsBinarySdsWithoutStringCodec() {
        byte[] sds = new byte[CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE];
        writeInt32BE(sds, CounterSchema.IDX_LIKE * CounterSchema.FIELD_SIZE, 42);
        writeInt32BE(sds, CounterSchema.IDX_FAV * CounterSchema.FIELD_SIZE, 7);

        when(redis.executePipelined(any(RedisCallback.class), any(RedisSerializer.class)))
                .thenReturn(List.of(sds));

        Map<String, Map<String, Long>> counts = service.getCountsBatch(
                "knowpost", List.of("10"), List.of("like", "fav"));

        assertEquals(42L, counts.get("10").get("like"));
        assertEquals(7L, counts.get("10").get("fav"));

        ArgumentCaptor<RedisSerializer<?>> serializerCaptor = ArgumentCaptor.forClass(RedisSerializer.class);
        verify(redis).executePipelined(any(RedisCallback.class), serializerCaptor.capture());
        byte[] roundTrip = new byte[] {0x00, (byte) 0xFF, 0x00, 0x01};
        assertArrayEquals(roundTrip, (byte[]) serializerCaptor.getValue().deserialize(roundTrip));
    }

    @Test
    void getCountsBatchDoesNotTreatUtf8StringsAsSdsBytes() {
        when(redis.executePipelined(any(RedisCallback.class), any(RedisSerializer.class)))
                .thenReturn(List.of("not-binary-sds"));

        Map<String, Map<String, Long>> counts = service.getCountsBatch(
                "knowpost", List.of("10"), List.of("like", "fav"));

        assertEquals(0L, counts.get("10").get("like"));
        assertEquals(0L, counts.get("10").get("fav"));
    }

    private static void writeInt32BE(byte[] buf, int off, long val) {
        buf[off] = (byte) ((val >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((val >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((val >>> 8) & 0xFF);
        buf[off + 3] = (byte) (val & 0xFF);
    }
}
