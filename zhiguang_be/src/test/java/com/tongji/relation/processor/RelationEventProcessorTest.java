package com.tongji.relation.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.CanalOutboxConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RelationEventProcessorTest {

    @Test
    void distinctUnfollowEventsForSameUsersUseDistinctDedupKeys() {
        RelationMapper mapper = mock(RelationMapper.class);
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        RelationEventProcessor processor = new RelationEventProcessor(mapper, redis);
        RelationEvent event = new RelationEvent("FollowCanceled", 10L, 20L, null);

        processor.process(event, "outbox-100");
        processor.process(event, "outbox-101");

        verify(mapper, times(2)).cancelFollower(20L, 10L);
        assertEquals("dedup:rel:event:outbox-100", redis.keys.get(0).get(0));
        assertEquals("dedup:rel:event:outbox-101", redis.keys.get(1).get(0));
    }

    @Test
    void redisFailurePropagatesSoTheSameEventCanBeRedelivered() {
        RelationMapper mapper = mock(RelationMapper.class);
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        redis.failNext = true;
        RelationEventProcessor processor = new RelationEventProcessor(mapper, redis);
        RelationEvent event = new RelationEvent("FollowCreated", 10L, 20L, 30L);

        assertThrows(IllegalStateException.class, () -> processor.process(event, "outbox-100"));
        processor.process(event, "outbox-100");

        // The DB upsert is idempotent; the atomic Redis script applies its side
        // effects once or observes the event marker after an uncertain timeout.
        verify(mapper, times(2)).insertFollower(30L, 20L, 10L, 1);
        assertEquals(2, redis.keys.size());
    }

    @Test
    void consumerPassesOutboxIdAndAcknowledgesOnlyAfterSuccess() throws Exception {
        RelationEventProcessor processor = mock(RelationEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CanalOutboxConsumer consumer = new CanalOutboxConsumer(new ObjectMapper(), processor);
        String message = relationOutboxMessage("901");

        consumer.onMessage(message, acknowledgment);

        verify(processor).process(
                eq(new RelationEvent("FollowCanceled", 10L, 20L, null)),
                eq("901")
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumerDoesNotAcknowledgeProcessorFailure() throws Exception {
        RelationEventProcessor processor = mock(RelationEventProcessor.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CanalOutboxConsumer consumer = new CanalOutboxConsumer(new ObjectMapper(), processor);
        String message = relationOutboxMessage("901");
        doThrow(new IllegalStateException("redis unavailable"))
                .when(processor).process(any(RelationEvent.class), eq("901"));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    private static String relationOutboxMessage(String outboxId) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("table", "outbox");
        root.put("type", "INSERT");
        ArrayNode data = root.putArray("data");
        ObjectNode row = data.addObject();
        row.put("id", outboxId);
        row.put("aggregate_type", "following");
        row.put(
                "payload",
                objectMapper.writeValueAsString(new RelationEvent("FollowCanceled", 10L, 20L, null))
        );
        return objectMapper.writeValueAsString(root);
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {
        private final List<List<String>> keys = new ArrayList<>();
        private boolean failNext;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.keys.add(List.copyOf(keys));
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("redis unavailable");
            }
            return (T) Long.valueOf(1);
        }
    }
}
