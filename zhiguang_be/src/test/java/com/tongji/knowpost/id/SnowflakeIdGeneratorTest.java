package com.tongji.knowpost.id;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SnowflakeIdGeneratorTest {

    @Test
    void nextIdEncodesConfiguredNodeIdentity() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 7);

        long id = generator.nextId();

        assertThat(generator.extractDatacenterId(id)).isEqualTo(3);
        assertThat(generator.extractWorkerId(id)).isEqualTo(7);
    }

    @Test
    void differentWorkersNeverShareIdsInTheSameBurst() {
        SnowflakeIdGenerator replicaA = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator replicaB = new SnowflakeIdGenerator(1, 2);

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            ids.add(replicaA.nextId());
            ids.add(replicaB.nextId());
        }

        assertThat(ids).hasSize(4000);
    }

    @Test
    void singleGeneratorIdsAreUnique() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            assertThat(ids.add(generator.nextId())).isTrue();
        }
    }

    @Test
    void deriveNodeIdsChangesWithPid() {
        long[] first = SnowflakeIdGenerator.deriveNodeIds("api-1", 1001L);
        long[] second = SnowflakeIdGenerator.deriveNodeIds("api-1", 1002L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void deriveNodeIdsChangesWithHost() {
        long[] first = SnowflakeIdGenerator.deriveNodeIds("api-1", 1001L);
        long[] second = SnowflakeIdGenerator.deriveNodeIds("api-2", 1001L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void assignUniqueNodeSkipsOccupiedLeaseAndClaimsNextSlot() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        long[] derived = SnowflakeIdGenerator.deriveNodeIds("api-1", 42L);
        long start = SnowflakeIdGenerator.slotOf(derived[0], derived[1]);
        long[] occupied = SnowflakeIdGenerator.nodeIdsFromSlot(start);
        long[] expected = SnowflakeIdGenerator.nodeIdsFromSlot(start + 1);

        when(ops.get(anyString())).thenReturn(null);
        when(ops.setIfAbsent(eq(SnowflakeIdGenerator.nodeLeaseKey(occupied[0], occupied[1])), anyString(), any(Duration.class)))
                .thenReturn(false);
        when(ops.setIfAbsent(eq(SnowflakeIdGenerator.nodeLeaseKey(expected[0], expected[1])), anyString(), any(Duration.class)))
                .thenReturn(true);

        long[] assigned = SnowflakeIdGenerator.assignUniqueNode(redis, "api-1", 42L);

        assertThat(assigned).containsExactly(expected[0], expected[1]);
        assertThat(assigned).isNotEqualTo(derived);
    }

    @Test
    void assignUniqueNodeFallsBackToDerivedIdsWhenRedisFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));

        long[] derived = SnowflakeIdGenerator.deriveNodeIds("api-1", 42L);
        long[] assigned = SnowflakeIdGenerator.assignUniqueNode(redis, "api-1", 42L);

        assertThat(assigned).containsExactly(derived[0], derived[1]);
    }

    @Test
    void rejectsOutOfRangeNodeIds() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0, 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datacenterId");
    }
}
