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
        when(counterService.getCounts(eq("knowpost"), anyString(), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        service = new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper().findAndRegisterModules(),
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
    void cachedDraftVisiblePublicIsNotServedToAnonymousUsers() {
        detailCache.put(DETAIL_KEY, detailResponse("public", null));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    @Test
    void ownerOnlyDetailReadsDoNotPopulateSharedCache() {
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public", null));

        KnowPostDetailResponse response = service.getDetail(POST_ID, 1L);

        assertThat(response.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
        verify(valueOperations, never()).set(eq(DETAIL_KEY), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    @Test
    void publishedPublicDetailReadsPopulateSharedCache() {
        Instant publishTime = Instant.parse("2026-05-21T11:00:00Z");
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "public", publishTime));

        KnowPostDetailResponse response = service.getDetail(POST_ID, null);

        assertThat(response.publishTime()).isEqualTo(publishTime);
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNotNull();
        verify(valueOperations).set(eq(DETAIL_KEY), anyString(), any(Duration.class));
    }

    private KnowPostDetailRow detailRow(String status, String visible, Instant publishTime) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(1L);
        row.setTitle("private draft");
        row.setDescription("sensitive description");
        row.setContentUrl("https://example.com/private.md");
        row.setImgUrls("[]");
        row.setTags("[]");
        row.setAuthorAvatar("avatar.png");
        row.setAuthorNickname("author");
        row.setAuthorTagJson("[]");
        row.setPublishTime(publishTime);
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }

    private KnowPostDetailResponse detailResponse(String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "private draft",
                "sensitive description",
                "https://example.com/private.md",
                List.of(),
                List.of(),
                "1",
                "avatar.png",
                "author",
                "[]",
                0L,
                0L,
                null,
                null,
                false,
                visible,
                "image_text",
                publishTime
        );
    }
}
