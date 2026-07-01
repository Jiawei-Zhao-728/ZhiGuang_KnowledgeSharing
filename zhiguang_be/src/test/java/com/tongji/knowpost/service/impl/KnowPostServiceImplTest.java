package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.config.CacheProperties;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.knowpost.service.cache.KnowPostDetailCacheEntry;
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

    private static final String DETAIL_KEY = "knowpost:detail:42:v1";

    private KnowPostMapper mapper;
    private CounterService counterService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private Cache<String, KnowPostDetailCacheEntry> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(KnowPostMapper.class);
        counterService = mock(CounterService.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        detailCache = Caffeine.newBuilder().build();

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redis.getExpire(anyString())).thenReturn(120L);
        when(counterService.getCounts(eq("knowpost"), anyString(), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        CacheProperties cacheProperties = new CacheProperties();
        service = new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper().findAndRegisterModules(),
                new OssProperties(),
                counterService,
                mock(UserCounterService.class),
                redis,
                detailCache,
                new HotKeyDetector(cacheProperties),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }

    @Test
    void ownerViewingDraftDoesNotPopulateSharedDetailCache() {
        when(mapper.findDetailById(42L)).thenReturn(detailRow("draft", "public"));

        KnowPostDetailResponse ownerResponse = service.getDetail(42L, 7L);

        assertThat(ownerResponse.id()).isEqualTo("42");
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
        verify(valueOperations, never()).set(eq(DETAIL_KEY), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
    }

    @Test
    void publicPublishedDetailStillUsesSharedCache() {
        when(mapper.findDetailById(42L)).thenReturn(detailRow("published", "public"));

        KnowPostDetailResponse firstResponse = service.getDetail(42L, null);
        KnowPostDetailResponse cachedResponse = service.getDetail(42L, null);

        assertThat(firstResponse.id()).isEqualTo("42");
        assertThat(cachedResponse.id()).isEqualTo("42");
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNotNull();
        verify(valueOperations).set(eq(DETAIL_KEY), anyString(), any(Duration.class));
    }

    private KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(7L);
        row.setTitle("cached detail");
        row.setDescription("description");
        row.setTags("[\"tag\"]");
        row.setImgUrls("[\"img.png\"]");
        row.setContentUrl("https://example.test/content.md");
        row.setAuthorAvatar("avatar.png");
        row.setAuthorNickname("author");
        row.setAuthorTagJson("[]");
        row.setPublishTime(Instant.parse("2026-01-01T00:00:00Z"));
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }
}
