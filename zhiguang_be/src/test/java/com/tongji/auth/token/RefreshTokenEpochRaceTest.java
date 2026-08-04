package com.tongji.auth.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure in-memory model of the epoch-based refresh whitelist.
 * Locks in the password-reset vs concurrent-refresh contract without a Redis server.
 */
class RefreshTokenEpochRaceTest {

    private InMemoryEpochRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryEpochRefreshTokenStore();
    }

    @Test
    void refreshStartedBeforeRevokeAllCannotSurvivePasswordReset() {
        long userId = 99L;
        store.storeToken(userId, "stolen-jti", Duration.ofDays(7));

        // Attacker begins refresh: atomically consumes the stolen token
        OptionalLong consumed = store.consumeToken(userId, "stolen-jti");
        assertThat(consumed).isPresent();

        // Victim password reset invalidates all sessions
        store.revokeAll(userId);

        // Attacker finishes refresh using the previously validated epoch
        store.storeToken(userId, "attacker-successor", Duration.ofDays(7), consumed.getAsLong());

        assertThat(store.isTokenValid(userId, "attacker-successor"))
                .as("successor minted across revokeAll must not remain valid")
                .isFalse();
        assertThat(store.consumeToken(userId, "attacker-successor")).isEmpty();
    }

    @Test
    void revokeAllThenLegitimateLoginIssuesValidToken() {
        long userId = 11L;
        store.storeToken(userId, "old", Duration.ofDays(7));
        store.revokeAll(userId);

        store.storeToken(userId, "after-reset-login", Duration.ofDays(7));

        assertThat(store.isTokenValid(userId, "old")).isFalse();
        assertThat(store.isTokenValid(userId, "after-reset-login")).isTrue();
    }

    @Test
    void concurrentRefreshCannotReplaySameToken() {
        long userId = 5L;
        store.storeToken(userId, "jti", Duration.ofDays(7));

        assertThat(store.consumeToken(userId, "jti")).isPresent();
        assertThat(store.consumeToken(userId, "jti")).isEmpty();
    }

    /**
     * Mirrors {@link RedisRefreshTokenStore} epoch semantics for contract tests.
     */
    static final class InMemoryEpochRefreshTokenStore implements RefreshTokenStore {
        private final Map<String, String> tokens = new ConcurrentHashMap<>();
        private final Map<Long, AtomicLong> epochs = new ConcurrentHashMap<>();

        @Override
        public void storeToken(long userId, String tokenId, Duration ttl) {
            storeToken(userId, tokenId, ttl, currentEpoch(userId));
        }

        @Override
        public void storeToken(long userId, String tokenId, Duration ttl, long epoch) {
            tokens.put(key(userId, tokenId), Long.toString(epoch));
        }

        @Override
        public boolean isTokenValid(long userId, String tokenId) {
            String value = tokens.get(key(userId, tokenId));
            return value != null && value.equals(Long.toString(currentEpoch(userId)));
        }

        @Override
        public synchronized OptionalLong consumeToken(long userId, String tokenId) {
            String value = tokens.remove(key(userId, tokenId));
            if (value == null) {
                return OptionalLong.empty();
            }
            if (!value.equals(Long.toString(currentEpoch(userId)))) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(value));
        }

        @Override
        public void revokeToken(long userId, String tokenId) {
            tokens.remove(key(userId, tokenId));
        }

        @Override
        public void revokeAll(long userId) {
            epochs.computeIfAbsent(userId, id -> new AtomicLong(0)).incrementAndGet();
        }

        private long currentEpoch(long userId) {
            AtomicLong epoch = epochs.get(userId);
            return epoch == null ? 0L : epoch.get();
        }

        private static String key(long userId, String tokenId) {
            return userId + ":" + tokenId;
        }
    }
}
