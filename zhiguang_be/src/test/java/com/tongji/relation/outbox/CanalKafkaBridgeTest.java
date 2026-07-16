package com.tongji.relation.outbox;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanalKafkaBridgeTest {

    private KafkaTemplate<String, String> kafka;
    private CanalKafkaBridge bridge;

    @BeforeEach
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        bridge = new CanalKafkaBridge(
                kafka,
                new ObjectMapper(),
                mock(TaskExecutor.class),
                true,
                "localhost",
                11111,
                "example",
                "",
                "",
                ".*\\.outbox",
                100,
                1000
        );
    }

    @Test
    void failedAsyncKafkaSendRollsBackWithoutAcknowledgingCanalBatch() {
        CanalConnector connector = mock(CanalConnector.class);
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(eq(OutboxTopics.CANAL_OUTBOX), anyString())).thenReturn(failedSend);

        assertThrows(ExecutionException.class,
                () -> bridge.publishAndAck(connector, messageWithPayload("{\"id\":42}")));

        verify(kafka).send(eq(OutboxTopics.CANAL_OUTBOX), anyString());
        verify(connector).rollback(7L);
        verify(connector, never()).ack(7L);
    }

    private Message messageWithPayload(String payload) {
        CanalEntry.Column payloadColumn = CanalEntry.Column.newBuilder()
                .setName("payload")
                .setValue(payload)
                .build();
        CanalEntry.RowData rowData = CanalEntry.RowData.newBuilder()
                .addAfterColumns(payloadColumn)
                .build();
        CanalEntry.RowChange rowChange = CanalEntry.RowChange.newBuilder()
                .setEventType(CanalEntry.EventType.INSERT)
                .addRowDatas(rowData)
                .build();
        CanalEntry.Header header = CanalEntry.Header.newBuilder()
                .setTableName("outbox")
                .build();
        CanalEntry.Entry entry = CanalEntry.Entry.newBuilder()
                .setHeader(header)
                .setEntryType(CanalEntry.EntryType.ROWDATA)
                .setStoreValue(rowChange.toByteString())
                .build();

        return new Message(7L, List.of(entry));
    }
}
