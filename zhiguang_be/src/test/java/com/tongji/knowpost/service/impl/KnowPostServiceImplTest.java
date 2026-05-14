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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {
    private static final long POST_ID = 42L;
    private static final String PAGE_KEY = "knowpost:detail:" + POST_ID + ":v1";

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

    private ObjectMapper objectMapper;
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        detailCache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
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
    void localDetailCacheStillRequiresCurrentViewerPermission() {
        detailCache.put(PAGE_KEY, cachedDetail("private"));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private", 7L));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(counterService, never()).getCounts(anyString(), anyString(), any());
        verify(counterService, never()).isLiked(anyString(), anyString(), anyLong());
        verify(counterService, never()).isFaved(anyString(), anyString(), anyLong());
    }

    @Test
    void redisDetailCacheStillRequiresCurrentViewerPermission() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(PAGE_KEY)).thenReturn(objectMapper.writeValueAsString(cachedDetail("private")));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private", 7L));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(counterService, never()).getCounts(anyString(), anyString(), any());
        verify(counterService, never()).isLiked(anyString(), anyString(), anyLong());
        verify(counterService, never()).isFaved(anyString(), anyString(), anyLong());
    }

    @Test
    void cachedPublicVisibilityDoesNotExposeUnpublishedDrafts() {
        detailCache.put(PAGE_KEY, cachedDetail("public"));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public", 7L));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(counterService, never()).getCounts(anyString(), anyString(), any());
        verify(counterService, never()).isLiked(anyString(), anyString(), anyLong());
        verify(counterService, never()).isFaved(anyString(), anyString(), anyLong());
    }

    private KnowPostDetailResponse cachedDetail(String visible) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "cached title",
                "cached description",
                "https://example.com/content.md",
                List.of(),
                List.of(),
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
                null
        );
    }

    private KnowPostDetailRow detailRow(String status, String visible, Long creatorId) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(creatorId);
        row.setStatus(status);
        row.setVisible(visible);
        row.setTitle("db title");
        row.setTags("[]");
        row.setImgUrls("[]");
        return row;
    }
}
