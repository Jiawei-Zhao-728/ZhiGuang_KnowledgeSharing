package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.cache.hotkey.HotKeyDetector;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private Cache<String, KnowPostDetailResponse> knowPostDetailCache;
    @Mock
    private HotKeyDetector hotKey;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
                new OssProperties(),
                counterService,
                userCounterService,
                redis,
                knowPostDetailCache,
                hotKey,
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void updateVisibilityWritesSearchOutboxEvent() throws Exception {
        when(mapper.updateVisibility(42L, 7L, "private")).thenReturn(1);
        when(idGen.nextId()).thenReturn(99L);

        service.updateVisibility(7L, 42L, "private");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).insert(
                eq(99L),
                eq("knowpost"),
                eq(42L),
                eq("KnowPostVisibilityUpdated"),
                payloadCaptor.capture()
        );
        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("entity").asText()).isEqualTo("knowpost");
        assertThat(payload.get("op").asText()).isEqualTo("upsert");
        assertThat(payload.get("id").asLong()).isEqualTo(42L);
    }
}
