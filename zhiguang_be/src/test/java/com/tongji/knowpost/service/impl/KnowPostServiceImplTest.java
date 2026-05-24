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
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KnowPostServiceImplTest {

    @Test
    void anonymousUserCannotReadCachedPrivateDetail() {
        Cache<String, KnowPostDetailResponse> detailCache = Caffeine.newBuilder().build();
        detailCache.put("knowpost:detail:7:v1", new KnowPostDetailResponse(
                "7",
                "private title",
                "private description",
                "https://example.test/content.md",
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
                "private",
                "image_text",
                Instant.now()
        ));
        KnowPostServiceImpl service = newService(detailCache);

        assertThatThrownBy(() -> service.getDetail(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    @Test
    void anonymousUserCannotReadCachedDraftEvenIfVisibleIsPublic() {
        Cache<String, KnowPostDetailResponse> detailCache = Caffeine.newBuilder().build();
        detailCache.put("knowpost:detail:8:v1", new KnowPostDetailResponse(
                "8",
                "draft title",
                "draft description",
                "https://example.test/draft.md",
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
                "public",
                "image_text",
                null
        ));
        KnowPostServiceImpl service = newService(detailCache);

        assertThatThrownBy(() -> service.getDetail(8L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    private KnowPostServiceImpl newService(Cache<String, KnowPostDetailResponse> detailCache) {
        return new KnowPostServiceImpl(
                mock(KnowPostMapper.class),
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                mock(OssProperties.class),
                mock(CounterService.class),
                mock(UserCounterService.class),
                mock(StringRedisTemplate.class),
                detailCache,
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }
}
