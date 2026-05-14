package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexServiceTest {
    @Mock
    private VectorStore vectorStore;
    @Mock
    private KnowPostMapper knowPostMapper;
    @Mock
    private ElasticsearchClient es;

    private RagIndexService service;

    @BeforeEach
    void setUp() {
        EsProperties esProperties = new EsProperties();
        esProperties.setIndex("rag-index");
        service = new RagIndexService(vectorStore, knowPostMapper, es, esProperties);
    }

    @Test
    void reindexPurgesExistingChunksWhenPostIsNoLongerPublic() throws Exception {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        when(knowPostMapper.findDetailById(42L)).thenReturn(row);

        service.reindexSinglePost(42L);

        verify(es).deleteByQuery(any(Function.class));
        verifyNoInteractions(vectorStore);
    }
}
