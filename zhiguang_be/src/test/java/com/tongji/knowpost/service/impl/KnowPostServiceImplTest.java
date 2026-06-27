package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import com.tongji.knowpost.model.KnowPostDetailRow;
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
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HotKeyDetector hotKey;
    @Mock
    private RagIndexService ragIndexService;
    @Mock
    private OutboxMapper outboxMapper;

    private ObjectMapper objectMapper;
    private Cache<String, KnowPostDetailResponse> detailCache;
    private KnowPostServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        detailCache = Caffeine.newBuilder().build();

        OssProperties ossProperties = new OssProperties();
        ossProperties.setPublicDomain("https://cdn.example.test");

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
    void cachedPrivateDetailIsDeniedToAnonymousUser() throws Exception {
        String cacheKey = "knowpost:detail:42:v1";
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(objectMapper.writeValueAsString(
                detailResponse("42", "7", "private", Instant.parse("2026-06-27T10:00:00Z"))
        ));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper);
        verify(hotKey, never()).record(anyString());
    }

    @Test
    void cachedPublicDraftDetailIsDeniedToAnonymousUser() throws Exception {
        String cacheKey = "knowpost:detail:42:v1";
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(objectMapper.writeValueAsString(
                detailResponse("42", "7", "public", null)
        ));

        assertThatThrownBy(() -> service.getDetail(42L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verifyNoInteractions(mapper);
        verify(hotKey, never()).record(anyString());
    }

    @Test
    void cachedPrivateDetailIsReturnedToAuthor() throws Exception {
        String cacheKey = "knowpost:detail:42:v1";
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(objectMapper.writeValueAsString(
                detailResponse("42", "7", "private", Instant.parse("2026-06-27T10:00:00Z"))
        ));
        when(redis.getExpire(anyString())).thenReturn(0L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(hotKey.ttlForPublic(anyInt(), anyString())).thenReturn(60);
        when(counterService.getCounts(eq("knowpost"), eq("42"), eq(List.of("like", "fav"))))
                .thenReturn(Map.of("like", 3L, "fav", 2L));

        KnowPostDetailResponse response = service.getDetail(42L, 7L);

        assertThat(response.contentUrl()).isEqualTo("https://cdn.example.test/posts/42/content.md");
        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.favoriteCount()).isEqualTo(2L);
        verifyNoInteractions(mapper);
    }

    @Test
    void privateDetailLoadedFromDatabaseIsNotWrittenToSharedCache() {
        String cacheKey = "knowpost:detail:42:v1";
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(mapper.findDetailById(42L)).thenReturn(detailRow("private", "published", 7L));
        when(counterService.getCounts(eq("knowpost"), eq("42"), eq(List.of("like", "fav"))))
                .thenReturn(Collections.emptyMap());

        KnowPostDetailResponse response = service.getDetail(42L, 7L);

        assertThat(response.visible()).isEqualTo("private");
        verify(valueOperations, never()).set(eq(cacheKey), anyString(), any(Duration.class));
        assertThat(detailCache.getIfPresent(cacheKey)).isNull();
    }

    @Test
    void confirmContentRejectsObjectKeyFromAnotherPost() {
        assertThatThrownBy(() -> service.confirmContent(
                7L,
                42L,
                "posts/99/content.md",
                "etag",
                10L,
                "sha256"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("objectKey 非法");

        verify(mapper, never()).updateContent(any(KnowPost.class));
        verifyNoInteractions(ragIndexService);
    }

    @Test
    void confirmContentAcceptsOwnContentObjectKey() {
        when(mapper.updateContent(any(KnowPost.class))).thenReturn(1);

        service.confirmContent(7L, 42L, "posts/42/content.md", "etag", 10L, "sha256");

        ArgumentCaptor<KnowPost> captor = ArgumentCaptor.forClass(KnowPost.class);
        verify(mapper).updateContent(captor.capture());
        KnowPost updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(42L);
        assertThat(updated.getCreatorId()).isEqualTo(7L);
        assertThat(updated.getContentObjectKey()).isEqualTo("posts/42/content.md");
        assertThat(updated.getContentUrl()).isEqualTo("https://cdn.example.test/posts/42/content.md");
    }

    private KnowPostDetailResponse detailResponse(String id, String authorId, String visible, Instant publishTime) {
        return new KnowPostDetailResponse(
                id,
                "title",
                "description",
                "https://cdn.example.test/posts/" + id + "/content.md",
                List.of(),
                List.of(),
                authorId,
                "avatar",
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

    private KnowPostDetailRow detailRow(String visible, String status, Long creatorId) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setCreatorId(creatorId);
        row.setTitle("title");
        row.setDescription("description");
        row.setContentUrl("https://cdn.example.test/posts/42/content.md");
        row.setAuthorAvatar("avatar");
        row.setAuthorNickname("author");
        row.setIsTop(false);
        row.setVisible(visible);
        row.setType("image_text");
        row.setStatus(status);
        row.setPublishTime(Instant.parse("2026-06-27T10:00:00Z"));
        return row;
    }
}
