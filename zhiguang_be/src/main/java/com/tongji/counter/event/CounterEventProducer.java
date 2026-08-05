package com.tongji.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 计数事件生产者。
 *
 * <p>职责：将业务产生的计数增量事件发送到 Kafka 主题，供聚合消费者处理。
 * 发送必须等待 broker 确认；失败向上抛出，由调用方回滚位图事实，避免 SDS 永久丢计数。</p>
 */
@Service
public class CounterEventProducer {
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public CounterEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布计数事件到 Kafka，并等待 broker 确认。
     * @param event 计数事件（实体类型、ID、指标、delta 等）
     * @throws IllegalStateException 序列化失败、发送失败或超时时抛出
     */
    public void publish(CounterEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            // 分区键按实体维度，保证同实体事件顺序
            String key = event.getEntityType() + ":" + event.getEntityId();
            kafka.send(CounterTopics.EVENTS, key, payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize counter event", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing counter event", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish counter event", e);
        }
    }
}
