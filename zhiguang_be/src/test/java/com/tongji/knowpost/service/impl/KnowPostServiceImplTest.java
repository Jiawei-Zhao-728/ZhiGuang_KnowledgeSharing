package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final long AUTHOR_ID = 10L;
    private static final String DETAIL_KEY = "knowpost:detail:" + POST_ID + ":v2";

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
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HotKeyDetector hotKey;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private ObjectMapper objectMapper;
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        detailCache = Caffeine.newBuilder().build();
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);

        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
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
    void ownerOnlyDraftDetailIsNotStoredInSharedCaches() {
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public"));
        when(counterService.getCounts(anyString(), anyString(), anyList()))
                .thenReturn(Map.of("like", 1L, "fav", 2L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, AUTHOR_ID);

        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.visible()).isEqualTo("public");
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void localCacheHitRejectsUnauthorizedNonPublicDetail() {
        detailCache.put(DETAIL_KEY, detailResponse("draft", "public"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper);
    }

    @Test
    void redisCacheHitRejectsUnauthorizedNonPublicDetail() throws Exception {
        when(valueOperations.get(DETAIL_KEY))
                .thenReturn(objectMapper.writeValueAsString(detailResponse("published", "private")));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper);
    }

    private KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(AUTHOR_ID);
        row.setTitle("private draft");
        row.setDescription("description");
        row.setContentUrl("https://example.test/content.md");
        row.setImgUrls("[\"https://example.test/image.png\"]");
        row.setTags("[\"tag\"]");
        row.setAuthorAvatar("https://example.test/avatar.png");
        row.setAuthorNickname("author");
        row.setAuthorTagJson("{}");
        row.setPublishTime(Instant.parse("2026-05-17T00:00:00Z"));
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }

    private KnowPostDetailResponse detailResponse(String status, String visible) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "private draft",
                "description",
                "https://example.test/content.md",
                List.of("https://example.test/image.png"),
                List.of("tag"),
                String.valueOf(AUTHOR_ID),
                "https://example.test/avatar.png",
                "author",
                "{}",
                1L,
                2L,
                null,
                null,
                false,
                visible,
                status,
                "image_text",
                Instant.parse("2026-05-17T00:00:00Z")
        );
    }
}
