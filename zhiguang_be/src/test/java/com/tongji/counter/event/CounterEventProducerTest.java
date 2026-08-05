package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CounterEventProducerTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishWaitsForBrokerAckWithEntityPartitionKey() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafka.send(eq(CounterTopics.EVENTS), eq("knowpost:42"), anyString())).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(mock(SendResult.class));

        CounterEventProducer producer = new CounterEventProducer(kafka, new ObjectMapper());
        producer.publish(CounterEvent.of("knowpost", "42", "like", 1, 7L, 1));

        verify(kafka).send(eq(CounterTopics.EVENTS), eq("knowpost:42"), anyString());
        verify(future).get(eq(5L), eq(TimeUnit.SECONDS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishPropagatesBrokerFailure() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(kafka.send(eq(CounterTopics.EVENTS), eq("knowpost:42"), anyString())).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new TimeoutException("broker unavailable"));

        CounterEventProducer producer = new CounterEventProducer(kafka, new ObjectMapper());

        assertThatThrownBy(() -> producer.publish(CounterEvent.of("knowpost", "42", "like", 1, 7L, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish counter event");
    }
}
