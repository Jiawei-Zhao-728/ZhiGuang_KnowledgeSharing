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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final long OWNER_ID = 7L;
    private static final String DETAIL_CACHE_KEY = "knowpost:detail:" + POST_ID + ":v1";

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
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        detailCache = Caffeine.newBuilder().build();
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
    void rejectsUnauthorizedCallerWhenPrivateDetailIsInRedisCache() throws Exception {
        KnowPostDetailResponse cachedPrivateDetail = detailResponse("private", Instant.now());
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_CACHE_KEY)).thenReturn(objectMapper.writeValueAsString(cachedPrivateDetail));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper);
    }

    @Test
    void doesNotStoreOwnerOnlyDetailInSharedCaches() {
        KnowPostDetailRow privateRow = detailRow("private", "published", Instant.now());
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_CACHE_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(privateRow);
        when(counterService.getCounts("knowpost", String.valueOf(POST_ID), List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, OWNER_ID);

        assertThat(response.contentUrl()).isEqualTo("https://cdn.example/private-content");
        assertThat(detailCache.getIfPresent(DETAIL_CACHE_KEY)).isNull();
        verify(valueOperations, never()).set(eq(DETAIL_CACHE_KEY), anyString(), any(Duration.class));
    }

    @Test
    void allowsPublicPublishedDetailToUseSharedCache() {
        KnowPostDetailRow publicRow = detailRow("public", "published", Instant.now());
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_CACHE_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(publicRow);
        when(counterService.getCounts("knowpost", String.valueOf(POST_ID), List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));
        when(hotKey.ttlForPublic(anyInt(), eq(DETAIL_CACHE_KEY))).thenReturn(60);

        KnowPostDetailResponse response = service.getDetail(POST_ID, null);

        assertThat(response.visible()).isEqualTo("public");
        assertThat(detailCache.getIfPresent(DETAIL_CACHE_KEY)).isNotNull();
        verify(valueOperations).set(eq(DETAIL_CACHE_KEY), anyString(), any(Duration.class));
    }

    private KnowPostDetailResponse detailResponse(String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "cached title",
                "cached description",
                "https://cdn.example/private-content",
                List.of(),
                List.of(),
                String.valueOf(OWNER_ID),
                "avatar.png",
                "owner",
                "[]",
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

    private KnowPostDetailRow detailRow(String visible, String status, Instant publishTime) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("title");
        row.setDescription("description");
        row.setContentUrl("https://cdn.example/private-content");
        row.setImgUrls("[]");
        row.setTags("[]");
        row.setAuthorAvatar("avatar.png");
        row.setAuthorNickname("owner");
        row.setAuthorTagJson("[]");
        row.setPublishTime(publishTime);
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }
}
