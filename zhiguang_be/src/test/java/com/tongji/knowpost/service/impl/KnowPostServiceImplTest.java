package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    @Mock
    private KnowPostMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private OssProperties ossProperties;
    @Mock
    private CounterService counterService;
    @Mock
    private UserCounterService userCounterService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HotKeyDetector hotKey;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    @Test
    void privateCachedDetailCannotBeReadByAnotherUser() {
        Cache<String, KnowPostDetailResponse> detailCache = Caffeine.newBuilder().build();
        long postId = 42L;
        detailCache.put("knowpost:detail:" + postId + ":v1", detailResponse("private", "7"));
        KnowPostServiceImpl service = newService(detailCache);

        assertThatThrownBy(() -> service.getDetail(postId, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
        verifyNoInteractions(counterService, redis, hotKey);
    }

    @Test
    void visibilityChangeEmitsSearchOutboxRefresh() {
        Cache<String, KnowPostDetailResponse> detailCache = Caffeine.newBuilder().build();
        KnowPostServiceImpl service = newService(detailCache);
        when(mapper.updateVisibility(42L, 7L, "private")).thenReturn(1);
        when(idGen.nextId()).thenReturn(99L);

        service.updateVisibility(7L, 42L, "private");

        verify(outboxMapper).insert(
                eq(99L),
                eq("knowpost"),
                eq(42L),
                eq("KnowPostVisibilityUpdated"),
                contains("\"op\":\"upsert\"")
        );
    }

    private KnowPostServiceImpl newService(Cache<String, KnowPostDetailResponse> detailCache) {
        return new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                ossProperties,
                counterService,
                userCounterService,
                redis,
                detailCache,
                hotKey,
                ragIndexService,
                outboxMapper
        );
    }

    private KnowPostDetailResponse detailResponse(String visible, String authorId) {
        return new KnowPostDetailResponse(
                "42",
                "private post",
                "description",
                "https://cdn.example.test/posts/42/content.md",
                List.of(),
                List.of(),
                authorId,
                null,
                "author",
                null,
                0L,
                0L,
                null,
                null,
                false,
                visible,
                "image_text",
                Instant.now()
        );
    }
}
