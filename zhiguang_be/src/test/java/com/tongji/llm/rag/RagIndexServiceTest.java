package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagIndexServiceTest {

    @Test
    void reindexPurgesChunksWhenPostIsNoLongerPublic() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        RagIndexService service = spy(new RagIndexService(
                vectorStore,
                mapper,
                mock(ElasticsearchClient.class),
                new EsProperties()));
        doNothing().when(service).purgePostIndex(10L);

        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(10L);
        row.setStatus("published");
        row.setVisible("private");
        when(mapper.findDetailById(10L)).thenReturn(row);

        int indexed = service.reindexSinglePost(10L);

        assertThat(indexed).isZero();
        verify(service).purgePostIndex(10L);
        verifyNoInteractions(vectorStore);
    }
}
