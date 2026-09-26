package org.sstamilschool.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsUserRepository;

@Service
public class TeamService {

    private final SstsUserRepository userRepository;

    public TeamService(SstsUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(value = CacheConfig.TEAM_MEMBERS, key = "'all'")
    @Transactional(readOnly = true)
    public List<SstsUser> getPublicTeamMembers() {
        return userRepository.findPublicTeamMembers();
    }

    @CacheEvict(value = CacheConfig.TEAM_MEMBERS, allEntries = true)
    public void evictTeamMembers() {
    }
}
