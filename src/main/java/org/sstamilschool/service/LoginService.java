package org.sstamilschool.service;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.model.SstsUser;

import java.time.LocalDateTime;

@Service
public class LoginService {

    private final SstsUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(SstsUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean authenticate(String usernameOrEmail, String password) {
        var userOpt = userRepository.findByEmailOrUsername(usernameOrEmail, usernameOrEmail);
        if (userOpt.isEmpty()) return false;

        var user = userOpt.get();
        if (!user.isActive()) return false;
        if (!passwordEncoder.matches(password, user.getPasswordHash())) return false;

        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        var auth = new UsernamePasswordAuthenticationToken(user, password, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return true;
    }

    public String redirectBasedOnUserType(Long userId) {
        var user = userRepository.findById(userId).orElse(null);
        if (user == null) return "/";

        return switch (user.getUserType().toLowerCase()) {
            case "admin", "staff" -> "/admin/dashboard";
            case "volunteer" -> "/volunteer/dashboard";
            default -> "/parent/dashboard";
        };
    }
}