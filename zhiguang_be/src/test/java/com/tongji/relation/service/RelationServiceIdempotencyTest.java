package com.tongji.relation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.relation.service.impl.RelationServiceImpl;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Repeated follow/unfollow must not emit extra outbox events; otherwise
 * RelationEventProcessor permanently inflates or deflates social counters.
 */
@ExtendWith(MockitoExtension.class)
class RelationServiceIdempotencyTest {

    @Mock
    private RelationMapper relationMapper;
    @Mock
    private OutboxMapper outboxMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private UserMapper userMapper;

    private RelationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RelationServiceImpl(relationMapper, outboxMapper, redis, new ObjectMapper(), userMapper);
    }

    private void allowFollowRateLimit() {
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void duplicateFollowWhileAlreadyActiveDoesNotEmitFollowCreated() {
        allowFollowRateLimit();
        when(relationMapper.insertFollowing(anyLong(), eq(10L), eq(20L), eq(1))).thenReturn(0);
        when(relationMapper.existsFollowing(10L, 20L)).thenReturn(1);

        assertTrue(service.follow(10L, 20L));

        verify(outboxMapper, never()).insert(anyLong(), anyString(), any(), anyString(), anyString());
        verify(relationMapper, times(1)).insertFollowing(anyLong(), eq(10L), eq(20L), eq(1));
    }

    @Test
    void firstFollowEmitsFollowCreated() {
        allowFollowRateLimit();
        when(relationMapper.insertFollowing(anyLong(), eq(10L), eq(20L), eq(1))).thenReturn(1);

        assertTrue(service.follow(10L, 20L));

        verify(outboxMapper, times(1)).insert(
                anyLong(),
                eq("following"),
                anyLong(),
                eq("FollowCreated"),
                anyString()
        );
    }

    @Test
    void unfollowWhenAlreadyInactiveDoesNotEmitFollowCanceled() {
        when(relationMapper.cancelFollowing(10L, 20L)).thenReturn(0);

        assertFalse(service.unfollow(10L, 20L));

        verify(outboxMapper, never()).insert(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void activeUnfollowEmitsFollowCanceled() {
        when(relationMapper.cancelFollowing(10L, 20L)).thenReturn(1);

        assertTrue(service.unfollow(10L, 20L));

        verify(outboxMapper, times(1)).insert(
                anyLong(),
                eq("following"),
                isNull(),
                eq("FollowCanceled"),
                anyString()
        );
    }
}
