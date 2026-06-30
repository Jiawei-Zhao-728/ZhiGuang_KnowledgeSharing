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
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final long OWNER_ID = 7L;
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

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, String> redisValues = new ConcurrentHashMap<>();
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(counterService.getCounts(anyString(), anyString(), any())).thenReturn(Map.of("like", 0L, "fav", 0L));

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
    void privateOwnerDetailDoesNotPopulateSharedCaches() {
        when(mapper.findDetailById(POST_ID)).thenReturn(privateDetailRow());

        KnowPostDetailResponse ownerResponse = service.getDetail(POST_ID, OWNER_ID);

        assertThat(ownerResponse.visible()).isEqualTo("private");
        assertThat(redisValues).doesNotContainKey(DETAIL_KEY);
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
    }

    @Test
    void anonymousCacheHitCannotReadPrivateDetail() throws Exception {
        redisValues.put(DETAIL_KEY, objectMapper.writeValueAsString(privateCachedResponse()));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(mapper, never()).findDetailById(POST_ID);
        assertThat(detailCache.getIfPresent(DETAIL_KEY)).isNull();
    }

    @Test
    void anonymousLocalCacheHitCannotReadPrivateDetail() {
        detailCache.put(DETAIL_KEY, privateCachedResponse());

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(mapper, never()).findDetailById(POST_ID);
    }

    private static KnowPostDetailRow privateDetailRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("Private title");
        row.setDescription("Private description");
        row.setContentUrl("https://cdn.example.com/private.md");
        row.setImgUrls("[\"https://cdn.example.com/private.png\"]");
        row.setTags("[\"secret\"]");
        row.setAuthorNickname("owner");
        row.setIsTop(false);
        row.setVisible("private");
        row.setType("image_text");
        row.setStatus("published");
        row.setPublishTime(Instant.parse("2026-06-30T10:00:00Z"));
        return row;
    }

    private static KnowPostDetailResponse privateCachedResponse() {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "Private title",
                "Private description",
                "https://cdn.example.com/private.md",
                List.of("https://cdn.example.com/private.png"),
                List.of("secret"),
                String.valueOf(OWNER_ID),
                null,
                "owner",
                null,
                0L,
                0L,
                null,
                null,
                false,
                "private",
                "image_text",
                Instant.parse("2026-06-30T10:00:00Z")
        );
    }
}
