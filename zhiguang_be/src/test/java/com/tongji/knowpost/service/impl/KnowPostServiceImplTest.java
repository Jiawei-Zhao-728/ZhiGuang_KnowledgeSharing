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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final String DETAIL_KEY = "knowpost:detail:" + POST_ID + ":v1";

    private KnowPostMapper mapper;
    private CounterService counterService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(KnowPostMapper.class);
        counterService = mock(CounterService.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        detailCache = Caffeine.newBuilder().build();

        when(redis.opsForValue()).thenReturn(valueOperations);

        service = new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                mock(OssProperties.class),
                counterService,
                mock(UserCounterService.class),
                redis,
                detailCache,
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }

    @Test
    void localCacheHitStillRejectsAnonymousViewerForPrivatePost() {
        detailCache.put(DETAIL_KEY, detailResponse("private"));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(counterService, never()).getCounts(anyString(), anyString(), any());
    }

    @Test
    void redisCacheHitStillRejectsAnonymousViewerForPrivatePost() throws Exception {
        KnowPostDetailResponse cached = detailResponse("private");
        when(valueOperations.get(DETAIL_KEY))
                .thenReturn(new ObjectMapper().writeValueAsString(cached))
                .thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void nonPublicOwnerReadDoesNotPopulateSharedDetailCaches() {
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private"));
        when(counterService.getCounts(eq("knowpost"), eq(String.valueOf(POST_ID)), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, 7L);

        assertThat(response.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private static KnowPostDetailResponse detailResponse(String visible) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "title",
                "description",
                "https://cdn.example.test/post.md",
                Collections.emptyList(),
                Collections.emptyList(),
                "7",
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
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(7L);
        row.setTitle("title");
        row.setDescription("description");
        row.setContentUrl("https://cdn.example.test/post.md");
        row.setAuthorNickname("author");
        row.setPublishTime(Instant.parse("2026-01-01T00:00:00Z"));
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }
}
