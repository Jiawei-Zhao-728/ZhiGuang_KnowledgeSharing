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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    private static final long POST_ID = 1001L;
    private static final long OWNER_ID = 2002L;

    private KnowPostMapper mapper;
    private CounterService counterService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(KnowPostMapper.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        counterService = mock(CounterService.class);
        UserCounterService userCounterService = mock(UserCounterService.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        detailCache = Caffeine.newBuilder().build();
        HotKeyDetector hotKeyDetector = mock(HotKeyDetector.class);
        RagIndexService ragIndexService = mock(RagIndexService.class);
        OutboxMapper outboxMapper = mock(OutboxMapper.class);

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(counterService.getCounts(eq("knowpost"), anyString(), any())).thenReturn(Collections.emptyMap());
        when(hotKeyDetector.ttlForPublic(anyInt(), anyString())).thenReturn(60);

        service = new KnowPostServiceImpl(
                mapper,
                idGenerator,
                new ObjectMapper(),
                new OssProperties(),
                counterService,
                userCounterService,
                redis,
                detailCache,
                hotKeyDetector,
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void privateDetailReturnedToOwnerIsNotWrittenToSharedCache() {
        KnowPostDetailRow privateRow = detailRow("published", "private");
        when(valueOperations.get(detailKey())).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(privateRow);

        KnowPostDetailResponse ownerResponse = service.getDetail(POST_ID, OWNER_ID);

        assertThat(ownerResponse.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(ownerResponse.visible()).isEqualTo("private");
        assertThat(detailCache.getIfPresent(detailKey())).isNull();
        verify(valueOperations, never()).set(eq(detailKey()), anyString(), any());

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
    }

    @Test
    void publicPublishedDetailIsCachedAndServedWithoutDatabaseLookup() {
        KnowPostDetailRow publicRow = detailRow("published", "public");
        when(valueOperations.get(detailKey())).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(publicRow);

        KnowPostDetailResponse firstResponse = service.getDetail(POST_ID, null);

        assertThat(firstResponse.id()).isEqualTo(String.valueOf(POST_ID));
        assertThat(detailCache.getIfPresent(detailKey())).isNotNull();
        verify(valueOperations).set(eq(detailKey()), anyString(), any());

        reset(mapper);

        KnowPostDetailResponse cachedResponse = service.getDetail(POST_ID, null);

        assertThat(cachedResponse.id()).isEqualTo(String.valueOf(POST_ID));
        verify(mapper, never()).findDetailById(POST_ID);
    }

    private KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("title");
        row.setDescription("description");
        row.setContentUrl("https://example.com/content.md");
        row.setAuthorAvatar("avatar.png");
        row.setAuthorNickname("owner");
        row.setAuthorTagJson("[]");
        row.setPublishTime(Instant.parse("2026-01-01T00:00:00Z"));
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }

    private String detailKey() {
        return "knowpost:detail:" + POST_ID + ":v2";
    }
}
