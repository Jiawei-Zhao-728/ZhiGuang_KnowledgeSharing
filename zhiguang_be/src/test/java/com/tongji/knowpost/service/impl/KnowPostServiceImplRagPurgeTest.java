package com.tongji.knowpost.service.impl;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplRagPurgeTest {

    @Mock
    private KnowPostMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private OssProperties ossProperties;
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

    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
                ossProperties,
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
    void delete_purgesRagChunksForSoftDeletedPost() {
        when(mapper.softDelete(9L, 1L)).thenReturn(1);

        service.delete(1L, 9L);

        verify(ragIndexService).purgeChunks(9L);
    }

    @Test
    void updateVisibility_purgesRagChunksWhenPostBecomesPrivate() {
        when(mapper.updateVisibility(9L, 1L, "private")).thenReturn(1);

        service.updateVisibility(1L, 9L, "private");

        verify(ragIndexService).purgeChunks(9L);
    }

    @Test
    void updateVisibility_keepsChunksWhenPostStaysPublic() {
        when(mapper.updateVisibility(9L, 1L, "public")).thenReturn(1);

        service.updateVisibility(1L, 9L, "public");

        verify(ragIndexService, never()).purgeChunks(anyLong());
    }
}
