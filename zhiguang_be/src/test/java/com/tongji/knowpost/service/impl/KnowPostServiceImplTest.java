package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.api.dto.FeedPageResponse;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {
    private static final long POST_ID = 123L;
    private static final String DETAIL_KEY = "knowpost:detail:" + POST_ID + ":v2";

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
    private ValueOperations<String, String> valueOps;
    @Mock
    private HotKeyDetector hotKey;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private Cache<String, KnowPostDetailResponse> detailCache;
    private Cache<String, FeedPageResponse> feedPublicCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();
        feedPublicCache = Caffeine.newBuilder().build();

        OssProperties ossProperties = new OssProperties();
        ossProperties.setBucket("bucket");
        ossProperties.setEndpoint("oss.example.com");

        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(counterService.getCounts(eq("knowpost"), anyString(), anyList())).thenReturn(Map.of());

        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
                ossProperties,
                counterService,
                userCounterService,
                redis,
                detailCache,
                feedPublicCache,
                hotKey,
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void ownerCanReadPrivateDetailButItIsNotSharedCached() {
        KnowPostDetailRow row = privateRow();
        when(valueOps.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(row);

        KnowPostDetailResponse response = service.getDetail(POST_ID, row.getCreatorId());

        assertThat(response.visible()).isEqualTo("private");
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
        verify(valueOps, never()).set(eq(DETAIL_KEY), anyString(), any(Duration.class));
    }

    @Test
    void cachedPrivateDetailDoesNotBypassPermissionCheck() throws Exception {
        KnowPostDetailResponse cachedPrivate = new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "private title",
                "secret",
                "https://cdn.example.com/posts/123/content.md",
                List.of(),
                List.of(),
                "42",
                null,
                "author",
                null,
                0L,
                0L,
                null,
                null,
                false,
                "private",
                "image_text",
                Instant.now()
        );
        when(valueOps.get(DETAIL_KEY)).thenReturn(objectMapper.writeValueAsString(cachedPrivate));
        when(mapper.findDetailById(POST_ID)).thenReturn(privateRow());

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(redis, atLeastOnce()).delete(DETAIL_KEY);
    }

    @Test
    void privateVisibilityUpdatePurgesPublicSurfacesAndUpdatesSearchIndex() {
        when(mapper.updateVisibility(POST_ID, 42L, "private")).thenReturn(1);
        when(idGen.nextId()).thenReturn(9001L);

        service.updateVisibility(42L, POST_ID, "private");

        verify(outboxMapper).insert(eq(9001L), eq("knowpost"), eq(POST_ID), eq("KnowPostVisibilityUpdated"), startsWith("{"));
        verify(ragIndexService).deleteIndexedChunks(POST_ID);
        verify(redis).delete("feed:item:" + POST_ID);
        assertThat(feedPublicCache.estimatedSize()).isZero();
        verify(ragIndexService, never()).ensureIndexed(anyLong());
    }

    @Test
    void publicVisibilityUpdateRefreshesPublicSurfaces() {
        when(mapper.updateVisibility(POST_ID, 42L, "public")).thenReturn(1);
        when(idGen.nextId()).thenReturn(9002L);

        service.updateVisibility(42L, POST_ID, "public");

        verify(outboxMapper).insert(eq(9002L), eq("knowpost"), eq(POST_ID), eq("KnowPostVisibilityUpdated"), startsWith("{"));
        verify(ragIndexService).ensureIndexed(POST_ID);
        verify(redis).delete("feed:item:" + POST_ID);
        verify(ragIndexService, never()).deleteIndexedChunks(anyLong());
    }

    private KnowPostDetailRow privateRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(42L);
        row.setTitle("private title");
        row.setDescription("secret");
        row.setContentUrl("https://cdn.example.com/posts/123/content.md");
        row.setAuthorNickname("author");
        row.setIsTop(false);
        row.setVisible("private");
        row.setType("image_text");
        row.setStatus("published");
        row.setPublishTime(Instant.now());
        return row;
    }
}
