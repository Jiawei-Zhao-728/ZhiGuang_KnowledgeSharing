package com.tongji.auth.verification;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis 的验证码存储实现。
 * <p>
 * 使用 Hash 结构保存 `code`、`maxAttempts` 与 `attempts`，TTL 控制有效期。
 * 校验时支持尝试计数与错误状态返回，成功后删除键以防重用。
 */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String FIELD_CODE = "code";
    private static final String FIELD_MAX_ATTEMPTS = "maxAttempts";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> verifyScript;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.verifyScript = new DefaultRedisScript<>();
        this.verifyScript.setResultType(String.class);
        this.verifyScript.setScriptText(VERIFY_CODE_LUA);
    }

    /**
     * 保存验证码到 Redis Hash，并设置 TTL。
     *
     * @param scene       场景名称。
     * @param identifier  标识（手机号或邮箱）。
     * @param code        验证码字符串。
     * @param ttl         有效期。
     * @param maxAttempts 最大尝试次数。
     * @throws RedisSystemException 保存失败时抛出。
     */
    @Override
    public void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts) {
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        try {
            ops.put(key, FIELD_CODE, code);
            ops.put(key, FIELD_MAX_ATTEMPTS, String.valueOf(maxAttempts));
            ops.put(key, FIELD_ATTEMPTS, "0");
            redisTemplate.expire(key, ttl);
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to save verification code", ex);
        }
    }

    /**
     * 校验验证码是否匹配，更新尝试计数并在成功时删除记录。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @param code       用户输入的验证码。
     * @return 校验结果（成功、未找到、错误、尝试过多）。
     */
    @Override
    public VerificationCheckResult verify(String scene, String identifier, String code) {
        String key = buildKey(scene, identifier);
        try {
            String encodedResult = redisTemplate.execute(verifyScript, List.of(key), code);
            return parseVerificationResult(encodedResult);
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to verify verification code", ex);
        }
    }

    /**
     * 使验证码失效（删除存储记录）。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     */
    @Override
    public void invalidate(String scene, String identifier) {
        redisTemplate.delete(buildKey(scene, identifier));
    }

    /**
     * 生成验证码的 Redis 键名。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @return 键名字符串。
     */
    private static String buildKey(String scene, String identifier) {
        return "auth:code:%s:%s".formatted(scene, identifier);
    }

    /**
     * Parse the status and counters returned by the atomic verification script.
     */
    private static VerificationCheckResult parseVerificationResult(String encodedResult) {
        if (encodedResult == null) {
            throw invalidScriptResult(null);
        }

        String[] fields = encodedResult.split(":", 3);
        if (fields.length != 3) {
            throw invalidScriptResult(encodedResult);
        }

        try {
            return new VerificationCheckResult(
                    VerificationCodeStatus.valueOf(fields[0]),
                    Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2])
            );
        } catch (IllegalArgumentException ex) {
            throw invalidScriptResult(encodedResult);
        }
    }

    private static RedisSystemException invalidScriptResult(String encodedResult) {
        return new RedisSystemException(
                "Invalid verification result returned by Redis",
                new IllegalStateException(String.valueOf(encodedResult))
        );
    }

    /**
     * Atomically consumes a matching code or increments the failed-attempt count.
     * Keeping the read, comparison, delete and increment in one script prevents
     * concurrent requests from reusing a code or overwriting each other's attempts.
     */
    private static final String VERIFY_CODE_LUA = """
            local key = KEYS[1]
            local candidate = ARGV[1]
            local storedCode = redis.call('HGET', key, 'code')
            if not storedCode then
              return 'NOT_FOUND:0:0'
            end

            local maxAttempts = tonumber(redis.call('HGET', key, 'maxAttempts')) or 5
            local attempts = tonumber(redis.call('HGET', key, 'attempts')) or 0
            if attempts >= maxAttempts then
              return 'TOO_MANY_ATTEMPTS:' .. attempts .. ':' .. maxAttempts
            end

            if storedCode == candidate then
              redis.call('DEL', key)
              return 'SUCCESS:' .. attempts .. ':' .. maxAttempts
            end

            attempts = redis.call('HINCRBY', key, 'attempts', 1)
            if attempts >= maxAttempts then
              redis.call('PEXPIRE', key, 1800000)
              return 'TOO_MANY_ATTEMPTS:' .. attempts .. ':' .. maxAttempts
            end
            return 'MISMATCH:' .. attempts .. ':' .. maxAttempts
            """;
}

