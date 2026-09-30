package org.sstamilschool.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.sstamilschool.model.SstsFamily;

/**
 * Data access for ssts_families -- the PARENT MODULE's table.
 *
 * <p>The one query that matters for auth-adjacent flows is {@link #findByUserId},
 * because it is how a logged-in parent finds their own household. Everything else
 * here serves the admin screens.
 *
 * <p>Derived query names only: {@code NULLS LAST} cannot be spelled in a derived
 * name (Spring Data parses it as a property path and fails at STARTUP), so any
 * ordering that needs it must be an explicit {@code @Query}.
 */
@Repository
public interface SstsFamilyRepository extends JpaRepository<SstsFamily, Long> {

    /**
     * The family belonging to one portal login.
     *
     * <p>Returns empty rather than throwing for an account with no family: the
     * column is nullable, so an unlinked account is a legal state, not an error.
     */
    Optional<SstsFamily> findByUserId(Long userId);

    /**
     * True when this login is already linked to a family. Used to keep
     * registration from silently creating a SECOND household for a person who
     * re-registers or re-links.
     */
    boolean existsByUserId(Long userId);

    /** Admin listing: parent1's name is the natural display key for a household. */
    List<SstsFamily> findAllByOrderByParent1FullNameAsc();

    long countByUserId(Long userId);
}
