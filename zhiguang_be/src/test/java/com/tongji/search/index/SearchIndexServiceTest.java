package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchIndexServiceTest {

    @Test
    void upsertPurgesNonPublicPostsInsteadOfIndexingThem() {
        ElasticsearchClient es = mock(ElasticsearchClient.class);
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        CounterService counterService = mock(CounterService.class);
        SearchIndexService service = spy(new SearchIndexService(es, mapper, counterService, new ObjectMapper()));
        doNothing().when(service).softDeleteKnowPost(10L);

        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(10L);
        row.setStatus("published");
        row.setVisible("private");
        when(mapper.findDetailById(10L)).thenReturn(row);

        service.upsertKnowPost(10L);

        verify(service).softDeleteKnowPost(10L);
        verifyNoInteractions(counterService);
    }
}
