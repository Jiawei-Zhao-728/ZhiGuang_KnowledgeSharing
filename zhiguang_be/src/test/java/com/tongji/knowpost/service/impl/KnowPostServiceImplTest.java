package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    @Test
    void cachedPrivateDetailRequiresOwner() {
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        cache.put("knowpost:detail:1:v1", detail("private", Instant.now()));

        KnowPostServiceImpl service = serviceWith(cache, mock(CounterService.class),
                mock(StringRedisTemplate.class), mock(HotKeyDetector.class));

        assertThatThrownBy(() -> service.getDetail(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
    }

    @Test
    void cachedPublishedPublicDetailCanBeServedAnonymously() {
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        cache.put("knowpost:detail:1:v1", detail("public", Instant.now()));
        CounterService counterService = mock(CounterService.class);
        when(counterService.getCounts("knowpost", "1", List.of("like", "fav")))
                .thenReturn(Map.of("like", 3L, "fav", 2L));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        HotKeyDetector hotKey = mock(HotKeyDetector.class);
        when(hotKey.ttlForPublic(60, "knowpost:1")).thenReturn(60);

        KnowPostServiceImpl service = serviceWith(cache, counterService, redis, hotKey);

        KnowPostDetailResponse response = service.getDetail(1L, null);

        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.favoriteCount()).isEqualTo(2L);
        assertThat(response.liked()).isFalse();
        assertThat(response.faved()).isFalse();
    }

    private KnowPostServiceImpl serviceWith(Cache<String, KnowPostDetailResponse> cache,
                                            CounterService counterService,
                                            StringRedisTemplate redis,
                                            HotKeyDetector hotKey) {
        return new KnowPostServiceImpl(
                null,
                null,
                new ObjectMapper(),
                null,
                counterService,
                null,
                redis,
                cache,
                hotKey,
                null,
                null
        );
    }

    private KnowPostDetailResponse detail(String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                "1",
                "title",
                "description",
                "https://example.com/content",
                Collections.emptyList(),
                Collections.emptyList(),
                "42",
                "https://example.com/avatar.png",
                "author",
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
}
