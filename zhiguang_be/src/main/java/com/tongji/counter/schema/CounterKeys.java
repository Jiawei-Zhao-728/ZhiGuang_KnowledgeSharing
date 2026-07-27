package com.tongji.counter.schema;

/**
 * Redis Key 生成工具。
 */
public final class CounterKeys {
    private CounterKeys() {}

    public static String sdsKey(String entityType, String entityId) {
        return String.format("cnt:%s:%s:%s", CounterSchema.SCHEMA_ID, entityType, entityId); // 固定结构计数（SDS）键
    }

    // 分片键：bm:{metric}:{etype}:{eid}:{chunk}
    public static String bitmapKey(String metric, String entityType, String entityId, long chunk) {
        return String.format("bm:%s:%s:%s:%d", metric, entityType, entityId, chunk); // 位图事实层（分片）
    }

    // 聚合增量持久化桶（Hash）：agg:{schema}:{etype}:{eid}
    public static String aggKey(String entityType, String entityId) {
        return String.format("agg:%s:%s:%s", CounterSchema.SCHEMA_ID, entityType, entityId); // 刷写前的增量存储桶
    }

    // SDS 重建世代：重建时递增，用于丢弃重建前已计入位图的过期聚合事件
    public static String epochKey(String entityType, String entityId) {
        return String.format("cnt:epoch:%s:%s", entityType, entityId);
    }

    public static String rebuildLockKey(String entityType, String entityId) {
        return String.format("lock:sds-rebuild:%s:%s", entityType, entityId);
    }
}