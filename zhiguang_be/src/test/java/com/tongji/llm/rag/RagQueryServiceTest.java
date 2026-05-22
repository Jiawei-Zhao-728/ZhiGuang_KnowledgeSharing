package com.tongji.llm.rag;

import com.tongji.common.exception.BusinessException;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQueryServiceTest {

    @Test
    void streamAnswerRejectsNonPublicPostsBeforeIndexLookup() {
        RagIndexService indexService = mock(RagIndexService.class);
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        when(mapper.findDetailById(42L)).thenReturn(privatePost());

        RagQueryService service = new RagQueryService(
                mock(VectorStore.class),
                mock(ChatClient.class),
                indexService,
                mapper
        );

        assertThatThrownBy(() -> service.streamAnswerFlux(42L, "question", 5, 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限查看");
        verify(indexService, never()).ensureIndexed(42L);
    }

    private KnowPostDetailRow privatePost() {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(42L);
        row.setStatus("published");
        row.setVisible("private");
        return row;
    }
}
