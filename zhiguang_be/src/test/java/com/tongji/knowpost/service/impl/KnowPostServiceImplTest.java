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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private CounterService counterService;
    @Mock
    private UserCounterService userCounterService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
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
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(counterService.getCounts(eq("knowpost"), anyString(), any()))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                new OssProperties(),
                counterService,
                userCounterService,
                redis,
                detailCache,
                hotKey,
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void ownerReadingPrivatePostDoesNotSeedSharedDetailCache() {
        KnowPostDetailRow privatePost = detailRow("private");
        when(mapper.findDetailById(42L)).thenReturn(privatePost);

        KnowPostDetailResponse ownerResponse = service.getDetail(42L, 7L);

        assertThat(ownerResponse.visible()).isEqualTo("private");
        assertThat(detailCache.asMap()).isEmpty();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
    }

    private KnowPostDetailRow detailRow(String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(7L);
        row.setTitle("private title");
        row.setDescription("private body");
        row.setContentUrl("https://example.test/content.md");
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus("published");
        return row;
    }
}
