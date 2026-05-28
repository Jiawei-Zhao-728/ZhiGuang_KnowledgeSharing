package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    @Test
    void nonPublicDetailReturnedToOwnerIsNotStoredInSharedCache() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        CounterService counterService = mock(CounterService.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("knowpost:detail:1:v2")).thenReturn(null);
        when(mapper.findDetailById(1L)).thenReturn(privatePost());
        when(counterService.getCounts(eq("knowpost"), eq("1"), anyList()))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostServiceImpl service = newService(mapper, redis, counterService);

        assertEquals("1", service.getDetail(1L, 7L).id());

        verify(valueOps, never()).set(eq("knowpost:detail:1:v2"), anyString(), any(java.time.Duration.class));
    }

    @Test
    void nonOwnerCannotReadNonPublicDetailAfterOwnerRequest() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        CounterService counterService = mock(CounterService.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("knowpost:detail:1:v2")).thenReturn(null);
        when(mapper.findDetailById(1L)).thenReturn(privatePost());
        when(counterService.getCounts(eq("knowpost"), eq("1"), anyList()))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostServiceImpl service = newService(mapper, redis, counterService);
        service.getDetail(1L, 7L);

        assertThrows(BusinessException.class, () -> service.getDetail(1L, null));
    }

    @Test
    void confirmContentRejectsObjectKeyFromAnotherPost() {
        KnowPostServiceImpl service = newService(
                mock(KnowPostMapper.class),
                mock(StringRedisTemplate.class),
                mock(CounterService.class)
        );

        assertThrows(BusinessException.class,
                () -> service.confirmContent(7L, 1L, "posts/2/content.md", "etag", 1L, "sha"));
    }

    private KnowPostServiceImpl newService(KnowPostMapper mapper, StringRedisTemplate redis, CounterService counterService) {
        OssProperties ossProperties = mock(OssProperties.class);

        return new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                ossProperties,
                counterService,
                mock(UserCounterService.class),
                redis,
                Caffeine.newBuilder().build(),
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }

    private KnowPostDetailRow privatePost() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(1L);
        row.setCreatorId(7L);
        row.setTitle("private title");
        row.setDescription("private description");
        row.setContentUrl("https://cdn.example.test/posts/1/content.md");
        row.setStatus("published");
        row.setVisible("private");
        row.setType("image_text");
        row.setPublishTime(Instant.now());
        return row;
    }
}
