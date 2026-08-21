package com.tongji.knowpost.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Objects;

/**
 * 线程安全的雪花算法 ID 生成器。
 * 41 位时间戳 + 5 位数据中心 + 5 位工作节点 + 12 位序列。
 * <p>
 * 多实例部署时必须使用互不相同的 (datacenterId, workerId)。默认构造不再硬编码 (1,1)：
 * 优先读取 {@code zhiguang.snowflake.*}，否则按 hostname+pid 派生并在 Redis 中抢占节点租约。
 */
@Component
public class SnowflakeIdGenerator {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC

    static final long WORKER_ID_BITS = 5L;
    static final long DATACENTER_ID_BITS = 5L;
    static final long SEQUENCE_BITS = 12L;

    static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    static final int NODE_SLOT_COUNT = (int) ((MAX_DATACENTER_ID + 1) * (MAX_WORKER_ID + 1));

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    static final String NODE_LEASE_KEY_PREFIX = "zhiguang:snowflake:node:";
    private static final Duration NODE_LEASE_TTL = Duration.ofDays(7);

    private final long datacenterId;
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * 显式指定节点身份。测试与需要固定 ID 的场景使用。
     */
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        this.datacenterId = requireDatacenterId(datacenterId);
        this.workerId = requireWorkerId(workerId);
    }

    /**
     * 生产构造：配置项优先，否则自动分配互不冲突的节点号。
     * {@code datacenter-id}/{@code worker-id} 为 -1 表示未配置，走自动分配。
     */
    @Autowired
    public SnowflakeIdGenerator(
            ObjectProvider<StringRedisTemplate> redis,
            @Value("${zhiguang.snowflake.datacenter-id:-1}") long datacenterId,
            @Value("${zhiguang.snowflake.worker-id:-1}") long workerId
    ) {
        if (datacenterId >= 0 && workerId >= 0) {
            this.datacenterId = requireDatacenterId(datacenterId);
            this.workerId = requireWorkerId(workerId);
            log.info("Snowflake node from config datacenterId={} workerId={}", this.datacenterId, this.workerId);
            return;
        }
        long[] assigned = assignUniqueNode(redis == null ? null : redis.getIfAvailable());
        this.datacenterId = assigned[0];
        this.workerId = assigned[1];
        log.info("Snowflake node auto-assigned datacenterId={} workerId={}", this.datacenterId, this.workerId);
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    public long extractDatacenterId(long id) {
        return (id >>> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    public long extractWorkerId(long id) {
        return (id >>> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;

            // 1. 小幅度回拨（比如 NTP 校时导致的 1~5ms 间抖动）：等待一会儿再试
            if (offset <= 5) {
                try {
                    Thread.sleep(offset);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Thread interrupted while waiting for clock to catch up", e);
                }

                timestamp = currentTime();
                if (timestamp < lastTimestamp) {
                    throw new IllegalStateException(
                            "Clock is still behind after waiting. last=" + lastTimestamp + ", now=" + timestamp);
                }
            } else {
                throw new IllegalStateException(
                        "Clock moved backwards too much. Refusing to generate id. offset=" + offset + "ms");
            }
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    static long[] deriveNodeIds(String host, long pid) {
        int hash = Objects.hash(host, pid);
        int mixed = hash ^ (hash >>> 16);
        long datacenterId = (mixed >>> WORKER_ID_BITS) & MAX_DATACENTER_ID;
        long workerId = mixed & MAX_WORKER_ID;
        return new long[]{datacenterId, workerId};
    }

    static long[] nodeIdsFromSlot(long slot) {
        long normalized = Math.floorMod(slot, NODE_SLOT_COUNT);
        long datacenterId = normalized / (MAX_WORKER_ID + 1);
        long workerId = normalized % (MAX_WORKER_ID + 1);
        return new long[]{datacenterId, workerId};
    }

    static long slotOf(long datacenterId, long workerId) {
        return datacenterId * (MAX_WORKER_ID + 1) + workerId;
    }

    static String nodeLeaseKey(long datacenterId, long workerId) {
        return NODE_LEASE_KEY_PREFIX + datacenterId + ":" + workerId;
    }

    static long[] assignUniqueNode(StringRedisTemplate redis) {
        String host = localHostName();
        long pid = ProcessHandle.current().pid();
        return assignUniqueNode(redis, host, pid);
    }

    static long[] assignUniqueNode(StringRedisTemplate redis, String host, long pid) {
        long[] derived = deriveNodeIds(host, pid);
        if (redis == null) {
            return derived;
        }
        String instanceId = host + ":" + pid;
        long start = slotOf(derived[0], derived[1]);
        for (int i = 0; i < NODE_SLOT_COUNT; i++) {
            long[] ids = nodeIdsFromSlot(start + i);
            String key = nodeLeaseKey(ids[0], ids[1]);
            try {
                String existing = redis.opsForValue().get(key);
                if (instanceId.equals(existing)) {
                    redis.expire(key, NODE_LEASE_TTL);
                    return ids;
                }
                Boolean claimed = redis.opsForValue().setIfAbsent(key, instanceId, NODE_LEASE_TTL);
                if (Boolean.TRUE.equals(claimed)) {
                    return ids;
                }
            } catch (RuntimeException e) {
                log.warn("Snowflake Redis node assignment failed, using derived ids: {}", e.getMessage());
                return derived;
            }
        }
        log.warn("Snowflake node slots exhausted, using derived ids datacenterId={} workerId={}",
                derived[0], derived[1]);
        return derived;
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static long requireWorkerId(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        return workerId;
    }

    private static long requireDatacenterId(long datacenterId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        return datacenterId;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
