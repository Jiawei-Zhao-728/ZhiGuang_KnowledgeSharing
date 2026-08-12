package com.tongji.counter.service.impl;

import com.tongji.counter.schema.UserCounterKeys;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.relation.mapper.RelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ensures user-counter rebuild cannot blind-SET over a concurrent increment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCounterRebuildRaceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock KnowPostMapper knowPostMapper;
    @Mock CounterService counterService;
    @Mock RelationMapper relationMapper;

    UserCounterServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new UserCounterServiceImpl(redis, knowPostMapper, counterService, relationMapper);
    }

    @Test
    void incrScriptBumpsGenerationKey() {
        assertTrue(UserCounterServiceImpl.INCR_FIELD_LUA.contains("INCR"));
        assertTrue(UserCounterServiceImpl.INCR_FIELD_LUA.contains("KEYS[2]"));
        assertTrue(UserCounterServiceImpl.SET_IF_GEN_LUA.contains("expected"));
    }

    @Test
    void incrementFollowersPassesGenerationKey() {
        long userId = 42L;
        doAnswer(inv -> 1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));

        service.incrementFollowers(userId, 1);

        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keysCaptor.capture(), any(Object[].class));
        List keys = keysCaptor.getValue();
        assertEquals(UserCounterKeys.sdsKey(userId), keys.get(0));
        assertEquals(UserCounterKeys.genKey(userId), keys.get(1));
    }

    @Test
    void rebuildRetriesWhenGenerationChangesDuringSnapshot() {
        long userId = 7L;
        when(valueOps.get(UserCounterKeys.genKey(userId))).thenReturn("3", "4");
        when(knowPostMapper.listMyPublishedIds(userId)).thenReturn(List.of());
        when(relationMapper.countFollowingActive(userId)).thenReturn(10, 10);
        when(relationMapper.countFollowerActive(userId)).thenReturn(20, 21);

        AtomicInteger casCalls = new AtomicInteger();
        doAnswer(inv -> {
            RedisScript<?> script = inv.getArgument(0);
            // Only intercept SET_IF_GEN (returns Long); RedisCallback path uses different overload
            if (script instanceof DefaultRedisScript<?> drs
                    && Long.class.equals(drs.getResultType())
                    && UserCounterServiceImpl.SET_IF_GEN_LUA.equals(drs.getScriptAsString())) {
                int n = casCalls.incrementAndGet();
                // First CAS misses (concurrent incr bumped gen); second succeeds
                return n == 1 ? 0L : 1L;
            }
            return null;
        }).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));

        // rebuild also uses RedisCallback GET for existing SDS — return null (missing)
        when(redis.execute(any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn(null);

        service.rebuildAllCounters(userId);

        assertEquals(2, casCalls.get(), "CAS should retry once after a generation conflict");
        verify(relationMapper, times(2)).countFollowerActive(userId);
        verify(relationMapper, times(2)).countFollowingActive(userId);
    }

    @Test
    void rebuildDoesNotApplyStaleSnapshotAfterSingleCasHit() {
        long userId = 9L;
        when(valueOps.get(UserCounterKeys.genKey(userId))).thenReturn("1");
        when(knowPostMapper.listMyPublishedIds(userId)).thenReturn(List.of());
        when(relationMapper.countFollowingActive(userId)).thenReturn(1);
        when(relationMapper.countFollowerActive(userId)).thenReturn(2);
        when(redis.execute(any(org.springframework.data.redis.core.RedisCallback.class))).thenReturn(null);

        AtomicInteger casCalls = new AtomicInteger();
        doAnswer(inv -> {
            RedisScript<?> script = inv.getArgument(0);
            if (script instanceof DefaultRedisScript<?> drs
                    && Long.class.equals(drs.getResultType())
                    && UserCounterServiceImpl.SET_IF_GEN_LUA.equals(drs.getScriptAsString())) {
                casCalls.incrementAndGet();
                List keys = inv.getArgument(1);
                Object[] args = inv.getArguments();
                // ARGV expected gen is first script arg after keys list
                // execute(script, keys, expectedGen, payload)
                assertEquals(UserCounterKeys.sdsKey(userId), keys.get(0));
                assertEquals(UserCounterKeys.genKey(userId), keys.get(1));
                assertEquals("1", inv.getArgument(2));
                return 1L;
            }
            return null;
        }).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));

        service.rebuildAllCounters(userId);

        assertEquals(1, casCalls.get());
        verify(relationMapper, times(1)).countFollowerActive(userId);
    }
}
