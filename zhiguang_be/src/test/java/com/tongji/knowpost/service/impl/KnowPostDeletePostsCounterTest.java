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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostDeletePostsCounterTest {

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
    private ValueOperations<String, String> valueOps;
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

    @Test
    void softDeleteSqlRejectsAlreadyDeletedRows() throws Exception {
        String xml;
        try (InputStream in = KnowPostMapper.class.getResourceAsStream("/mapper/KnowPostMapper.xml")) {
            assertThat(in).isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int start = xml.indexOf("<update id=\"softDelete\">");
        int end = xml.indexOf("</update>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);

        String softDeleteSql = xml.substring(start, end);
        assertThat(softDeleteSql).contains("status <> 'deleted'");
    }

    @Test
    void deletePublishedPostDecrementsPostsCounter() {
        KnowPost published = KnowPost.builder()
                .id(42L)
                .creatorId(7L)
                .status("published")
                .build();
        when(mapper.findById(42L)).thenReturn(published);
        when(mapper.softDelete(42L, 7L)).thenReturn(1);
        when(redis.delete(anyString())).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(99L);

        service.delete(7L, 42L);

        verify(userCounterService).incrementPosts(7L, -1);
        verify(outboxMapper).insert(eq(99L), eq("knowpost"), eq(42L), eq("KnowPostDeleted"), anyString());
    }

    @Test
    void deleteDraftDoesNotDecrementPostsCounter() {
        KnowPost draft = KnowPost.builder()
                .id(42L)
                .creatorId(7L)
                .status("draft")
                .build();
        when(mapper.findById(42L)).thenReturn(draft);
        when(mapper.softDelete(42L, 7L)).thenReturn(1);
        when(redis.delete(anyString())).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(99L);

        service.delete(7L, 42L);

        verify(userCounterService, never()).incrementPosts(anyLong(), anyInt());
    }

    @Test
    void deleteAlreadyDeletedDoesNotDecrementPostsCounter() {
        KnowPost deleted = KnowPost.builder()
                .id(42L)
                .creatorId(7L)
                .status("deleted")
                .build();
        when(mapper.findById(42L)).thenReturn(deleted);
        when(redis.delete(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L, 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("草稿不存在或无权限");

        verify(mapper, never()).softDelete(anyLong(), anyLong());
        verify(userCounterService, never()).incrementPosts(anyLong(), anyInt());
        verify(outboxMapper, never()).insert(anyLong(), anyString(), anyLong(), anyString(), anyString());
    }
}
