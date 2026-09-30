package org.sstamilschool.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.sstamilschool.model.SstsDonor;

/**
 * Donor queries.
 *
 * <p>Note the boolean derived-method naming, which is a recurring trap in this
 * codebase: the entity field is {@code publiclyListed} (getter
 * {@code isPubliclyListed()}), so the derived fragment is
 * {@code ...ByPubliclyListedTrue}, NEVER {@code ...ByIsPubliclyListedTrue} --
 * the latter throws "No property 'isPubliclyListed' found" at STARTUP and takes
 * the whole context down.
 */
@Repository
public interface SstsDonorRepository extends JpaRepository<SstsDonor, Long> {

    /**
     * What the public home-page carousel renders: donors who are BOTH active
     * and consented to public listing, in curated order.
     *
     * <p>An explicit @Query rather than a derived method because the ordering
     * would make the derived name unreadable, and because the two boolean
     * filters are exactly the kind of thing that is easy to get subtly wrong.
     */
    @Query("SELECT d FROM SstsDonor d "
         + "WHERE d.active = true AND d.publiclyListed = true "
         + "ORDER BY d.displayOrder ASC, d.name ASC")
    List<SstsDonor> findPubliclyListed();

    /**
     * Admin list. Deliberately ignores isActive so a deactivated donor stays
     * editable, matching every other admin list in this app.
     */
    List<SstsDonor> findAllByOrderByDisplayOrderAscNameAsc();

    /**
     * Pre-check for duplicate donor names. Case-insensitive because the database
     * enforces it that way: uq_ssts_donors_name_lower is a unique index on
     * lower(name), so "Acme Corp" and "ACME CORP" collide. A plain UNIQUE(name)
     * would not catch that, and two rows for the same donor would double-count in
     * every giving total and appear twice on the public carousel.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Case-insensitive name lookup that excludes one donor.
     *
     * <p>Needed for RENAME, not create. On create, {@code existsByNameIgnoreCase}
     * is enough. On update, a donor saving their own unchanged name must not be
     * told it collides with itself, so the {@code d.id <> :id} clause is what
     * makes "saved with the same name" legal while "renamed to another donor's
     * name" is refused.
     *
     * <p>This is an explicit @Query rather than a derived method because a
     * derived name cannot express an id exclusion -- {@code excludedId} maps to
     * no property path and Spring Data would fail to parse it.
     */
    @Query("SELECT d FROM SstsDonor d "
         + "WHERE lower(d.name) = lower(:name) AND d.id <> :id")
    java.util.Optional<SstsDonor> findByNameIgnoreCaseExcludingId(
            @Param("name") String name, @Param("id") Long id);

    long countByActiveTrue();

    /** Active donors, for the donation form's picker. */
    List<SstsDonor> findByActiveTrueOrderByNameAsc();

    /** Used by the reports page so the count is live, not cached. */
    long countByPubliclyListedTrue();
}