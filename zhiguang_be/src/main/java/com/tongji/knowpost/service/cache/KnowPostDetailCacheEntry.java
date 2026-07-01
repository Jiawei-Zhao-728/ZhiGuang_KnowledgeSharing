package com.tongji.knowpost.service.cache;

import java.time.Instant;
import java.util.List;

/**
 * Cache-only representation of a knowpost detail response.
 *
 * <p>The public API response intentionally omits {@code status}, but cache-hit
 * authorization needs it to distinguish public published posts from drafts that
 * happen to have {@code visible=public}.</p>
 */
public record KnowPostDetailCacheEntry(
        String id,
        String title,
        String description,
        String contentUrl,
        List<String> images,
        List<String> tags,
        Long authorId,
        String authorAvatar,
        String authorNickname,
        String authorTagJson,
        Long likeCount,
        Long favoriteCount,
        Boolean isTop,
        String visible,
        String type,
        Instant publishTime,
        String status
) {}
