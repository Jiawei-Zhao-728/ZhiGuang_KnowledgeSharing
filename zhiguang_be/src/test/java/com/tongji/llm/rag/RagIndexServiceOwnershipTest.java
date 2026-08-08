package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexServiceOwnershipTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private KnowPostMapper knowPostMapper;
    @Mock
    private ElasticsearchClient es;
    @Mock
    private EsProperties esProps;

    private RagIndexService service;

    @BeforeEach
    void setUp() {
        service = new RagIndexService(vectorStore, knowPostMapper, es, esProps);
    }

    @Test
    void reindexOwnedPost_rejectsNonOwnerAndDoesNotTouchVectors() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(1L);
        row.setCreatorId(100L);
        row.setStatus("published");
        row.setVisible("public");
        when(knowPostMapper.findDetailById(1L)).thenReturn(row);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.reindexOwnedPost(999L, 1L)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(vectorStore, never()).add(any());
        verify(es, never()).deleteByQuery(any());
    }

    @Test
    void reindexOwnedPost_rejectsMissingPost() {
        when(knowPostMapper.findDetailById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.reindexOwnedPost(100L, 1L)
        );

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(vectorStore, never()).add(any());
    }

    @Test
    void reindexOwnedPost_allowsOwner() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(1L);
        row.setCreatorId(100L);
        // Non-public so reindexSinglePost exits before vector writes.
        row.setStatus("published");
        row.setVisible("private");
        when(knowPostMapper.findDetailById(1L)).thenReturn(row);

        int chunks = service.reindexOwnedPost(100L, 1L);

        assertEquals(0, chunks);
        verify(vectorStore, never()).add(any());
    }
}
