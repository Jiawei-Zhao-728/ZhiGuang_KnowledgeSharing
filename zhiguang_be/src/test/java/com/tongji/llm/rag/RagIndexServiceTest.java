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

import java.io.IOException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexServiceTest {
    private static final long POST_ID = 42L;

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
        esProperties.setIndex("zhiguang-ai-index");
        service = new RagIndexService(vectorStore, knowPostMapper, es, esProperties);
    }

    @Test
    void reindexDeletesExistingChunksWhenPostBecomesPrivate() throws IOException {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setStatus("published");
        row.setVisible("private");
        when(knowPostMapper.findDetailById(POST_ID)).thenReturn(row);

        int indexed = service.reindexSinglePost(POST_ID);

        assertThat(indexed).isZero();
        verify(es).deleteByQuery(any(Function.class));
        verify(vectorStore, never()).add(any());
    }
}
