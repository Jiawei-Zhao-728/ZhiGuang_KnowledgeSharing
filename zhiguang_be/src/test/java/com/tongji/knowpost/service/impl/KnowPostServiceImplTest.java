package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    private static final String DETAIL_KEY = "knowpost:detail:42:v1";

    private final KnowPostMapper mapper = mock(KnowPostMapper.class);
    private final SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OssProperties ossProperties = mock(OssProperties.class);
    private final CounterService counterService = mock(CounterService.class);
    private final UserCounterService userCounterService = mock(UserCounterService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final HotKeyDetector hotKeyDetector = mock(HotKeyDetector.class);
    private final RagIndexService ragIndexService = mock(RagIndexService.class);
    private final OutboxMapper outboxMapper = mock(OutboxMapper.class);

    @Test
    void rejectsNonOwnerWhenDraftDetailIsInLocalCache() {
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        cache.put(DETAIL_KEY, detail("1", "public", null));
        KnowPostServiceImpl service = newService(cache);

        assertThatThrownBy(() -> service.getDetail(42L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(mapper, never()).findDetailById(42L);
    }

    @Test
    void rejectsAnonymousUserWhenPrivateDetailIsInRedisCache() throws Exception {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_KEY)).thenReturn(objectMapper.writeValueAsString(
                detail("1", "private", Instant.parse("2026-06-26T11:00:00Z"))
        ));
        KnowPostServiceImpl service = newService(Caffeine.newBuilder().build());

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(mapper, never()).findDetailById(42L);
    }

    private KnowPostServiceImpl newService(Cache<String, KnowPostDetailResponse> cache) {
        return new KnowPostServiceImpl(
                mapper,
                idGenerator,
                objectMapper,
                ossProperties,
                counterService,
                userCounterService,
                redis,
                cache,
                hotKeyDetector,
                ragIndexService,
                outboxMapper
        );
    }

    private KnowPostDetailResponse detail(String authorId, String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                "42",
                "cached title",
                "cached description",
                "https://cdn.example.com/posts/42/content.md",
                List.of(),
                List.of(),
                authorId,
                "avatar.png",
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
