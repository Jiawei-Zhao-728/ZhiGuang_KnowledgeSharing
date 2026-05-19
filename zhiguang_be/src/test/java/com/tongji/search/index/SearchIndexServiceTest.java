package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    private SearchIndexService service;

    @BeforeEach
    void setUp() {
        service = new SearchIndexService(es, knowPostMapper, counterService, new ObjectMapper());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void upsertRemovesNonPublicPostInsteadOfIndexingBody() throws Exception {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        row.setTitle("private title");
        row.setDescription("private description");
        row.setContentUrl("https://example.com/private-body.txt");
        when(knowPostMapper.findDetailById(42L)).thenReturn(row);

        service.upsertKnowPost(42L);

        ArgumentCaptor<IndexRequest<Map<String, Object>>> captor =
                ArgumentCaptor.forClass((Class) IndexRequest.class);
        verify(es).index(captor.capture());
        Map<String, Object> document = captor.getValue().document();
        assertThat(document)
                .containsEntry("content_id", 42L)
                .containsEntry("status", "deleted")
                .doesNotContainKeys("title", "description", "body", "title_suggest");
        verifyNoInteractions(counterService);
    }
}
