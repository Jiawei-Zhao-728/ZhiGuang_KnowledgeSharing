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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

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
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper().findAndRegisterModules(),
                ossProperties,
                counterService,
                userCounterService,
                redis,
                detailCache,
                hotKey,
                ragIndexService,
                outboxMapper
        );

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(hotKey.ttlForPublic(anyInt(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(counterService.getCounts(eq("knowpost"), anyString(), anyList()))
                .thenReturn(Map.of("like", 0L, "fav", 0L));
    }

    @Test
    void cachedPrivateDetailIsNotServedToNonOwner() {
        long postId = 42L;
        detailCache.put(detailKey(postId), response(postId, "100", "private", Instant.now()));

        assertThatThrownBy(() -> service.getDetail(postId, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(mapper, never()).findDetailById(anyLong());
    }

    @Test
    void cachedPublicPublishedDetailCanBeServedAnonymously() {
        long postId = 42L;
        detailCache.put(detailKey(postId), response(postId, "100", "public", Instant.now()));

        KnowPostDetailResponse result = service.getDetail(postId, null);

        assertThat(result.id()).isEqualTo(String.valueOf(postId));
        verify(mapper, never()).findDetailById(anyLong());
    }

    @Test
    void ownerOnlyDatabaseDetailDoesNotPopulateSharedCache() {
        long postId = 42L;
        when(mapper.findDetailById(postId)).thenReturn(row(postId, 100L, "published", "private"));

        KnowPostDetailResponse result = service.getDetail(postId, 100L);

        assertThat(result.visible()).isEqualTo("private");
        assertThat(detailCache.getIfPresent(detailKey(postId))).isNull();
        verify(valueOperations, never()).set(eq(detailKey(postId)), anyString(), any(Duration.class));
    }

    private static String detailKey(long postId) {
        return "knowpost:detail:" + postId + ":v1";
    }

    private static KnowPostDetailResponse response(long postId, String authorId, String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                String.valueOf(postId),
                "title",
                "description",
                "https://example.com/content.md",
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

    private static KnowPostDetailRow row(long postId, Long creatorId, String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(postId);
        row.setCreatorId(creatorId);
        row.setTitle("title");
        row.setDescription("description");
        row.setTags("[]");
        row.setImgUrls("[]");
        row.setContentUrl("https://example.com/content.md");
        row.setAuthorNickname("author");
        row.setPublishTime(Instant.now());
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }
}
