package com.tongji.relation.processor;

import com.tongji.counter.service.UserCounterService;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationEventProcessorTest {

    @Mock
    private RelationMapper mapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private UserCounterService userCounterService;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RelationEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new RelationEventProcessor(mapper, redis, userCounterService);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void followCreatedInvalidatesListCachesInsteadOfSeedingPartialZSet() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        processor.process(new RelationEvent("FollowCreated", 10L, 20L, 99L));

        verify(mapper).insertFollower(99L, 20L, 10L, 1);
        verify(userCounterService).incrementFollowings(10L, 1);
        verify(userCounterService).incrementFollowers(20L, 1);
        verify(redis).delete("uf:flws:10");
        verify(redis).delete("uf:fans:20");
        verify(redis, never()).opsForZSet();
        verify(redis, never()).expire(anyString(), any());
    }

    @Test
    void followCanceledInvalidatesListCachesInsteadOfZremOnPossiblyPartialSet() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        processor.process(new RelationEvent("FollowCanceled", 10L, 20L, null));

        verify(mapper).cancelFollower(20L, 10L);
        verify(userCounterService).incrementFollowings(10L, -1);
        verify(userCounterService).incrementFollowers(20L, -1);
        verify(redis).delete("uf:flws:10");
        verify(redis).delete("uf:fans:20");
        verify(redis, never()).opsForZSet();
    }

    @Test
    void duplicateEventDoesNotTouchListsOrCounters() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        processor.process(new RelationEvent("FollowCreated", 10L, 20L, 99L));

        verifyNoInteractions(mapper, userCounterService);
        verify(redis, never()).delete(anyString());
        verify(redis, never()).opsForZSet();
    }
}
