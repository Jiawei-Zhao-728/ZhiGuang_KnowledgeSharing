package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexServiceTest {

    @Test
    void upsertSoftDeletesNonPublicPosts() throws Exception {
        ElasticsearchClient es = mock(ElasticsearchClient.class);
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        when(mapper.findDetailById(42L)).thenReturn(privatePost());

        SearchIndexService service = new SearchIndexService(
                es,
                mapper,
                mock(CounterService.class),
                new ObjectMapper()
        );

        service.upsertKnowPost(42L);

        verify(es).index(org.mockito.ArgumentMatchers.argThat(request ->
                "42".equals(request.id())
                        && request.document() instanceof java.util.Map<?, ?> doc
                        && "deleted".equals(doc.get("status"))
        ));
    }

    private KnowPostDetailRow privatePost() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        return row;
    }
}
