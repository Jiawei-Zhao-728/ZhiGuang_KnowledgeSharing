package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagIndexServiceTest {

    @Test
    void reindexPurgesExistingChunksWhenPostIsNoLongerPublic() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        ElasticsearchClient es = mock(ElasticsearchClient.class);
        EsProperties esProperties = new EsProperties();
        esProperties.setIndex("rag-index");
        RagIndexService service = new RagIndexService(vectorStore, mapper, es, esProperties);

        long postId = 42L;
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(postId);
        row.setStatus("published");
        row.setVisible("private");
        when(mapper.findDetailById(postId)).thenReturn(row);

        service.reindexSinglePost(postId);

        verify(es).deleteByQuery(any(Function.class));
        verifyNoInteractions(vectorStore);
    }
}
