package com.tongji.llm.rag;

import com.tongji.common.exception.BusinessException;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void privatePostQaPurgesStaleIndexAndRejectsBeforeVectorSearch() {
        KnowPostDetailRow privatePost = new KnowPostDetailRow();
        privatePost.setId(99L);
        privatePost.setStatus("published");
        privatePost.setVisible("private");
        when(knowPostMapper.findDetailById(99L)).thenReturn(privatePost);

        RagQueryService service = new RagQueryService(vectorStore, chatClient, indexService, knowPostMapper);

        assertThatThrownBy(() -> service.streamAnswerFlux(99L, "question", 5, 128))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");
        verify(indexService).deletePostIndex(99L);
        verify(indexService, never()).ensureIndexed(99L);
        verifyNoInteractions(vectorStore, chatClient);
    }
}
