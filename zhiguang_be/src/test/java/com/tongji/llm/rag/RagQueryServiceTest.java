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
    void streamRejectsNonPublicPostBeforeSearchingVectors() {
        RagQueryService service = new RagQueryService(vectorStore, chatClient, indexService, knowPostMapper);
        when(knowPostMapper.findDetailById(42L)).thenReturn(row("published", "private"));

        assertThatThrownBy(() -> service.streamAnswerFlux(42L, "question", 5, 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不存在或无权限");

        verify(indexService, never()).ensureIndexed(42L);
        verifyNoInteractions(vectorStore, chatClient);
    }

    @Test
    void streamRejectsPostThatBecomesPrivateDuringIndexRefresh() {
        RagQueryService service = new RagQueryService(vectorStore, chatClient, indexService, knowPostMapper);
        when(knowPostMapper.findDetailById(42L))
                .thenReturn(row("published", "public"))
                .thenReturn(row("published", "private"));

        assertThatThrownBy(() -> service.streamAnswerFlux(42L, "question", 5, 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不存在或无权限");

        verify(indexService).ensureIndexed(42L);
        verifyNoInteractions(vectorStore, chatClient);
    }

    private static KnowPostDetailRow row(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
