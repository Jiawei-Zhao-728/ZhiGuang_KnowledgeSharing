package com.tongji.llm.rag;

import com.tongji.common.exception.BusinessException;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagQueryServiceTest {

    @Test
    void refusesQaForNonPublicPostBeforeVectorSearch() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        when(mapper.findDetailById(42L)).thenReturn(row);

        VectorStore vectorStore = mock(VectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagIndexService indexService = mock(RagIndexService.class);
        RagQueryService service = new RagQueryService(vectorStore, chatClient, indexService, mapper);

        assertThatThrownBy(() -> service.streamAnswerFlux(42L, "question", 3, 256))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");

        verify(indexService).deleteExistingChunks(42L);
        verifyNoInteractions(vectorStore, chatClient);
    }
}
