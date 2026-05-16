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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagIndexServiceTest {

    private VectorStore vectorStore;
    private KnowPostMapper knowPostMapper;
    private ElasticsearchClient es;
    private RagIndexService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        knowPostMapper = mock(KnowPostMapper.class);
        es = mock(ElasticsearchClient.class);
        EsProperties esProperties = new EsProperties();
        esProperties.setIndex("rag-index");
        service = new RagIndexService(vectorStore, knowPostMapper, es, esProperties);
    }

    @Test
    void purgesExistingChunksWhenPostIsNotPublic() throws Exception {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setStatus("published");
        row.setVisible("private");
        when(knowPostMapper.findDetailById(42L)).thenReturn(row);

        int indexed = service.reindexSinglePost(42L);

        assertThat(indexed).isZero();
        verify(vectorStore, never()).add(anyList());
        verify(es).deleteByQuery(anyDeleteByQuery());
    }

    @Test
    void purgesExistingChunksWhenPostNoLongerExists() throws Exception {
        when(knowPostMapper.findDetailById(42L)).thenReturn(null);

        int indexed = service.reindexSinglePost(42L);

        assertThat(indexed).isZero();
        verify(vectorStore, never()).add(anyList());
        verify(es).deleteByQuery(anyDeleteByQuery());
    }

    private static Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>> anyDeleteByQuery() {
        return any();
    }
}
