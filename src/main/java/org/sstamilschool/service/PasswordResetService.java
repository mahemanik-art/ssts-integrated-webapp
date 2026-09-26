package org.sstamilschool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.model.SstsPasswordResetToken;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsResetTokenRepository;
import org.sstamilschool.repository.SstsUserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final SstsResetTokenRepository tokenRepository;
    private final SstsUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final long tokenTtlSeconds;

    public PasswordResetService(SstsResetTokenRepository tokenRepository, SstsUserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.password-reset.token-expiry-seconds:3600}") long tokenTtlSeconds) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public Optional<String> createTokenForEmail(String email) {
        return userRepository.findByEmail(email)
            .map(user -> {
                tokenRepository.deleteByEmail(user.getEmail());

                SstsPasswordResetToken token = new SstsPasswordResetToken(
                    user.getEmail(),
                    generateToken(),
                    Instant.now().plusSeconds(tokenTtlSeconds));
                tokenRepository.save(token);
                return token.getToken();
            });
    }

    public Optional<SstsPasswordResetToken> findValidToken(String token) {
        return tokenRepository.findByToken(token)
            .filter(t -> !t.isUsed())
            .filter(t -> !t.isExpired());
    }

    @Transactional
    public boolean consumeToken(String token, String newPassword) {
        var holder = findValidToken(token);
        if (holder.isEmpty()) {
            return false;
        }

        SstsPasswordResetToken resetToken = holder.get();
        SstsUser user = userRepository.findByEmail(resetToken.getEmail())
            .orElseThrow(() -> new IllegalStateException("User for reset token not found: " + resetToken.getEmail()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
        return true;
    }

    @Transactional
    public void purgeExpiredAndUsed() {
        tokenRepository.findAll().forEach(t -> {
            if (t.isUsed() || t.isExpired()) {
                tokenRepository.delete(t);
            }
        });
    }
}
