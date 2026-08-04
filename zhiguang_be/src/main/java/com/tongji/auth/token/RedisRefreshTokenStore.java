package com.tongji.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

/**
 * 基于 Redis 的刷新令牌白名单存储。
 * <p>
 * 键空间：
 * - `auth:rt:{userId}:{tokenId}`：值是签发时的会话世代，TTL 控制过期；
 * - `auth:rt:epoch:{userId}`：用户会话世代；`revokeAll` 原子自增以使旧令牌全部失效。
 * <p>
 * 相比 KEYS+DELETE，世代失效可避免与并发 refresh/login 的竞态：在扫描与删除之间新写入的
 * jti 不会在密码重置后继续存活。
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String CONSUME_LUA = """
            local tokenKey = KEYS[1]
            local epochKey = KEYS[2]
            local value = redis.call('GETDEL', tokenKey)
            if not value then
              return nil
            end
            local epoch = redis.call('GET', epochKey)
            if not epoch then
              epoch = '0'
            end
            if value ~= epoch then
              return nil
            end
            return tonumber(value)
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> consumeScript;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>();
        this.consumeScript.setResultType(Long.class);
        this.consumeScript.setScriptText(CONSUME_LUA);
    }

    /**
     * 将刷新令牌写入白名单，绑定当前会话世代并设置过期时间。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     * @param ttl     生存时间（Redis TTL）。
     */
    @Override
    public void storeToken(long userId, String tokenId, Duration ttl) {
        storeToken(userId, tokenId, ttl, currentEpoch(userId));
    }

    /**
     * 以指定会话世代写入白名单（轮换时沿用已校验世代，防止越过 revokeAll）。
     */
    @Override
    public void storeToken(long userId, String tokenId, Duration ttl, long epoch) {
        String key = key(userId, tokenId);
        redisTemplate.opsForValue().set(key, Long.toString(epoch), ttl);
    }

    /**
     * 判断刷新令牌是否仍有效（键存在且世代等于当前用户会话世代）。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     * @return 是否有效。
     */
    @Override
    public boolean isTokenValid(long userId, String tokenId) {
        String value = redisTemplate.opsForValue().get(key(userId, tokenId));
        if (value == null) {
            return false;
        }
        return value.equals(Long.toString(currentEpoch(userId)));
    }

    /**
     * 原子消费令牌：GETDEL 后校验世代，避免并发 refresh 重放，并与 revokeAll 世代失效协同。
     */
    @Override
    public OptionalLong consumeToken(long userId, String tokenId) {
        Long epoch = redisTemplate.execute(
                consumeScript,
                List.of(key(userId, tokenId), epochKey(userId))
        );
        return epoch == null ? OptionalLong.empty() : OptionalLong.of(epoch);
    }

    /**
     * 撤销单个刷新令牌。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     */
    @Override
    public void revokeToken(long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    /**
     * 撤销该用户全部刷新令牌：原子提升会话世代，使旧 jti 立即失效。
     *
     * @param userId 用户 ID。
     */
    @Override
    public void revokeAll(long userId) {
        redisTemplate.opsForValue().increment(epochKey(userId));
    }

    long currentEpoch(long userId) {
        String value = redisTemplate.opsForValue().get(epochKey(userId));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /**
     * 生成白名单键名。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     * @return Redis 键名。
     */
    static String key(long userId, String tokenId) {
        return "auth:rt:%d:%s".formatted(userId, tokenId);
    }

    static String epochKey(long userId) {
        return "auth:rt:epoch:%d".formatted(userId);
    }
}
