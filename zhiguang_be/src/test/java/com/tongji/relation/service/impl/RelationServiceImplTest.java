package com.tongji.relation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.profile.api.dto.ProfileResponse;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationServiceImplTest {

    @Mock
    private RelationMapper relationMapper;
    @Mock
    private OutboxMapper outboxMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private UserMapper userMapper;

    @Test
    void relationProfilesDoNotExposePhoneOrEmail() {
        User user = User.builder()
                .id(7L)
                .nickname("alice")
                .phone("13800138000")
                .email("alice@example.test")
                .build();
        when(userMapper.listByIds(List.of(7L))).thenReturn(List.of(user));

        RelationServiceImpl service = new RelationServiceImpl(
                relationMapper,
                outboxMapper,
                redis,
                new ObjectMapper(),
                userMapper
        );

        @SuppressWarnings("unchecked")
        List<ProfileResponse> profiles = (List<ProfileResponse>) ReflectionTestUtils.invokeMethod(
                service,
                "toProfiles",
                List.of(7L)
        );

        assertThat(profiles).hasSize(1);
        assertThat(profiles.getFirst().phone()).isNull();
        assertThat(profiles.getFirst().email()).isNull();
    }
}
