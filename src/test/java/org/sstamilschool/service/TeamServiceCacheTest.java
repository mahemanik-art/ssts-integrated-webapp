package org.sstamilschool.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache-only test: wires CacheConfig + TeamService with a mocked repository so it
 * never needs a live datasource.
 */
@SpringJUnitConfig(classes = { CacheConfig.class, TeamServiceCacheTest.TestConfig.class })
class TeamServiceCacheTest {

    @Autowired TeamService teamService;
    @Autowired SstsUserRepository userRepository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsUserRepository userRepository() {
            return mock(SstsUserRepository.class);
        }

        @Bean
        TeamService teamService(SstsUserRepository userRepository) {
            return new TeamService(userRepository);
        }
    }

    @BeforeEach
    void clearCache() {
        teamService.evictTeamMembers();
        clearInvocations(userRepository);
    }

    @Test void repeatedCallsHitDatabaseOnlyOnce() {
        when(userRepository.findPublicTeamMembers()).thenReturn(List.of());

        teamService.getPublicTeamMembers();
        teamService.getPublicTeamMembers();
        teamService.getPublicTeamMembers();

        verify(userRepository, times(1)).findPublicTeamMembers();
    }

    @Test void evictForcesNextCallToReloadFromDatabase() {
        when(userRepository.findPublicTeamMembers()).thenReturn(List.of(new SstsUser()));

        List<SstsUser> first = teamService.getPublicTeamMembers();
        List<SstsUser> cached = teamService.getPublicTeamMembers();
        assertThat(cached).isSameAs(first);
        verify(userRepository, times(1)).findPublicTeamMembers();

        teamService.evictTeamMembers();

        teamService.getPublicTeamMembers();
        verify(userRepository, times(2)).findPublicTeamMembers();
    }
}
