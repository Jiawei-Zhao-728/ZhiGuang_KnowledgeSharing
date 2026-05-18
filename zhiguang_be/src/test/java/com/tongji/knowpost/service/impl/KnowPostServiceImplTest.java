package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {
    private static final long POST_ID = 42L;
    private static final long OWNER_ID = 100L;
    private static final String DETAIL_KEY = "knowpost:detail:" + POST_ID + ":v1";

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

    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
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
    void cachedDraftDetailIsNotServedToAnonymousUsers() {
        detailCache.put(DETAIL_KEY, detailResponse("public"));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(redis).delete(DETAIL_KEY);
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
    }

    @Test
    void ownerDraftDetailIsNotStoredInSharedCache() {
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public"));
        when(counterService.getCounts("knowpost", String.valueOf(POST_ID), List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, OWNER_ID);

        assertThat(response.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(response.contentUrl()).isEqualTo("https://cdn.example/private.md");
        verify(valueOperations, never()).set(eq(DETAIL_KEY), anyString(), any());
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
    }

    private static KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("private draft");
        row.setDescription("draft description");
        row.setTags("[\"java\"]");
        row.setImgUrls("[\"https://cdn.example/image.png\"]");
        row.setContentUrl("https://cdn.example/private.md");
        row.setAuthorAvatar("avatar.png");
        row.setAuthorNickname("author");
        row.setAuthorTagJson("{}");
        row.setPublishTime(Instant.parse("2026-01-01T00:00:00Z"));
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }

    private static KnowPostDetailResponse detailResponse(String visible) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "cached draft",
                "cached description",
                "https://cdn.example/private.md",
                List.of(),
                List.of(),
                String.valueOf(OWNER_ID),
                "avatar.png",
                "author",
                "{}",
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
}
