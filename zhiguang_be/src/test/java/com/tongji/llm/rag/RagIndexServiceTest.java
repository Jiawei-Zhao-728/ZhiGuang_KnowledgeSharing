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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagIndexServiceTest {

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
    void reindexSinglePost_purgesLeftoverChunksWhenPostIsPrivate() throws Exception {
        KnowPostDetailRow row = row("published", "private");
        when(knowPostMapper.findDetailById(42L)).thenReturn(row);
        when(esProps.getIndex()).thenReturn("zhiguang-ai-index");

        assertThat(service.reindexSinglePost(42L)).isZero();

        verifyDeleteByQuery();
        verify(vectorStore, never()).add(any());
    }

    @Test
    void reindexSinglePost_purgesLeftoverChunksWhenPostIsDeleted() throws Exception {
        KnowPostDetailRow row = row("deleted", "public");
        when(knowPostMapper.findDetailById(42L)).thenReturn(row);
        when(esProps.getIndex()).thenReturn("zhiguang-ai-index");

        assertThat(service.reindexSinglePost(42L)).isZero();

        verifyDeleteByQuery();
        verify(vectorStore, never()).add(any());
    }

    @Test
    void reindexSinglePost_purgesLeftoverChunksWhenPostIsMissing() throws Exception {
        when(knowPostMapper.findDetailById(42L)).thenReturn(null);
        when(esProps.getIndex()).thenReturn("zhiguang-ai-index");

        assertThat(service.reindexSinglePost(42L)).isZero();

        verifyDeleteByQuery();
        verify(vectorStore, never()).add(any());
    }

    @Test
    void isPubliclyQueryable_trueOnlyForPublishedPublic() {
        when(knowPostMapper.findDetailById(1L)).thenReturn(row("published", "public"));
        when(knowPostMapper.findDetailById(2L)).thenReturn(row("published", "private"));
        when(knowPostMapper.findDetailById(3L)).thenReturn(row("deleted", "public"));
        when(knowPostMapper.findDetailById(4L)).thenReturn(null);

        assertThat(service.isPubliclyQueryable(1L)).isTrue();
        assertThat(service.isPubliclyQueryable(2L)).isFalse();
        assertThat(service.isPubliclyQueryable(3L)).isFalse();
        assertThat(service.isPubliclyQueryable(4L)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private void verifyDeleteByQuery() throws Exception {
        verify(es).deleteByQuery(any(Function.class));
    }

    private static KnowPostDetailRow row(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus(status);
        row.setVisible(visible);
        row.setContentUrl("https://cdn.example/posts/42/content.md");
        return row;
    }
}
