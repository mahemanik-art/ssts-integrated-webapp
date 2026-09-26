package org.sstamilschool.repository;

import org.sstamilschool.model.SstsPasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SstsResetTokenRepository extends JpaRepository<SstsPasswordResetToken, Long> {
    Optional<SstsPasswordResetToken> findByToken(String token);
    void deleteByEmail(String email);
}
