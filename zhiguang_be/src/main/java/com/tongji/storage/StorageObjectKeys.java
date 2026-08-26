package com.tongji.storage;

import java.util.UUID;

/**
 * OSS object-key helpers for knowpost uploads.
 *
 * <p>Content bodies are stored on a public-read bucket (the app fetches
 * {@code contentUrl} without credentials). Keys must therefore be
 * unguessable: a key of {@code posts/{postId}/content.md} lets anyone
 * who can mint a nearby snowflake ID read unpublished drafts.</p>
 */
public final class StorageObjectKeys {

    private StorageObjectKeys() {}

    /**
     * Content object key: {@code posts/{postId}/{unguessable}/content{ext}}.
     */
    public static String knowpostContentKey(long postId, String ext) {
        String token = UUID.randomUUID().toString().replace("-", "");
        return "posts/" + postId + "/" + token + "/content" + ext;
    }

    /**
     * Image object key: {@code posts/{postId}/images/{yyyyMMdd}/{rand}{ext}}.
     */
    public static String knowpostImageKey(long postId, String dateUtc, String rand, String ext) {
        return "posts/" + postId + "/images/" + dateUtc + "/" + rand + ext;
    }
}
