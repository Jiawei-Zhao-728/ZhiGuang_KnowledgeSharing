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
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowPostServiceImplTest {

    private final KnowPostMapper mapper = mock(KnowPostMapper.class);
    private final CounterService counterService = mock(CounterService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final Cache<String, KnowPostDetailResponse> cache = Caffeine.newBuilder().build();
    private final HotKeyDetector hotKey = mock(HotKeyDetector.class);

    private final KnowPostServiceImpl service = new KnowPostServiceImpl(
            mapper,
            mock(SnowflakeIdGenerator.class),
            new ObjectMapper(),
            new OssProperties(),
            counterService,
            mock(UserCounterService.class),
            redis,
            cache,
            hotKey,
            mock(RagIndexService.class),
            mock(OutboxMapper.class)
    );

    @Test
    void cachedDraftDetailIsRejectedForAnonymousUsersAndEvicted() {
        long postId = 42L;
        String pageKey = "knowpost:detail:" + postId + ":v1";
        cache.put(pageKey, cachedDetail(postId, 10L, "public"));
        when(mapper.findDetailById(postId)).thenReturn(detailRow(postId, 10L, "draft", "public"));

        assertThatThrownBy(() -> service.getDetail(postId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");

        assertThat(cache.getIfPresent(pageKey)).isNull();
    }

    @Test
    void ownerDraftDetailIsNotWrittenToSharedCaches() {
        long postId = 42L;
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(mapper.findDetailById(postId)).thenReturn(detailRow(postId, 10L, "draft", "public"));
        when(counterService.getCounts("knowpost", String.valueOf(postId), List.of("like", "fav")))
                .thenReturn(Map.of("like", 0L, "fav", 0L));

        KnowPostDetailResponse response = service.getDetail(postId, 10L);

        assertThat(response.id()).isEqualTo(String.valueOf(postId));
        assertThat(cache.getIfPresent("knowpost:detail:" + postId + ":v1")).isNull();
    }

    private KnowPostDetailResponse cachedDetail(long postId, long creatorId, String visible) {
        return new KnowPostDetailResponse(
                String.valueOf(postId),
                "draft title",
                "draft description",
                "https://cdn.example.test/content.md",
                List.of(),
                List.of(),
                String.valueOf(creatorId),
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
                null
        );
    }

    private KnowPostDetailRow detailRow(long postId, long creatorId, String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(postId);
        row.setCreatorId(creatorId);
        row.setTitle("draft title");
        row.setDescription("draft description");
        row.setContentUrl("https://cdn.example.test/content.md");
        row.setAuthorNickname("author");
        row.setStatus(status);
        row.setVisible(visible);
        row.setType("image_text");
        row.setIsTop(false);
        return row;
    }
}
