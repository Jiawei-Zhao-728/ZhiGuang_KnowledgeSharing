package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.config.CacheProperties;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final long OWNER_ID = 100L;
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
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(counterService.getCounts(eq("knowpost"), anyString(), anyList()))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                new OssProperties(),
                counterService,
                userCounterService,
                redis,
                detailCache,
                new HotKeyDetector(new CacheProperties()),
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void cachedPrivateDetailIsDeniedToAnonymousViewer() {
        detailCache.put(PAGE_KEY, detailResponse("private", null));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessage("无权限查看");
    }

    @Test
    void ownerOnlyDetailIsNotStoredInSharedCache() {
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public"));

        KnowPostDetailResponse response = service.getDetail(POST_ID, OWNER_ID);

        assertThat(response.contentUrl()).isEqualTo("https://cdn.example/private.md");
        assertThat(detailCache.getIfPresent(PAGE_KEY)).isNull();
        verify(valueOperations, never()).set(eq(PAGE_KEY), anyString(), org.mockito.ArgumentMatchers.any());
    }

    private KnowPostDetailResponse detailResponse(String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "Private post",
                "description",
                "https://cdn.example/private.md",
                List.of(),
                List.of(),
                String.valueOf(OWNER_ID),
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

    private KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("Private post");
        row.setDescription("description");
        row.setContentUrl("https://cdn.example/private.md");
        row.setStatus(status);
        row.setVisible(visible);
        row.setIsTop(false);
        row.setType("image_text");
        row.setPublishTime("published".equals(status) ? Instant.parse("2026-06-06T00:00:00Z") : null);
        return row;
    }
}
