package org.sstamilschool.service;

import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.model.SstsUser;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SstsUserDetailsService implements UserDetailsService {

    private final SstsUserRepository userRepository;

    public SstsUserDetailsService(SstsUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByEmailOrUsername(username, username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("Account is disabled: " + username);
        }

        user.getAuthorities();

        return user;
    }
}