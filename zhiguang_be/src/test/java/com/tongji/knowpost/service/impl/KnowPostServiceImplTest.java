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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final long OWNER_ID = 101L;
    private static final String DETAIL_KEY = "knowpost:detail:42:v1";

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

    private Cache<String, KnowPostDetailResponse> cache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                new OssProperties(),
                counterService,
                userCounterService,
                redis,
                cache,
                hotKey,
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void cachedPrivateDetailIsDeniedToNonOwner() {
        cache.put(DETAIL_KEY, detailResponse("private", Instant.now()));

        assertThrows(BusinessException.class, () -> service.getDetail(POST_ID, 202L));

        verify(mapper, never()).findDetailById(any());
    }

    @Test
    void ownerPrivateDetailLoadedFromDbIsNotStoredInSharedCaches() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_KEY)).thenReturn(null);
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "private"));
        when(counterService.getCounts(eq("knowpost"), eq(String.valueOf(POST_ID)), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 3L, "fav", 4L));

        KnowPostDetailResponse response = service.getDetail(POST_ID, OWNER_ID);

        assertEquals("private", response.visible());
        assertNull(cache.getIfPresent(DETAIL_KEY));
        verify(valueOperations, never()).set(eq(DETAIL_KEY), anyString(), any(Duration.class));
    }

    private static KnowPostDetailResponse detailResponse(String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "secret draft",
                "private description",
                "https://cdn.example.com/posts/42/content.md",
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

    private static KnowPostDetailRow detailRow(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(OWNER_ID);
        row.setTitle("secret draft");
        row.setDescription("private description");
        row.setTags("[]");
        row.setImgUrls("[]");
        row.setContentUrl("https://cdn.example.com/posts/42/content.md");
        row.setAuthorNickname("author");
        row.setPublishTime(null);
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        return row;
    }
}
