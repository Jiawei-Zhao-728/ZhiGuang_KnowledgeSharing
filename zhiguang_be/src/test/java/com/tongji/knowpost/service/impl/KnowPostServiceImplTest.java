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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    private ObjectMapper objectMapper;
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
        OssProperties ossProperties = new OssProperties();
        ossProperties.setPublicDomain("https://cdn.example.test");
        detailCache = Caffeine.newBuilder().build();
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                objectMapper,
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
    void cachedPrivateDetailIsNotReturnedToAnonymousUser() {
        long postId = 42L;
        detailCache.put("knowpost:detail:" + postId + ":v1", new KnowPostDetailResponse(
                String.valueOf(postId),
                "private title",
                "private description",
                "https://cdn.example.test/posts/42/content.md",
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
        ));

        assertThrows(BusinessException.class, () -> service.getDetail(postId, null));
        verifyNoInteractions(mapper, counterService, hotKey);
    }

    @Test
    void confirmContentRejectsObjectKeyForAnotherPost() {
        assertThrows(BusinessException.class,
                () -> service.confirmContent(7L, 42L, "posts/99/content.md", "etag", 12L, "sha256"));

        verifyNoInteractions(mapper, redis, ragIndexService);
    }

    @Test
    void confirmContentAcceptsObjectKeyForSamePost() {
        when(mapper.updateContent(any(KnowPost.class))).thenReturn(1);

        service.confirmContent(7L, 42L, "posts/42/content.md", "etag", 12L, "sha256");

        ArgumentCaptor<KnowPost> postCaptor = ArgumentCaptor.forClass(KnowPost.class);
        verify(mapper).updateContent(postCaptor.capture());
        KnowPost post = postCaptor.getValue();
        assertThat(post.getId()).isEqualTo(42L);
        assertThat(post.getCreatorId()).isEqualTo(7L);
        assertThat(post.getContentObjectKey()).isEqualTo("posts/42/content.md");
        assertThat(post.getContentUrl()).isEqualTo("https://cdn.example.test/posts/42/content.md");
    }
}
