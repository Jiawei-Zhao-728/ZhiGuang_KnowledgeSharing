package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.util.ObjectBuilder;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagIndexServiceTest {

    private VectorStore vectorStore;
    private KnowPostMapper knowPostMapper;
    private ElasticsearchClient elasticsearchClient;
    private RagIndexService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        knowPostMapper = mock(KnowPostMapper.class);
        elasticsearchClient = mock(ElasticsearchClient.class);
        EsProperties esProperties = new EsProperties();
        esProperties.setIndex("rag-index");
        service = new RagIndexService(vectorStore, knowPostMapper, elasticsearchClient, esProperties);
    }

    @Test
    void nonPublicPostDeletesExistingChunksInsteadOfLeavingStaleVectors() throws Exception {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        when(knowPostMapper.findDetailById(42L)).thenReturn(row);

        service.reindexSinglePost(42L);

        verifyDeleteByQueryCalled();
        verify(vectorStore, never()).add(any());
    }

    @Test
    void missingPostDeletesExistingChunksInsteadOfLeavingStaleVectors() throws Exception {
        when(knowPostMapper.findDetailById(42L)).thenReturn(null);

        service.reindexSinglePost(42L);

        verifyDeleteByQueryCalled();
        verify(vectorStore, never()).add(any());
    }

    private void verifyDeleteByQueryCalled() throws Exception {
        verify(elasticsearchClient).deleteByQuery(
                argThat((Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>> fn) -> fn != null)
        );
    }
}
