package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.api.dto.FeedItemResponse;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostFeedRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: public feed id-list cache must preserve newest-first order from SQL.
 * {@code LPUSH} reverses the sequence; cached pages would then serve oldest-first.
 */
@ExtendWith(MockitoExtension.class)
class KnowPostFeedServiceImplTest {

    @Mock
    KnowPostMapper mapper;
    @Mock
    StringRedisTemplate redis;
    @Mock
    CounterService counterService;
    @Mock
    HotKeyDetector hotKey;
    @Mock
    ListOperations<String, String> listOps;
    @Mock
    ValueOperations<String, String> valueOps;
    @Mock
    SetOperations<String, String> setOps;

    Cache<String, FeedPageResponse> feedPublicCache;
    Cache<String, FeedPageResponse> feedMineCache;
    KnowPostFeedServiceImpl service;

    @BeforeEach
    void setUp() {
        feedPublicCache = Caffeine.newBuilder().build();
        feedMineCache = Caffeine.newBuilder().build();
        lenient().when(redis.opsForList()).thenReturn(listOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        lenient().when(counterService.getCounts(anyString(), anyString(), any())).thenReturn(Map.of("like", 0L, "fav", 0L));
        lenient().when(hotKey.ttlForPublic(anyInt(), anyString())).thenReturn(60);

        service = new KnowPostFeedServiceImpl(
                mapper,
                redis,
                new ObjectMapper(),
                counterService,
                feedPublicCache,
                feedMineCache,
                hotKey
        );
    }

    @Test
    void publicFeedCacheWritePreservesNewestFirstOrder() {
        // Cold fragment cache → DB backfill
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(null);
        when(valueOps.get(anyString())).thenReturn(null);

        KnowPostFeedRow newest = row(30L, "newest");
        KnowPostFeedRow middle = row(20L, "middle");
        KnowPostFeedRow oldest = row(10L, "oldest");
        // SQL contract: publish_time DESC (newest first); size+1 probe has no extra row → hasMore=false
        when(mapper.listFeedPublic(eq(3), eq(0))).thenReturn(List.of(newest, middle, oldest));

        FeedPageResponse page = service.getPublicFeed(1, 2, null);

        assertEquals(List.of("30", "20"), page.items().stream().map(FeedItemResponse::id).toList());
        // size+1 probe returned 3 rows for page size 2 → hasMore
        assertTrue(page.hasMore());

        ArgumentCaptor<String> idsKeyCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        verify(redis, atLeastOnce()).delete(idsKeyCaptor.capture());
        String idsKey = idsKeyCaptor.getAllValues().stream()
                .filter(k -> k.contains("feed:public:ids:v2:"))
                .filter(k -> !k.endsWith(":hasMore"))
                .findFirst()
                .orElseThrow();

        verify(listOps).rightPushAll(eq(idsKey), idsCaptor.capture());
        assertEquals(List.of("30", "20"), List.copyOf(idsCaptor.getValue()));
        verify(listOps, never()).leftPushAll(anyString(), anyCollection());
    }

    @Test
    void publicFeedAssembledFromCacheKeepsStoredIdOrder() throws Exception {
        ObjectMapper om = new ObjectMapper();
        FeedItemResponse a = item("30", "newest");
        FeedItemResponse b = item("20", "middle");

        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(List.of("30", "20"));
        when(valueOps.get(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key.endsWith(":hasMore")) {
                return "0";
            }
            return null;
        });
        when(valueOps.multiGet(any())).thenReturn(List.of(
                om.writeValueAsString(a),
                om.writeValueAsString(b)
        ));

        FeedPageResponse page = service.getPublicFeed(1, 2, null);

        assertEquals(List.of("30", "20"), page.items().stream().map(FeedItemResponse::id).toList());
        verify(mapper, never()).listFeedPublic(anyInt(), anyInt());
    }

    private static KnowPostFeedRow row(long id, String title) {
        KnowPostFeedRow r = new KnowPostFeedRow();
        r.setId(id);
        r.setTitle(title);
        r.setDescription(title);
        r.setTags("[]");
        r.setImgUrls("[]");
        r.setAuthorAvatar("a");
        r.setAuthorNickname("n");
        r.setAuthorTagJson("[]");
        r.setPublishTime(Instant.ofEpochMilli(id));
        r.setIsTop(false);
        return r;
    }

    private static FeedItemResponse item(String id, String title) {
        return new FeedItemResponse(id, title, title, null, List.of(), "a", "n", "[]", 0L, 0L, null, null, null);
    }
}
