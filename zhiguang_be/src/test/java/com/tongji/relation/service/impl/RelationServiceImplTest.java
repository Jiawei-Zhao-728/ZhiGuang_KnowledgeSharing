package com.tongji.relation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.profile.api.dto.ProfileResponse;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationServiceImplTest {

    @Test
    void relationProfileProjectionRedactsContactIdentifiers() {
        UserMapper userMapper = mock(UserMapper.class);
        User relatedUser = User.builder()
                .id(12L)
                .nickname("related")
                .avatar("avatar")
                .bio("bio")
                .zgId("zg-12")
                .phone("13800138000")
                .email("related@example.test")
                .tagsJson("{}")
                .build();
        when(userMapper.listByIds(List.of(12L))).thenReturn(List.of(relatedUser));
        RelationServiceImpl service = new RelationServiceImpl(
                mock(RelationMapper.class),
                mock(OutboxMapper.class),
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                userMapper
        );

        @SuppressWarnings("unchecked")
        List<ProfileResponse> profiles = (List<ProfileResponse>) ReflectionTestUtils.invokeMethod(service, "toProfiles", List.of(12L));

        assertThat(profiles).hasSize(1);
        assertThat(profiles.getFirst().phone()).isNull();
        assertThat(profiles.getFirst().email()).isNull();
        assertThat(profiles.getFirst().nickname()).isEqualTo("related");
    }
}
