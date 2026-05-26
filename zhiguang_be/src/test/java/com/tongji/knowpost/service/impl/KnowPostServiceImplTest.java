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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final String DETAIL_KEY = "knowpost:detail:" + POST_ID + ":v1";

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
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private Cache<String, KnowPostDetailResponse> cache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
                ossProperties,
                counterService,
                userCounterService,
                redis,
                cache,
                hotKey,
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void localDetailCacheHitStillRejectsUnauthorizedPrivateContent() {
        cache.put(DETAIL_KEY, cachedDetail());
        when(mapper.findDetailById(POST_ID)).thenReturn(privateDetailRow());

        assertThatThrownBy(() -> service.getDetail(POST_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(mapper).findDetailById(POST_ID);
        verifyNoInteractions(counterService);
    }

    @Test
    void redisDetailCacheHitStillRejectsUnauthorizedPrivateContent() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(DETAIL_KEY)).thenReturn(objectMapper.writeValueAsString(cachedDetail()));
        when(mapper.findDetailById(POST_ID)).thenReturn(privateDetailRow());

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        verify(mapper).findDetailById(POST_ID);
        verifyNoInteractions(counterService);
    }

    private KnowPostDetailResponse cachedDetail() {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "private draft",
                "sensitive description",
                "https://example.test/private.html",
                Collections.emptyList(),
                List.of("secret"),
                "7",
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
                null
        );
    }

    private KnowPostDetailRow privateDetailRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(7L);
        row.setStatus("published");
        row.setVisible("private");
        return row;
    }
}
