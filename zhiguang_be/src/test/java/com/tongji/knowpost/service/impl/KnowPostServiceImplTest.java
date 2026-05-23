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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final String DETAIL_KEY = "knowpost:detail:" + POST_ID + ":v1";

    @Mock
    private KnowPostMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGen;
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
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
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
    void cachedPrivateDetailDeniesDifferentUser() {
        detailCache.put(DETAIL_KEY, detailResponse("private", Instant.now(), "100"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verifyNoInteractions(mapper, counterService, hotKey);
    }

    @Test
    void privateDbResponseIsNotStoredInSharedCache() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private", 100L));
        when(counterService.getCounts("knowpost", String.valueOf(POST_ID), List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, 100L);

        assertThat(response.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
        verify(valueOperations, never()).set(eq(DETAIL_KEY), anyString(), any(Duration.class));
    }

    private static KnowPostDetailResponse detailResponse(String visible, Instant publishTime, String authorId) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "title",
                "description",
                "https://cdn.example.com/content.md",
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
                publishTime
        );
    }

    private static KnowPostDetailRow detailRow(String status, String visible, long creatorId) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(creatorId);
        row.setStatus(status);
        row.setVisible(visible);
        row.setTitle("title");
        row.setDescription("description");
        row.setContentUrl("https://cdn.example.com/content.md");
        row.setAuthorNickname("author");
        row.setIsTop(false);
        row.setType("image_text");
        row.setPublishTime(Instant.now());
        return row;
    }
}
