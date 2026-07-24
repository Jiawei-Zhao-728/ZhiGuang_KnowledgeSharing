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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowPostPublishGuardTest {

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
    void publishSqlRequiresDraftStatus() throws Exception {
        String xml;
        try (InputStream in = KnowPostMapper.class.getResourceAsStream("/mapper/KnowPostMapper.xml")) {
            assertThat(in).isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int publishStart = xml.indexOf("<update id=\"publish\">");
        int publishEnd = xml.indexOf("</update>", publishStart);
        assertThat(publishStart).isGreaterThanOrEqualTo(0);
        assertThat(publishEnd).isGreaterThan(publishStart);

        String publishSql = xml.substring(publishStart, publishEnd);
        assertThat(publishSql)
                .contains("AND status = 'draft'")
                .doesNotContain("status = 'deleted'");
    }

    @Test
    void publishDoesNotResurrectWhenMapperRejectsNonDraft() {
        when(mapper.publish(42L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.publish(7L, 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("草稿不存在或无权限");

        verify(userCounterService, never()).incrementPosts(anyLong(), anyLong());
        verify(outboxMapper, never()).insert(anyLong(), anyString(), anyLong(), anyString(), anyString());
        verify(ragIndexService, never()).ensureIndexed(anyLong());
    }
}
