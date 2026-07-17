package com.tongji.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.service.impl.KnowPostServiceImpl;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.relation.service.impl.RelationServiceImpl;
import com.tongji.storage.config.OssProperties;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxAtomicityTest {

    @Test
    void knowpostWriteDoesNotReportSuccessWhenOutboxInsertFails() {
        KnowPostMapper knowPostMapper = mock(KnowPostMapper.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        UserCounterService userCounterService = mock(UserCounterService.class);

        when(knowPostMapper.publish(42L, 7L)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(100L);
        when(outboxMapper.insert(anyLong(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("outbox unavailable"));

        KnowPostServiceImpl service = new KnowPostServiceImpl(
                knowPostMapper,
                idGenerator,
                new ObjectMapper(),
                new OssProperties(),
                mock(CounterService.class),
                userCounterService,
                mock(StringRedisTemplate.class),
                Caffeine.newBuilder().build(),
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                outboxMapper
        );

        assertThatThrownBy(() -> service.publish(7L, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        verify(knowPostMapper).publish(42L, 7L);
        verify(outboxMapper).insert(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void relationWriteDoesNotReportSuccessWhenOutboxInsertFails() {
        RelationMapper relationMapper = mock(RelationMapper.class);
        OutboxMapper outboxMapper = mock(OutboxMapper.class);

        when(relationMapper.cancelFollowing(7L, 9L)).thenReturn(1);
        when(outboxMapper.insert(anyLong(), anyString(), isNull(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("outbox unavailable"));

        RelationServiceImpl service = new RelationServiceImpl(
                relationMapper,
                outboxMapper,
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(UserMapper.class)
        );

        assertThatThrownBy(() -> service.unfollow(7L, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox unavailable");

        verify(relationMapper).cancelFollowing(7L, 9L);
        verify(outboxMapper).insert(anyLong(), anyString(), isNull(), anyString(), anyString());
    }
}
