package org.sstamilschool.repository;

import org.sstamilschool.model.SstsRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SstsRoleRepository extends JpaRepository<SstsRole, Long> {
    Optional<SstsRole> findByName(String name);
}
