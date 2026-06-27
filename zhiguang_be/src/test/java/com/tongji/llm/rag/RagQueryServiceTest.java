package com.tongji.llm.rag;

import com.tongji.common.exception.BusinessException;
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

    @Test
    void streamAnswerRejectsNonPublicPostBeforeRetrieval() {
        RagQueryService service = new RagQueryService(vectorStore, chatClient, indexService);
        when(indexService.isPublicPublished(42L)).thenReturn(false);

        assertThatThrownBy(() -> service.streamAnswerFlux(42L, "question", 5, 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权限查看");

        verify(indexService, never()).ensureIndexed(42L);
        verifyNoInteractions(vectorStore, chatClient);
    }
}
