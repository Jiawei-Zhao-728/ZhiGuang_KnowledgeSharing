package com.tongji.llm.rag;

import com.tongji.common.exception.BusinessException;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private ChatClient chatClient;
    @Mock
    private RagIndexService indexService;
    @Mock
    private KnowPostMapper knowPostMapper;

    private RagQueryService service;

    @BeforeEach
    void setUp() {
        service = new RagQueryService(vectorStore, chatClient, indexService, knowPostMapper);
    }

    @Test
    void rejectsPrivatePostBeforeVectorSearch() {
        long postId = 42L;
        when(knowPostMapper.findDetailById(postId)).thenReturn(row("published", "private"));

        assertThatThrownBy(() -> service.streamAnswerFlux(postId, "question", 5, 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(indexService, never()).ensureIndexed(postId);
        verify(vectorStore, never()).similaritySearch(org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class));
    }

    private KnowPostDetailRow row(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
