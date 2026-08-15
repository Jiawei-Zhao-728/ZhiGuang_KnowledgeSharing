package com.tongji.relation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.profile.api.dto.ProfileResponse;
import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.OutboxMapper;
import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression: following/follower profile lists are visible to any authenticated caller.
 * Phone and email are login identifiers (password-reset targets) and must not leak.
 */
@ExtendWith(MockitoExtension.class)
class RelationServiceImplTest {

    @Mock
    RelationMapper relationMapper;
    @Mock
    OutboxMapper outboxMapper;
    @Mock
    StringRedisTemplate redis;
    @Mock
    UserMapper userMapper;
    @Mock
    ZSetOperations<String, String> zSetOps;

    RelationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RelationServiceImpl(relationMapper, outboxMapper, redis, new ObjectMapper(), userMapper);
        when(redis.opsForZSet()).thenReturn(zSetOps);
    }

    @Test
    void followingProfilesOmitsPhoneAndEmail() {
        when(zSetOps.reverseRange(eq("uf:flws:1"), eq(0L), eq(19L)))
                .thenReturn(new LinkedHashSet<>(List.of("2")));
        when(userMapper.listByIds(anyList())).thenReturn(List.of(userWithPii(2L)));

        List<ProfileResponse> profiles = service.followingProfiles(1L, 20, 0, null);

        assertThat(profiles).hasSize(1);
        ProfileResponse profile = profiles.getFirst();
        assertThat(profile.id()).isEqualTo(2L);
        assertThat(profile.nickname()).isEqualTo("alice");
        assertThat(profile.phone()).isNull();
        assertThat(profile.email()).isNull();
    }

    @Test
    void followersProfilesOmitsPhoneAndEmail() {
        when(zSetOps.reverseRange(eq("uf:fans:1"), eq(0L), eq(19L)))
                .thenReturn(new LinkedHashSet<>(List.of("3")));
        when(userMapper.listByIds(anyList())).thenReturn(List.of(userWithPii(3L)));

        List<ProfileResponse> profiles = service.followersProfiles(1L, 20, 0, null);

        assertThat(profiles).hasSize(1);
        ProfileResponse profile = profiles.getFirst();
        assertThat(profile.id()).isEqualTo(3L);
        assertThat(profile.nickname()).isEqualTo("alice");
        assertThat(profile.phone()).isNull();
        assertThat(profile.email()).isNull();
    }

    private static User userWithPii(long id) {
        return User.builder()
                .id(id)
                .nickname("alice")
                .avatar("https://cdn/a.png")
                .bio("hi")
                .zgId("zg_alice")
                .phone("13800138000")
                .email("alice@example.com")
                .tagsJson("[]")
                .build();
    }
}
