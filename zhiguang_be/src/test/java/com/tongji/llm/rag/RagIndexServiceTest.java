package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.util.ObjectBuilder;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagIndexServiceTest {

    private VectorStore vectorStore;
    private KnowPostMapper knowPostMapper;
    private ElasticsearchClient es;
    private EsProperties esProps;
    private RestTemplate http;
    private RagIndexService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        knowPostMapper = mock(KnowPostMapper.class);
        es = mock(ElasticsearchClient.class);
        esProps = new EsProperties();
        esProps.setIndex("zhiguang-ai-index");
        http = mock(RestTemplate.class);
        service = new RagIndexService(vectorStore, knowPostMapper, es, esProps, http);
    }

    @Test
    void failedVectorAddDoesNotDeleteExistingChunks() throws Exception {
        long postId = 42L;
        when(knowPostMapper.findDetailById(postId)).thenReturn(publishedPublicPost(postId));
        // Empty index name forces fingerprint check to skip ES search and treat as stale.
        esProps.setIndex("");
        when(http.getForObject(eq("https://cdn.example/posts/42/content.md"), eq(String.class)))
                .thenReturn("# Title\nbody");
        doThrow(new RuntimeException("embedding unavailable")).when(vectorStore).add(any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.reindexSinglePost(postId));

        assertEquals("Failed to index post 42", ex.getMessage());
        verify(vectorStore).add(any());
        verify(es, never()).deleteByQuery(ArgumentMatchers.<Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>>>any());
    }

    @Test
    void successfulAddDeletesStaleChunksAfterWrite() throws Exception {
        long postId = 7L;
        when(knowPostMapper.findDetailById(postId)).thenReturn(publishedPublicPost(postId));
        when(es.search(
                ArgumentMatchers.<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>>any(),
                eq(Map.class)))
                .thenThrow(new RuntimeException("fingerprint unavailable"));
        when(http.getForObject(eq("https://cdn.example/posts/42/content.md"), eq(String.class)))
                .thenReturn("# Title\nbody");
        when(es.deleteByQuery(
                ArgumentMatchers.<Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>>>any()))
                .thenReturn(null);

        int written = service.reindexSinglePost(postId);

        assertEquals(1, written);
        InOrder order = inOrder(vectorStore, es);
        order.verify(vectorStore).add(ArgumentMatchers.<List>any());
        order.verify(es).deleteByQuery(
                ArgumentMatchers.<Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>>>any());
    }

    private static KnowPostDetailRow publishedPublicPost(long postId) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(postId);
        row.setStatus("published");
        row.setVisible("public");
        row.setContentUrl("https://cdn.example/posts/42/content.md");
        row.setContentSha256("abc123");
        row.setContentEtag("etag-1");
        row.setTitle("Sample");
        return row;
    }
}
