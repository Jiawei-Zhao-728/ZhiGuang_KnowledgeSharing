package com.tongji.storage;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageObjectKeysTest {

    private static final Pattern CONTENT_KEY =
            Pattern.compile("posts/1920000000000000123/[0-9a-f]{32}/content\\.md");

    @Test
    void contentKeyIsNotDeterministicFromPostId() {
        long postId = 1920000000000000123L;

        String a = StorageObjectKeys.knowpostContentKey(postId, ".md");
        String b = StorageObjectKeys.knowpostContentKey(postId, ".md");

        assertTrue(CONTENT_KEY.matcher(a).matches(), a);
        assertTrue(CONTENT_KEY.matcher(b).matches(), b);
        assertNotEquals(a, b);
        assertFalse(a.equals("posts/" + postId + "/content.md"));
        assertFalse(a.startsWith("posts/" + postId + "/content"));
    }

    @Test
    void contentKeysDoNotCollideAcrossManyDraws() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            keys.add(StorageObjectKeys.knowpostContentKey(1L, ".md"));
        }
        assertEquals(200, keys.size());
    }

    @Test
    void imageKeyKeepsRandomSegment() {
        String key = StorageObjectKeys.knowpostImageKey(99L, "20260826", "abcd1234", ".png");
        assertEquals("posts/99/images/20260826/abcd1234.png", key);
    }
}
