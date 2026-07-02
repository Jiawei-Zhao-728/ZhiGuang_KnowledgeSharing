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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 100L;
    private static final String PAGE_KEY = "knowpost:detail:" + POST_ID + ":v1";

    @Mock
    private KnowPostMapper mapper;
    @Mock
    private CounterService counterService;
    @Mock
    private UserCounterService userCounterService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HotKeyDetector hotKeyDetector;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();
        when(redis.opsForValue()).thenReturn(valueOperations);
        service = new KnowPostServiceImpl(
                mapper,
                new SnowflakeIdGenerator(),
                objectMapper,
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
    void cachedPrivateDetailIsNotServedToAnonymousUsers() throws Exception {
        KnowPostDetailResponse cached = detailResponse("private", null);
        when(valueOperations.get(PAGE_KEY)).thenReturn(objectMapper.writeValueAsString(cached));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("published", "private"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(redis).delete(PAGE_KEY);
        assertThat(detailCache.getIfPresent(PAGE_KEY)).isNull();
    }

    @Test
    void cachedDraftDetailIsNotServedEvenWhenVisibleFieldIsPublic() throws Exception {
        KnowPostDetailResponse cached = detailResponse("public", null);
        when(valueOperations.get(PAGE_KEY)).thenReturn(objectMapper.writeValueAsString(cached));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public"));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(redis).delete(PAGE_KEY);
        assertThat(detailCache.getIfPresent(PAGE_KEY)).isNull();
    }

    @Test
    void ownerPrivateDetailBypassesSharedCacheOnReload() throws Exception {
        KnowPostDetailResponse cached = detailResponse("private", null);
        KnowPostDetailRow row = detailRow("published", "private");
        when(valueOperations.get(PAGE_KEY)).thenReturn(objectMapper.writeValueAsString(cached), null);
        when(mapper.findDetailById(POST_ID)).thenReturn(row, row);
        when(counterService.getCounts("knowpost", String.valueOf(POST_ID), List.of("like", "fav")))
                .thenReturn(Collections.emptyMap());

        KnowPostDetailResponse response = service.getDetail(POST_ID, 42L);

        assertThat(response.visible()).isEqualTo("private");
        assertThat(response.contentUrl()).isEqualTo("https://cdn.example.com/private.md");
        verify(redis).delete(PAGE_KEY);
        verify(valueOperations, never()).set(eq(PAGE_KEY), anyString(), any(Duration.class));
        assertThat(detailCache.getIfPresent(PAGE_KEY)).isNull();
    }

    private KnowPostDetailResponse detailResponse(String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "private title",
                "private description",
                "https://cdn.example.com/private.md",
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
                visible,
                "image_text",
                publishTime
        );
    }

    private KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(42L);
        row.setStatus(status);
        row.setVisible(visible);
        row.setTitle("private title");
        row.setDescription("private description");
        row.setContentUrl("https://cdn.example.com/private.md");
        row.setTags("[]");
        row.setImgUrls("[]");
        row.setAuthorNickname("author");
        row.setIsTop(false);
        row.setType("image_text");
        return row;
    }
}
