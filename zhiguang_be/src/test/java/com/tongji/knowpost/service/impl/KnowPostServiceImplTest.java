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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    @Test
    void getDetailDoesNotServeCachedPrivatePostToAnonymousCaller() {
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        cache.put("knowpost:detail:42:v1", cachedDetail("private", "100", Instant.now()));
        CounterService counterService = mock(CounterService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HotKeyDetector hotKey = mock(HotKeyDetector.class);
        KnowPostServiceImpl service = newService(cache, counterService, redis, hotKey);

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
        verifyNoInteractions(counterService, redis, hotKey);
    }

    @Test
    void getDetailServesCachedPrivatePostToOwner() {
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        cache.put("knowpost:detail:42:v1", cachedDetail("private", "100", Instant.now()));
        CounterService counterService = mock(CounterService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HotKeyDetector hotKey = mock(HotKeyDetector.class);
        when(counterService.getCounts("knowpost", "42", List.of("like", "fav")))
                .thenReturn(Map.of("like", 7L, "fav", 3L));
        when(counterService.isLiked("knowpost", "42", 100L)).thenReturn(true);
        when(counterService.isFaved("knowpost", "42", 100L)).thenReturn(false);
        when(hotKey.ttlForPublic(anyInt(), anyString())).thenReturn(60);
        when(redis.getExpire(anyString())).thenReturn(0L);
        KnowPostServiceImpl service = newService(cache, counterService, redis, hotKey);

        KnowPostDetailResponse response = service.getDetail(42L, 100L);

        assertThat(response.id()).isEqualTo("42");
        assertThat(response.likeCount()).isEqualTo(7L);
        assertThat(response.favoriteCount()).isEqualTo(3L);
        assertThat(response.liked()).isTrue();
        assertThat(response.faved()).isFalse();
    }

    private static KnowPostServiceImpl newService(
            Cache<String, KnowPostDetailResponse> cache,
            CounterService counterService,
            StringRedisTemplate redis,
            HotKeyDetector hotKey
    ) {
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

    private static KnowPostDetailResponse cachedDetail(String visible, String authorId, Instant publishTime) {
        return new KnowPostDetailResponse(
                "42",
                "Private post",
                "description",
                "https://example.test/content.md",
                List.of(),
                List.of(),
                authorId,
                "avatar",
                "author",
                "{}",
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
