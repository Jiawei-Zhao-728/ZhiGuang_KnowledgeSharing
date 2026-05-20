package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KnowPostServiceImplTest {

    @Test
    void getDetailRejectsNonOwnerReadingPrivateDetailFromLocalCache() {
        Cache<String, KnowPostDetailResponse> localCache = Caffeine.newBuilder().build();
        localCache.put("knowpost:detail:42:v1", new KnowPostDetailResponse(
                "42",
                "private title",
                "private description",
                "https://oss.example/private.md",
                List.of(),
                List.of(),
                "100",
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
        KnowPostServiceImpl service = newService(localCache);

        assertThatThrownBy(() -> service.getDetail(42L, 200L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    private KnowPostServiceImpl newService(Cache<String, KnowPostDetailResponse> localCache) {
        return new KnowPostServiceImpl(
                mock(KnowPostMapper.class),
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                mock(OssProperties.class),
                mock(CounterService.class),
                mock(UserCounterService.class),
                mock(StringRedisTemplate.class),
                localCache,
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }
}
