package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.common.exception.BusinessException;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    private KnowPostMapper mapper;
    private CounterService counterService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(KnowPostMapper.class);
        counterService = mock(CounterService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        HotKeyDetector hotKey = mock(HotKeyDetector.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(counterService.getCounts(eq("knowpost"), anyString(), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 0L, "fav", 0L));
        when(hotKey.ttlForPublic(anyInt(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        OssProperties ossProperties = new OssProperties();
        ossProperties.setBucket("bucket");
        ossProperties.setEndpoint("oss.example.com");

        service = new KnowPostServiceImpl(
                mapper,
                mock(SnowflakeIdGenerator.class),
                new ObjectMapper(),
                ossProperties,
                counterService,
                mock(UserCounterService.class),
                redis,
                Caffeine.newBuilder().build(),
                hotKey,
                mock(RagIndexService.class),
                mock(OutboxMapper.class)
        );
    }

    @Test
    void ownerOnlyDetailIsNotStoredInSharedCache() {
        long postId = 99L;
        when(mapper.findDetailById(postId)).thenReturn(detailRow(postId, 7L, "draft", "public", null));

        service.getDetail(postId, 7L);

        assertThatThrownBy(() -> service.getDetail(postId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
        verify(mapper, times(2)).findDetailById(postId);
        verify(valueOps, never()).set(eq("knowpost:detail:99:v2"), anyString(), any(java.time.Duration.class));
    }

    @Test
    void publishedPublicDetailCanUseSharedCache() {
        long postId = 42L;
        when(mapper.findDetailById(postId)).thenReturn(detailRow(postId, 7L, "published", "public", Instant.now()));

        service.getDetail(postId, null);
        service.getDetail(postId, null);

        verify(mapper, times(1)).findDetailById(postId);
        verify(valueOps, times(1)).set(eq("knowpost:detail:42:v2"), anyString(), any(java.time.Duration.class));
    }

    private KnowPostDetailRow detailRow(long id, long creatorId, String status, String visible, Instant publishTime) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(id);
        row.setCreatorId(creatorId);
        row.setStatus(status);
        row.setVisible(visible);
        row.setPublishTime(publishTime);
        row.setType("image_text");
        row.setTitle("private draft");
        row.setDescription("secret");
        row.setContentUrl("https://oss.example.com/secret.md");
        row.setTags("[\"tag\"]");
        row.setImgUrls("[]");
        return row;
    }
}
