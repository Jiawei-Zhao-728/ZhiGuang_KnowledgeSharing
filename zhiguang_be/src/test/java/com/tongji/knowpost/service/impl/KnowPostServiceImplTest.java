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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        OssProperties ossProperties = new OssProperties();
        ossProperties.setBucket("bucket");
        ossProperties.setEndpoint("oss.example.com");

        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                ossProperties,
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
    void cachedPrivateDetailCannotBeReadByAnonymousUser() {
        detailCache.put("knowpost:detail:42:v1", privateDetail());

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper, counterService, hotKey);
    }

    @Test
    void confirmContentRejectsObjectKeyFromAnotherPost() {
        assertThatThrownBy(() -> service.confirmContent(7L, 42L, "posts/99/content.md", "etag", 12L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("objectKey 与知文不匹配");

        verify(mapper, never()).updateContent(any());
    }

    @Test
    void confirmContentRejectsObjectKeyWithNestedContentPath() {
        assertThatThrownBy(() -> service.confirmContent(7L, 42L, "posts/42/content.md/evil", "etag", 12L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("objectKey 与知文不匹配");

        verify(mapper, never()).updateContent(any());
    }

    @Test
    void cachedPrivateDetailCanStillBeReadByOwner() {
        detailCache.put("knowpost:detail:42:v1", privateDetail());
        when(counterService.getCounts("knowpost", "42", List.of("like", "fav"))).thenReturn(Map.of());

        service.getDetail(42L, 7L);

        verify(counterService).isLiked("knowpost", "42", 7L);
        verify(counterService).isFaved("knowpost", "42", 7L);
    }

    private KnowPostDetailResponse privateDetail() {
        return new KnowPostDetailResponse(
                "42",
                "private title",
                "private description",
                "https://bucket.oss.example.com/posts/42/content.md",
                List.of(),
                List.of(),
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
                Instant.now()
        );
    }
}
