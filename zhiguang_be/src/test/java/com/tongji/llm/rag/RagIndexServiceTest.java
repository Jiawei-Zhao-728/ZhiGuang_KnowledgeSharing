package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private EsProperties esProps;

    @Test
    void purgesExistingChunksWhenPostIsNoLongerPublic() {
        RagIndexService service = spy(new RagIndexService(vectorStore, knowPostMapper, es, esProps));
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(POST_ID);
        row.setStatus("published");
        row.setVisible("private");
        when(knowPostMapper.findDetailById(POST_ID)).thenReturn(row);
        doNothing().when(service).purgePost(POST_ID);

        int indexedChunks = service.reindexSinglePost(POST_ID);

        assertThat(indexedChunks).isZero();
        verify(service).purgePost(POST_ID);
        verify(vectorStore, never()).add(org.mockito.ArgumentMatchers.anyList());
        verifyNoInteractions(es);
    }
}
