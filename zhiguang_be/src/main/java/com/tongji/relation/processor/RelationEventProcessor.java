package com.tongji.relation.processor;

import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.tongji.counter.service.UserCounterService;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 关系事件处理器。
 * 职责：对 FollowCreated/FollowCanceled 事件进行去重、防抖与幂等处理，落库更新粉丝表，失效关注/粉丝列表缓存，并原子更新用户维度计数（SDS）。
 */
@Service
public class RelationEventProcessor {
    private final RelationMapper mapper;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;

    public RelationEventProcessor(RelationMapper mapper, StringRedisTemplate redis, UserCounterService userCounterService) {
        this.mapper = mapper;
        this.redis = redis;
        this.userCounterService = userCounterService;
    }

    /**
     * 处理关系事件：入库、更新缓存、刷新计数，并进行幂等去重。
     * @param evt 关系事件
     */
    public void process(RelationEvent evt) {
        String dk = "dedup:rel:" + evt.type() + ":" + evt.fromUserId() + ":" + evt.toUserId() + ":" + (evt.id() == null ? "0" : String.valueOf(evt.id()));
        Boolean first = redis.opsForValue().setIfAbsent(dk, "1", Duration.ofMinutes(10));

        // 非首次（存在去重键）直接返回，保证消息幂等
        if (first == null || !first) {
            return;
        }
        if ("FollowCreated".equals(evt.type())) {
            // 异步插入粉丝表
            mapper.insertFollower(evt.id(), evt.toUserId(), evt.fromUserId(), 1);
            // 只失效、不写穿：读路径把非空 ZSet 当成完整列表。冷缓存上 ZADD/ZREM
            // 会种下一份残缺集合，历史关注/粉丝在 TTL 内被吞掉。
            invalidateListCaches(evt.fromUserId(), evt.toUserId());

            // 更新关注数与粉丝数
            userCounterService.incrementFollowings(evt.fromUserId(), 1);
            userCounterService.incrementFollowers(evt.toUserId(), 1);
        } else if ("FollowCanceled".equals(evt.type())) {
            mapper.cancelFollower(evt.toUserId(), evt.fromUserId());
            invalidateListCaches(evt.fromUserId(), evt.toUserId());

            // 更新关注数与粉丝数
            userCounterService.incrementFollowings(evt.fromUserId(), -1);
            userCounterService.incrementFollowers(evt.toUserId(), -1);
        }
    }

    /**
     * Drop follow/follower list caches so the next read backfills from MySQL.
     */
    private void invalidateListCaches(long fromUserId, long toUserId) {
        redis.delete("uf:flws:" + fromUserId);
        redis.delete("uf:fans:" + toUserId);
    }
}
