package com.tongji.llm.rag;

import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private RagQueryService service;

    @BeforeEach
    void setUp() {
        service = new RagQueryService(vectorStore, chatClient, indexService);
    }

    @Test
    void streamAnswerFlux_rejectsNonPublicPostWithoutSearchingOrGenerating() {
        when(indexService.isPubliclyQueryable(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.streamAnswerFlux(7L, "请总结这篇文章", 5, 128))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(be.getMessage()).contains("无权限");
                });

        verify(indexService).purgeChunks(7L);
        verify(indexService, never()).ensureIndexed(anyLong());
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(chatClient);
    }
}
