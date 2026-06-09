package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final long OWNER_ID = 7L;
    private static final String DETAIL_CACHE_KEY = "knowpost:detail:" + POST_ID + ":v2";

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
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        OssProperties ossProperties = new OssProperties();
        ossProperties.setBucket("bucket");
        ossProperties.setEndpoint("oss.example.com");
        detailCache = Caffeine.newBuilder().build();

        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lenient().when(counterService.getCounts(anyString(), anyString(), anyList()))
                .thenReturn(Map.of("like", 0L, "fav", 0L));
        lenient().when(hotKey.ttlForPublic(anyInt(), anyString())).thenReturn(60);
        lenient().when(redis.getExpire(anyString())).thenReturn(60L);

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
    void ownerOnlyDetailIsNotCachedForAnonymousReuse() {
        KnowPostDetailRow draft = detailRow("draft", "public", null);
        when(mapper.findDetailById(POST_ID)).thenReturn(draft);

        KnowPostDetailResponse ownerResponse = service.getDetail(POST_ID, OWNER_ID);

        assertThat(ownerResponse.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(detailCache.getIfPresent(DETAIL_CACHE_KEY)).isNull();
        verify(valueOperations, never()).set(eq(DETAIL_CACHE_KEY), startsWith("{"), any(Duration.class));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
        verify(mapper, times(2)).findDetailById(POST_ID);
    }

    @Test
    void publicPublishedDetailCanBeServedFromSharedCache() {
        KnowPostDetailRow row = detailRow("published", "public", Instant.parse("2026-06-09T11:00:00Z"));
        when(mapper.findDetailById(POST_ID)).thenReturn(row);

        service.getDetail(POST_ID, null);
        KnowPostDetailResponse cachedResponse = service.getDetail(POST_ID, null);

        assertThat(cachedResponse.title()).isEqualTo("Public title");
        assertThat(detailCache.getIfPresent(DETAIL_CACHE_KEY)).isNotNull();
        verify(mapper, times(1)).findDetailById(POST_ID);
        verify(valueOperations, atLeastOnce()).set(eq(DETAIL_CACHE_KEY), startsWith("{"), any(Duration.class));
    }

    @Test
    void confirmContentRejectsObjectKeyOutsidePostPrefix() {
        assertThatThrownBy(() -> service.confirmContent(OWNER_ID, POST_ID, "posts/99/content.md", "etag", 12L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("objectKey 非法");
        assertThatThrownBy(() -> service.confirmContent(OWNER_ID, POST_ID, "posts/42/../99/content.md", "etag", 12L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("objectKey 非法");

        verify(mapper, never()).updateContent(any(KnowPost.class));
    }

    private KnowPostDetailRow detailRow(String status, String visible, Instant publishTime) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("Public title");
        row.setDescription("description");
        row.setContentUrl("https://bucket.oss.example.com/posts/42/content.md");
        row.setAuthorNickname("owner");
        row.setPublishTime(publishTime);
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }
}
