package com.tongji.counter.event;

import lombok.Data;

/**
 * 计数事件模型。
 *
 * <p>用于描述一次状态变化导致的计数增量（如点赞 +1 / 取消点赞 -1），
 * 由生产者发送到 Kafka，消费者聚合后折叠到汇总计数。</p>
 */
@Data
public class CounterEvent {
    private String entityType;
    private String entityId;
    private String metric; // like | fav（指标名称）
    private int idx; // schema index（见 CounterSchema.NAME_TO_IDX）
    private long userId;
    private int delta; // +1 / -1
    /**
     * SDS 重建世代。事件产生时读取；若消费时当前世代更大，说明位图重建已覆盖该增量，应丢弃。
     */
    private long epoch;

    public CounterEvent() {
    }

    public CounterEvent(String entityType, String entityId, String metric, int idx, long userId, int delta) {
        this(entityType, entityId, metric, idx, userId, delta, 0L);
    }

    public CounterEvent(String entityType, String entityId, String metric, int idx, long userId, int delta, long epoch) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.metric = metric;
        this.idx = idx;
        this.userId = userId;
        this.delta = delta;
        this.epoch = epoch;
    }

    public static CounterEvent of(String entityType, String entityId, String metric, int idx, long userId, int delta) {
        return of(entityType, entityId, metric, idx, userId, delta, 0L);
    }

    public static CounterEvent of(String entityType, String entityId, String metric, int idx, long userId, int delta, long epoch) {
        return new CounterEvent(entityType, entityId, metric, idx, userId, delta, epoch);
    }
}
