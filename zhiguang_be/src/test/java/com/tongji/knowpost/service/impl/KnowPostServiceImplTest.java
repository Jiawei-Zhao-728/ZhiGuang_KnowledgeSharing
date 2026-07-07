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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    @Test
    void ownerOnlyDetailIsNotStoredInSharedCache() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        CounterService counterService = mock(CounterService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(mapper.findDetailById(42L)).thenReturn(privateRow());
        when(counterService.getCounts(eq("knowpost"), eq("42"), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 1L, "fav", 2L));

        KnowPostServiceImpl service = newService(mapper, counterService, redis, cache);

        KnowPostDetailResponse ownerResponse = service.getDetail(42L, 7L);

        assertThat(ownerResponse.visible()).isEqualTo("private");
        assertThat(cache.getIfPresent("knowpost:detail:42:v2")).isNull();
        verify(valueOperations, never()).set(eq("knowpost:detail:42:v2"), anyString(), any());

        assertThatThrownBy(() -> service.getDetail(42L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    private KnowPostServiceImpl newService(KnowPostMapper mapper,
                                           CounterService counterService,
                                           StringRedisTemplate redis,
                                           Cache<String, KnowPostDetailResponse> cache) {
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

    private KnowPostDetailRow privateRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(7L);
        row.setTitle("private title");
        row.setDescription("private description");
        row.setContentUrl("https://example.test/private.md");
        row.setTags("[\"secret\"]");
        row.setImgUrls("[\"https://example.test/image.png\"]");
        row.setAuthorAvatar("avatar");
        row.setAuthorNickname("author");
        row.setAuthorTagJson("{}");
        row.setPublishTime(Instant.now());
        row.setIsTop(false);
        row.setVisible("private");
        row.setType("image_text");
        row.setStatus("published");
        return row;
    }
}
