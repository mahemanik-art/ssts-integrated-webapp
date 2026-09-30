package org.sstamilschool.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.sstamilschool.model.SstsDonation;

/**
 * Donation (gift ledger) queries.
 *
 * <p>There is deliberately NO {@code sumByAmount} aggregate method here. The
 * project's rule is that a derived quantity is computed at read time so it can
 * never go stale, and a SQL SUM would also hand back a different numeric type
 * that is easy to widen by accident. {@code DonorService} sums the BigDecimals
 * in Java instead, which keeps the arithmetic exact and the type honest.
 */
@Repository
public interface SstsDonationRepository extends JpaRepository<SstsDonation, Long> {

    /**
     * The donations ledger, newest gift first.
     *
     * <p>{@code LEFT JOIN FETCH d.donor} is load-bearing, not an optimisation:
     * the admin list renders the donor's name on every row, and without the
     * fetch that is an N+1 (or, under a closed EntityManager, a
     * LazyInitializationException).
     */
    @Query("SELECT d FROM SstsDonation d "
         + "LEFT JOIN FETCH d.donor "
         + "ORDER BY d.donatedOn DESC, d.id DESC")
    List<SstsDonation> findAllForLedger();

    /** Same fetch, restricted to one donor's giving history. */
    @Query("SELECT d FROM SstsDonation d "
         + "LEFT JOIN FETCH d.donor "
         + "WHERE d.donor.id = :donorId "
         + "ORDER BY d.donatedOn DESC, d.id DESC")
    List<SstsDonation> findByDonorIdForLedger(@Param("donorId") Long donorId);

    /**
     * Gifts in a date range, used for the year-to-date total. Fetched, because
     * the donations list shows the donor name alongside each one.
     */
    @Query("SELECT d FROM SstsDonation d "
         + "LEFT JOIN FETCH d.donor "
         + "WHERE d.donatedOn BETWEEN :from AND :to "
         + "ORDER BY d.donatedOn DESC, d.id DESC")
    List<SstsDonation> findByDonatedOnBetweenForLedger(@Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    long countByDonorId(Long donorId);
}