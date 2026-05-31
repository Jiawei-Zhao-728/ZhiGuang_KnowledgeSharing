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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    @Mock
    private KnowPostMapper mapper;
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
                new SnowflakeIdGenerator(),
                new ObjectMapper(),
                new OssProperties(),
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
    void cachedPrivateDetailRejectsNonOwner() {
        detailCache.put("knowpost:detail:42:v1", cachedDetail("private"));
        when(mapper.findDetailById(42L)).thenReturn(detailRow(42L, 7L, "published", "private"));

        assertThatThrownBy(() -> service.getDetail(42L, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verifyNoInteractions(counterService);
    }

    @Test
    void cachedDraftWithPublicVisibilityRejectsAnonymousViewer() {
        detailCache.put("knowpost:detail:42:v1", cachedDetail("public"));
        when(mapper.findDetailById(42L)).thenReturn(detailRow(42L, 7L, "draft", "public"));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verifyNoInteractions(counterService);
    }

    @Test
    void cachedPublishedPublicDetailStillServesAnonymousViewer() {
        detailCache.put("knowpost:detail:42:v1", cachedDetail("public"));
        when(mapper.findDetailById(42L)).thenReturn(detailRow(42L, 7L, "published", "public"));
        when(hotKey.ttlForPublic(eq(60), anyString())).thenReturn(60);
        when(redis.getExpire(anyString())).thenReturn(60L);
        when(counterService.getCounts("knowpost", "42", List.of("like", "fav")))
                .thenReturn(Map.of("like", 3L, "fav", 2L));

        KnowPostDetailResponse response = service.getDetail(42L, null);

        assertThat(response.id()).isEqualTo("42");
        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.favoriteCount()).isEqualTo(2L);
        assertThat(response.liked()).isFalse();
        assertThat(response.faved()).isFalse();
    }

    private KnowPostDetailResponse cachedDetail(String visible) {
        return new KnowPostDetailResponse(
                "42",
                "cached title",
                "cached description",
                "https://cdn.example/posts/42.md",
                List.of("https://cdn.example/posts/42.png"),
                List.of("java"),
                "7",
                "https://cdn.example/avatar.png",
                "author",
                "[]",
                1L,
                1L,
                null,
                null,
                false,
                visible,
                "image_text",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private KnowPostDetailRow detailRow(long id, long creatorId, String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(id);
        row.setCreatorId(creatorId);
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
