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
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    @Test
    void rejectsAnonymousViewerOnCachedPrivateDetail() {
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        cache.put("knowpost:detail:42:v1", detail("42", "7", "private", Instant.now()));

        KnowPostServiceImpl service = service(mock(KnowPostMapper.class), cache, mock(StringRedisTemplate.class), mock(CounterService.class));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    @Test
    void doesNotWriteOwnerOnlyDetailIntoSharedCaches() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(7L);
        row.setTitle("private draft");
        row.setVisible("private");
        row.setStatus("published");
        row.setType("image_text");
        when(mapper.findDetailById(42L)).thenReturn(row);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("knowpost:detail:42:v1")).thenReturn(null);

        CounterService counterService = mock(CounterService.class);
        when(counterService.getCounts(eq("knowpost"), eq("42"), any())).thenReturn(Map.of());

        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        KnowPostServiceImpl service = service(mapper, cache, redis, counterService);

        KnowPostDetailResponse response = service.getDetail(42L, 7L);

        assertThat(response.id()).isEqualTo("42");
        assertThat(cache.getIfPresent("knowpost:detail:42:v1")).isNull();
        verify(values, never()).set(eq("knowpost:detail:42:v1"), any(), any());
    }

    private KnowPostServiceImpl service(
            KnowPostMapper mapper,
            Cache<String, KnowPostDetailResponse> cache,
            StringRedisTemplate redis,
            CounterService counterService
    ) {
        return new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                new OssProperties(),
                counterService,
                mock(UserCounterService.class),
                redis,
                cache,
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }

    private KnowPostDetailResponse detail(String id, String authorId, String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                id,
                "title",
                "description",
                "https://example.test/content.md",
                List.of(),
                List.of(),
                authorId,
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
}
