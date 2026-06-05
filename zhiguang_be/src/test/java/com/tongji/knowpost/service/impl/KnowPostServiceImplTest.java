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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostServiceImplTest {

    private static final long POST_ID = 42L;
    private static final String DETAIL_CACHE_KEY = "knowpost:detail:" + POST_ID + ":v1";

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
        ossProperties.setPublicDomain("https://cdn.example.com");
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
    void cachedDraftDetailIsNotServedToAnonymousUsers() {
        detailCache.put(DETAIL_CACHE_KEY, cachedDetail("public"));
        when(mapper.findDetailById(POST_ID)).thenReturn(detailRow("draft", "public", 100L));

        assertThatThrownBy(() -> service.getDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(redis).delete(DETAIL_CACHE_KEY);
    }

    @Test
    void confirmContentRejectsObjectKeysForOtherPosts() {
        assertThatThrownBy(() -> service.confirmContent(100L, POST_ID, "posts/99/content.md", "\"etag\"", 10L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("objectKey 与知文不匹配");

        verify(mapper, never()).updateContent(any());
    }

    private KnowPostDetailResponse cachedDetail(String visible) {
        return new KnowPostDetailResponse(
                String.valueOf(POST_ID),
                "title",
                "description",
                "https://cdn.example.com/posts/" + POST_ID + "/content.md",
                List.of(),
                List.of(),
                "100",
                "avatar",
                "author",
                "[]",
                0L,
                0L,
                null,
                null,
                false,
                visible,
                "image_text",
                Instant.now()
        );
    }

    private KnowPostDetailRow detailRow(String status, String visible, Long creatorId) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setCreatorId(creatorId);
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
