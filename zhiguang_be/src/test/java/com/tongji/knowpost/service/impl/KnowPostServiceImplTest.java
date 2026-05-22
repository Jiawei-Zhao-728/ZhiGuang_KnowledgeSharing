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

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    @Test
    void ownerDraftDetailDoesNotPopulateSharedCache() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        CounterService counterService = mock(CounterService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        Cache<String, KnowPostDetailResponse> detailCache = Caffeine.newBuilder().build();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(mapper.findDetailById(42L)).thenReturn(draftRow());
        when(counterService.getCounts(anyString(), anyString(), any())).thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostServiceImpl service = new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                new OssProperties(),
                counterService,
                mock(UserCounterService.class),
                redis,
                detailCache,
                mock(HotKeyDetector.class),
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );

        KnowPostDetailResponse ownerResponse = service.getDetail(42L, 7L);

        assertThat(ownerResponse.id()).isEqualTo("42");
        assertThat(detailCache.asMap()).isEmpty();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
    }

    private KnowPostDetailRow draftRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(7L);
        row.setTitle("private draft");
        row.setDescription("draft body");
        row.setContentUrl("https://example.test/content.md");
        row.setVisible("public");
        row.setStatus("draft");
        row.setType("image_text");
        return row;
    }
}
