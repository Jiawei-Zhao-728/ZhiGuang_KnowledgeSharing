package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    @Mock
    private ElasticsearchClient es;
    @Mock
    private KnowPostMapper knowPostMapper;
    @Mock
    private CounterService counterService;

    @Test
    void upsertRemovesNonPublicPostInsteadOfIndexingBody() throws Exception {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        row.setContentUrl("https://cdn.example.test/private.md");
        when(knowPostMapper.findDetailById(anyLong())).thenReturn(row);
        SearchIndexService service = new SearchIndexService(es, knowPostMapper, counterService, new ObjectMapper());

        service.upsertKnowPost(42L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(es).index(requestCaptor.capture());
        Map<?, ?> tombstone = (Map<?, ?>) requestCaptor.getValue().document();
        assertThat(tombstone.get("content_id")).isEqualTo(42L);
        assertThat(tombstone.get("status")).isEqualTo("deleted");
        verifyNoInteractions(counterService);
    }
}
