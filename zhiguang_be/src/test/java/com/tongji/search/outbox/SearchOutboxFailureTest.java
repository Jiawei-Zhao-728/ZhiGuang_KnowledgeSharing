package com.tongji.search.outbox;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.search.index.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchOutboxFailureTest {

    @Test
    void searchIndexFailurePropagatesToKafkaListenerWithoutAcknowledging() {
        SearchIndexService indexService = mock(SearchIndexService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CanalOutboxConsumerSearch consumer =
                new CanalOutboxConsumerSearch(new ObjectMapper(), indexService);
        String message = """
                {
                  "table": "outbox",
                  "type": "INSERT",
                  "data": [{
                    "payload": "{\\"entity\\":\\"knowpost\\",\\"op\\":\\"delete\\",\\"id\\":42}"
                  }]
                }
                """;
        doThrow(new IllegalStateException("Elasticsearch unavailable"))
                .when(indexService).softDeleteKnowPost(42L);

        assertThatThrownBy(() -> consumer.onMessage(message, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("search outbox");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void softDeleteDoesNotHideElasticsearchFailure() throws Exception {
        ElasticsearchClient elasticsearch = mock(ElasticsearchClient.class);
        when(elasticsearch.index(any(IndexRequest.class)))
                .thenThrow(new IOException("Elasticsearch unavailable"));
        SearchIndexService indexService = new SearchIndexService(
                elasticsearch,
                mock(KnowPostMapper.class),
                mock(CounterService.class),
                new ObjectMapper());

        assertThatThrownBy(() -> indexService.softDeleteKnowPost(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("soft delete")
                .hasCauseInstanceOf(IOException.class);
    }
}
