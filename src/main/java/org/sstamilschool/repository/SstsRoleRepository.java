package org.sstamilschool.repository;

import org.sstamilschool.model.SstsRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SstsRoleRepository extends JpaRepository<SstsRole, Long> {
    Optional<SstsRole> findByName(String name);

    List<SstsRole> findAllByOrderByNameAsc();
    boolean existsByName(String name);
    @Query("SELECT COUNT(r) FROM SstsRole r WHERE LOWER(r.name) = LOWER(:name)")
    long countByNameIgnoreCase(@Param("name") String name);
    long countByIsActiveTrue();
}
