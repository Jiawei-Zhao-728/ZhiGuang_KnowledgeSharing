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
import com.tongji.knowpost.model.KnowPost;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    @Mock
    private KnowPostMapper mapper;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private CounterService counterService;
    @Mock
    private UserCounterService userCounterService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HotKeyDetector hotKeyDetector;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setPublicDomain("https://cdn.example");
        Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
                mapper,
                idGenerator,
                new ObjectMapper(),
                ossProperties,
                counterService,
                userCounterService,
                redis,
                cache,
                hotKeyDetector,
                ragIndexService,
                outboxMapper
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "posts/99/content.md",
            "posts/42/content./../../99/content.md",
            "posts/42/images/private.md"
    })
    void confirmContentRejectsKeysOutsideThePostContentPath(String objectKey) {
        assertThatThrownBy(() -> service.confirmContent(7L, 42L, objectKey, "etag", 10L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("内容对象路径非法");

        verify(mapper, never()).updateContent(any());
        verify(redis, never()).delete(any(String.class));
    }

    @Test
    void confirmContentAcceptsItsOwnGeneratedContentKey() {
        when(mapper.updateContent(any())).thenReturn(1);

        service.confirmContent(7L, 42L, "posts/42/content.md", "etag", 10L, "sha");

        ArgumentCaptor<KnowPost> postCaptor = ArgumentCaptor.forClass(KnowPost.class);
        verify(mapper).updateContent(postCaptor.capture());
        assertThat(postCaptor.getValue().getContentObjectKey()).isEqualTo("posts/42/content.md");
        assertThat(postCaptor.getValue().getContentUrl()).isEqualTo("https://cdn.example/posts/42/content.md");
        verify(ragIndexService).ensureIndexed(42L);
    }
}
