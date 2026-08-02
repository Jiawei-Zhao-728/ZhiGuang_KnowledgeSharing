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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostContentConfirmGuardTest {

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
    void updateContentSqlRequiresDraftStatus() throws Exception {
        String xml;
        try (InputStream in = KnowPostMapper.class.getResourceAsStream("/mapper/KnowPostMapper.xml")) {
            assertThat(in).isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int start = xml.indexOf("<update id=\"updateContent\"");
        int end = xml.indexOf("</update>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);

        String sql = xml.substring(start, end);
        assertThat(sql).contains("AND status = 'draft'");
    }

    @Test
    void confirmContentDoesNotMutateWhenMapperRejectsNonDraft() {
        when(mapper.updateContent(any())).thenReturn(0);

        assertThatThrownBy(() ->
                service.confirmContent(7L, 42L, "posts/42/content.md", "etag", 10L, "sha"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("草稿不存在或无权限");

        verify(ragIndexService, never()).ensureIndexed(42L);
    }
}
