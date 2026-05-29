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
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

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
    private HotKeyDetector hotKey;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        detailCache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                ossProperties,
                counterService,
                userCounterService,
                redis,
                detailCache,
                hotKey,
                ragIndexService,
                outboxMapper);
    }

    @Test
    void cachedPrivateDetailIsNotReturnedToAnonymousUser() {
        detailCache.put("knowpost:detail:10:v1", privateCachedDetail());

        assertThatThrownBy(() -> service.getDetail(10L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(mapper, never()).findDetailById(10L);
    }

    @Test
    void cachedPrivateDetailIsStillReturnedToOwner() {
        detailCache.put("knowpost:detail:10:v1", privateCachedDetail());
        when(hotKey.ttlForPublic(eq(60), anyString())).thenReturn(60);
        when(redis.getExpire(anyString())).thenReturn(60L);
        when(counterService.getCounts("knowpost", "10", List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostDetailResponse response = service.getDetail(10L, 1L);

        assertThat(response.contentUrl()).isEqualTo("https://oss/private.md");
        assertThat(response.liked()).isFalse();
        assertThat(response.faved()).isFalse();
    }

    private KnowPostDetailResponse privateCachedDetail() {
        return new KnowPostDetailResponse(
                "10",
                "private draft",
                "secret",
                "https://oss/private.md",
                List.of(),
                List.of(),
                "1",
                null,
                "author",
                null,
                0L,
                0L,
                null,
                null,
                false,
                "private",
                "image_text",
                null);
    }
}
