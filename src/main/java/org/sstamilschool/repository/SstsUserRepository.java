package org.sstamilschool.repository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.model.SstsRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SstsUserRepository extends JpaRepository<SstsUser, Long> {
    Optional<SstsUser> findByEmail(String email);
    Optional<SstsUser> findByUsername(String username);
    Optional<SstsUser> findByEmailOrUsername(String email, String username);
    List<SstsUser> findByUserType(String userType);
    long countByUserType(String userType);

    @Query("SELECT COUNT(s) FROM SstsUser s WHERE s.lastLogin > :since")
    long countActiveUsersSince(@Param("since") LocalDateTime since);

    @Modifying
    @Transactional
    @Query("UPDATE SstsUser u SET u.lastLogin = :now WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}