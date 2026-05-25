package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.cache.config.CacheProperties;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    private OssProperties ossProperties;
    @Mock
    private CounterService counterService;
    @Mock
    private UserCounterService userCounterService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private Cache<String, KnowPostDetailResponse> cache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        service = new KnowPostServiceImpl(
                mapper,
                idGen,
                new ObjectMapper(),
                ossProperties,
                counterService,
                userCounterService,
                redis,
                cache,
                new HotKeyDetector(new CacheProperties()),
                ragIndexService,
                outboxMapper
        );
    }

    @Test
    void cachedPrivateDetailIsNotReturnedToAnonymousUsers() {
        cache.put("knowpost:detail:42:v1", detailResponse("42", "7", "private", Instant.now()));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper);
    }

    @Test
    void ownerOnlyDetailFromDatabaseIsNotStoredInSharedCaches() {
        String pageKey = "knowpost:detail:42:v1";
        when(valueOperations.get(pageKey)).thenReturn(null);
        when(mapper.findDetailById(42L)).thenReturn(privatePublishedRow());
        when(counterService.getCounts("knowpost", "42", List.of("like", "fav")))
                .thenReturn(Map.of("like", 1L, "fav", 2L));

        KnowPostDetailResponse response = service.getDetail(42L, 7L);

        assertThat(response.id()).isEqualTo("42");
        assertThat(response.visible()).isEqualTo("private");
        assertThat(cache.getIfPresent(pageKey)).isNull();
        verify(valueOperations, never()).set(eq(pageKey), anyString(), any(Duration.class));
    }

    private KnowPostDetailRow privatePublishedRow() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(7L);
        row.setTitle("private title");
        row.setDescription("private description");
        row.setContentUrl("https://oss.example/posts/42/content.md");
        row.setAuthorNickname("author");
        row.setPublishTime(Instant.now());
        row.setIsTop(false);
        row.setVisible("private");
        row.setType("image_text");
        row.setStatus("published");
        return row;
    }

    private KnowPostDetailResponse detailResponse(String id, String authorId, String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                id,
                "title",
                "description",
                "https://oss.example/posts/" + id + "/content.md",
                List.of(),
                List.of(),
                authorId,
                null,
                "author",
                null,
                0L,
                0L,
                null,
                null,
                false,
                visible,
                "image_text",
                publishTime
        );
    }
}
