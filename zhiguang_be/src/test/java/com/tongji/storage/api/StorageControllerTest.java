package com.tongji.storage.api;

import com.tongji.auth.token.JwtService;
import com.tongji.common.exception.BusinessException;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.storage.OssStorageService;
import com.tongji.storage.api.dto.StoragePresignRequest;
import com.tongji.storage.api.dto.StoragePresignResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    private OssStorageService ossStorageService;
    @Mock
    private JwtService jwtService;
    @Mock
    private KnowPostMapper knowPostMapper;
    @Mock
    private Jwt jwt;

    private StorageController controller;

    @BeforeEach
    void setUp() {
        controller = new StorageController(ossStorageService, jwtService, knowPostMapper);
        when(jwtService.extractUserId(jwt)).thenReturn(7L);
    }

    @Test
    void contentPresignRejectedForPublishedPost() {
        KnowPost published = KnowPost.builder()
                .id(42L)
                .creatorId(7L)
                .status("published")
                .build();
        when(knowPostMapper.findById(42L)).thenReturn(published);

        StoragePresignRequest request = new StoragePresignRequest(
                "knowpost_content", "42", "text/markdown", ".md");

        assertThatThrownBy(() -> controller.presign(request, jwt))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅草稿可上传正文内容");

        verify(ossStorageService, never()).generatePresignedPutUrl(anyString(), anyString(), anyInt());
    }

    @Test
    void contentPresignAllowedForDraft() {
        KnowPost draft = KnowPost.builder()
                .id(42L)
                .creatorId(7L)
                .status("draft")
                .build();
        when(knowPostMapper.findById(42L)).thenReturn(draft);
        when(ossStorageService.generatePresignedPutUrl("posts/42/content.md", "text/markdown", 600))
                .thenReturn("https://oss.example/put");

        StoragePresignRequest request = new StoragePresignRequest(
                "knowpost_content", "42", "text/markdown", ".md");

        StoragePresignResponse response = controller.presign(request, jwt);

        assertThat(response.objectKey()).isEqualTo("posts/42/content.md");
        assertThat(response.putUrl()).isEqualTo("https://oss.example/put");
    }
}
