package com.tongji.relation.processor;

import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关系事件处理器。
 * 职责：对 FollowCreated/FollowCanceled 事件进行去重、防抖与幂等处理，落库更新粉丝表，维护关注/粉丝 ZSet 缓存与 TTL，并原子更新用户维度计数（SDS）。
 */
@Service
public class RelationEventProcessor {
    private final RelationMapper mapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> applyEventScript;

    public RelationEventProcessor(RelationMapper mapper, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.redis = redis;
        this.applyEventScript = new DefaultRedisScript<>(APPLY_EVENT_LUA, Long.class);
    }

    /**
     * 处理关系事件：入库、更新缓存、刷新计数，并进行幂等去重。
     * @param evt 关系事件
     * @param eventId outbox 事件 ID，用于区分同一用户对的多次关注状态变化
     */
    public void process(RelationEvent evt, String eventId) {
        if (!"FollowCreated".equals(evt.type()) && !"FollowCanceled".equals(evt.type())) {
            return;
        }

        if (eventId == null || eventId.isBlank() || evt.fromUserId() == null || evt.toUserId() == null) {
            throw new IllegalArgumentException("Relation event is missing its identity or user IDs");
        }

        int delta;
        if ("FollowCreated".equals(evt.type())) {
            if (evt.id() == null) {
                throw new IllegalArgumentException("FollowCreated event is missing its relation ID");
            }
            // 异步插入粉丝表
            mapper.insertFollower(evt.id(), evt.toUserId(), evt.fromUserId(), 1);
            delta = 1;
        } else {
            mapper.cancelFollower(evt.toUserId(), evt.fromUserId());
            delta = -1;
        }

        /*
         * The follower-table mutation is idempotent. All Redis side effects and the
         * event marker must be one operation: if the client times out after Redis
         * commits, a redelivery observes the marker; if Redis aborts, none of the
         * counters/cache entries were changed and the event can safely be retried.
         */
        redis.execute(
                applyEventScript,
                List.of(
                        "dedup:rel:event:" + eventId,
                        "uf:flws:" + evt.fromUserId(),
                        "uf:fans:" + evt.toUserId(),
                        "ucnt:" + evt.fromUserId(),
                        "ucnt:" + evt.toUserId()
                ),
                String.valueOf(evt.toUserId()),
                String.valueOf(evt.fromUserId()),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(delta)
        );
    }

    private static final String APPLY_EVENT_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return 0
            end

            local delta = tonumber(ARGV[4])
            if delta == 1 then
              redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
              redis.call('ZADD', KEYS[3], ARGV[3], ARGV[2])
            else
              redis.call('ZREM', KEYS[2], ARGV[1])
              redis.call('ZREM', KEYS[3], ARGV[2])
            end
            redis.call('EXPIRE', KEYS[2], 7200)
            redis.call('EXPIRE', KEYS[3], 7200)

            local function write32be(n)
              local t = {}
              for i=4,1,-1 do
                t[i] = n % 256
                n = math.floor(n / 256)
              end
              return string.char(unpack(t))
            end

            local function updateCounter(key, idx)
              local cnt = redis.call('GET', key)
              if not cnt or string.len(cnt) ~= 20 then
                cnt = string.rep(string.char(0), 20)
              end
              local off = (idx - 1) * 4
              local b = {string.byte(cnt, off + 1, off + 4)}
              local value = 0
              for i=1,4 do
                value = value * 256 + b[i]
              end
              value = value + delta
              if value < 0 then value = 0 end
              local segment = write32be(value)
              redis.call('SET', key, string.sub(cnt, 1, off) .. segment .. string.sub(cnt, off + 5))
            end

            updateCounter(KEYS[4], 1)
            updateCounter(KEYS[5], 2)
            redis.call('SET', KEYS[1], '1', 'EX', 604800)
            return 1
            """;
}
