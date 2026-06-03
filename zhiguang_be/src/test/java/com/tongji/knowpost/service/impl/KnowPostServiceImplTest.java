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
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final String CACHE_KEY = "knowpost:detail:" + POST_ID + ":v1";

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

    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
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

    @Test
    void cachedDraftDetailIsNotReturnedToAnonymousUser() {
        detailCache.put(CACHE_KEY, cachedDetail("public", 111L));
        when(mapper.findDetailById(POST_ID)).thenReturn(row("draft", "public", 111L));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(counterService, never()).getCounts(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void cachedPublicPublishedDetailStillReturnsWithFreshCounts() {
        detailCache.put(CACHE_KEY, cachedDetail("public", 111L));
        when(mapper.findDetailById(POST_ID)).thenReturn(row("published", "public", 111L));
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(hotKey.ttlForPublic(anyInt(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(counterService.getCounts("knowpost", String.valueOf(POST_ID), List.of("like", "fav")))
                .thenReturn(Map.of("like", 7L, "fav", 3L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, null);

        assertThat(response.likeCount()).isEqualTo(7L);
        assertThat(response.favoriteCount()).isEqualTo(3L);
        assertThat(response.liked()).isFalse();
        assertThat(response.faved()).isFalse();
    }

    private KnowPostDetailResponse cachedDetail(String visible, long authorId) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "title",
                "description",
                "https://cdn.example/posts/42/content.md",
                List.of(),
                List.of(),
                String.valueOf(authorId),
                null,
                "author",
                null,
                1L,
                2L,
                null,
                null,
                false,
                visible,
                "image_text",
                Instant.now()
        );
    }

    private KnowPostDetailRow row(String status, String visible, long creatorId) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(creatorId);
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
