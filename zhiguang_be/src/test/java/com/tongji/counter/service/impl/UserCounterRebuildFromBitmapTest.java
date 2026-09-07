package com.tongji.counter.service.impl;

import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.relation.mapper.RelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCounterRebuildFromBitmapTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private KnowPostMapper knowPostMapper;
    @Mock
    private CounterService counterService;
    @Mock
    private RelationMapper relationMapper;
    @Mock
    private RedisConnection connection;
    @Mock
    private RedisStringCommands stringCommands;

    private UserCounterServiceImpl service;
    private final AtomicReference<byte[]> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new UserCounterServiceImpl(redis, knowPostMapper, counterService, relationMapper);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.get(any())).thenReturn(null);
        when(stringCommands.set(any(byte[].class), any(byte[].class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return Boolean.TRUE;
        });
        when(redis.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
    }

    @Test
    void rebuildAllCountersUsesGetCountsNotBatchZeros() {
        when(relationMapper.countFollowingActive(1L)).thenReturn(2);
        when(relationMapper.countFollowerActive(1L)).thenReturn(3);
        when(knowPostMapper.listMyPublishedIds(1L)).thenReturn(List.of(10L, 11L));
        when(counterService.getCounts("knowpost", "10", List.of("like", "fav")))
                .thenReturn(Map.of("like", 40L, "fav", 5L));
        when(counterService.getCounts("knowpost", "11", List.of("like", "fav")))
                .thenReturn(Map.of("like", 2L, "fav", 2L));

        service.rebuildAllCounters(1L);

        verify(counterService, never()).getCountsBatch(any(), any(), any());
        byte[] buf = stored.get();
        assertEquals(20, buf.length);
        assertEquals(2L, read32be(buf, 0));
        assertEquals(3L, read32be(buf, 4));
        assertEquals(2L, read32be(buf, 8));
        assertEquals(42L, read32be(buf, 12));
        assertEquals(7L, read32be(buf, 16));
    }

    private static long read32be(byte[] buf, int off) {
        long n = 0L;
        for (int i = 0; i < 4; i++) {
            n = (n << 8) | (buf[off + i] & 0xFFL);
        }
        return n;
    }
}
