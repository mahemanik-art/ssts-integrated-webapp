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

    // Still JPQL rather than a derived query: NULLS LAST cannot be spelled in a
    // derived method name (Spring Data parses "NullsLast" as a property and
    // fails with "No property 'nullsLastFullName' found"), and unit tests mock
    // this repository so only a real boot catches it. The LEFT JOIN FETCH u.profile
    // that used to be here is gone -- bio/avatarUrl are columns on ssts_users now.
    @Query("SELECT u FROM SstsUser u WHERE u.userType IN ('staff', 'volunteer') AND u.isActive = true ORDER BY u.joiningDate ASC NULLS LAST, u.fullName ASC")
    List<SstsUser> findPublicTeamMembers();

    // Super-admin team management: every staff/volunteer regardless of isActive,
    // so a deactivated member stays editable instead of vanishing from the list.
    List<SstsUser> findByUserTypeInOrderByFullNameAsc(List<String> userTypes);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT COUNT(s) FROM SstsUser s WHERE s.lastLogin > :since")
    long countActiveUsersSince(@Param("since") LocalDateTime since);

    @Modifying
    @Transactional
    @Query("UPDATE SstsUser u SET u.lastLogin = :now WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // User Management module (super-admin): all users with role joined for list/edit
    @Query("SELECT u FROM SstsUser u LEFT JOIN FETCH u.role ORDER BY u.userType ASC, u.fullName ASC")
    List<SstsUser> findAllForAdmin();

    @Query("SELECT u FROM SstsUser u LEFT JOIN FETCH u.role WHERE u.id = :id")
    Optional<SstsUser> findByIdWithRole(@Param("id") Long id);

    // Counts for the Reports module (later wave) and last-super-admin protection
    long countByRoleId(Long roleId);

    @Query("SELECT COUNT(u) FROM SstsUser u WHERE u.role.name = :roleName")
    long countByRoleName(@Param("roleName") String roleName);

    long countByIsActiveTrue();
    long countByIsActiveFalse();
    long countByEmailVerifiedFalse();
    long countByUserTypeAndIsActive(String userType, boolean isActive);
}
