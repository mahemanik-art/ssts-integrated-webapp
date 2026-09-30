package org.sstamilschool.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.repository.SstsDonationRepository;
import org.sstamilschool.repository.SstsDonorRepository;

/**
 * Donor administration plus the read-time giving totals.
 *
 * <p><b>Totals are computed here, never stored.</b> A persisted
 * {@code total_giving} on the donor row would be a second source of truth that
 * can only ever disagree with the {@code ssts_donations} ledger -- and it would
 * disagree silently, because nothing recalculates it when a gift is added,
 * edited, or deleted. Summing the BigDecimals at read time costs one query over
 * a handful of rows and is correct by construction.
 *
 * <p><b>Donors are cached; donations are not.</b> The donor list changes rarely
 * and is rendered on the public home page, so it is a good cache candidate. The
 * donation ledger is NOT cached: it is the money, it is edited one row at a
 * time, and a stale total on a donations page is a correctness problem rather
 * than a slow page.
 */
@Service
public class DonorService {

    private final SstsDonorRepository donorRepo;
    private final SstsDonationRepository donationRepo;

    /**
     * No cycle exists despite {@link DonationService} depending on this class:
     * totals are computed by querying the donation REPOSITORY directly, not by
     * calling back into {@code DonationService}. Injecting the service would
     * have created a genuine bean cycle and needed a @Lazy proxy.
     */
    public DonorService(SstsDonorRepository donorRepo, SstsDonationRepository donationRepo) {
        this.donorRepo = donorRepo;
        this.donationRepo = donationRepo;
    }

    // ---------------------------------------------------------------- read

    /** What the public home page renders. Cached; see {@code app.cache.donors-ttl-hours}. */
    @Cacheable(CacheConfig.DONORS)
    @Transactional(readOnly = true)
    public List<SstsDonor> findPubliclyListed() {
        return donorRepo.findPubliclyListed();
    }

    /**
     * Admin list, ordered by displayOrder then name. Deliberately unfiltered by
     * isActive so a deactivated donor stays editable -- the same choice every
     * other admin list in this app makes.
     */
    @Transactional(readOnly = true)
    public List<SstsDonor> findAllForAdmin() {
        return donorRepo.findAllByOrderByDisplayOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public SstsDonor findById(Long id) {
        return id == null ? null : donorRepo.findById(id).orElse(null);
    }

    /** Donors offered in the donation form's picker. */
    @Transactional(readOnly = true)
    public List<SstsDonor> findActiveForPicker() {
        return donorRepo.findByActiveTrueOrderByNameAsc();
    }

    /**
     * Lifetime total for one donor, summed from the ledger at read time.
     * Returns {@link BigDecimal#ZERO} rather than null so callers can add to it
     * without a null check.
     */
    @Transactional(readOnly = true)
    public BigDecimal totalFor(Long donorId) {
        if (donorId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var d : donationRepo.findByDonorIdForLedger(donorId)) {
            total = total.add(d.getAmount());
        }
        return total;
    }

    /** Grand total across every gift ever recorded. */
    @Transactional(readOnly = true)
    public BigDecimal overallTotal() {
        return sum(donationRepo.findAllForLedger());
    }

    /** Total for one calendar year, used by the reports page. */
    @Transactional(readOnly = true)
    public BigDecimal totalForYear(int year) {
        return sum(donationRepo.findByDonatedOnBetweenForLedger(
                java.time.LocalDate.of(year, 1, 1), java.time.LocalDate.of(year, 12, 31)));
    }

    private static BigDecimal sum(List<org.sstamilschool.model.SstsDonation> gifts) {
        BigDecimal total = BigDecimal.ZERO;
        for (var g : gifts) {
            if (g.getAmount() != null) {
                total = total.add(g.getAmount());
            }
        }
        return total;
    }

    // --------------------------------------------------------------- write

    @Transactional
    @CacheEvict(value = CacheConfig.DONORS, allEntries = true)
    public SstsDonor create(SstsDonor form) {
        requireName(form);
        if (donorRepo.existsByNameIgnoreCase(form.getName().trim())) {
            throw new IllegalArgumentException("A donor named \"" + form.getName().trim() + "\" already exists.");
        }
        return donorRepo.save(form);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.DONORS, allEntries = true)
    public SstsDonor update(Long id, SstsDonor form) {
        SstsDonor existing = donorRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("That donor no longer exists."));
        requireName(form);

        // A rename must still respect the unique index, but must not collide
        // with the donor's own current name -- hence the explicit id exclusion.
        boolean renamed = !form.getName().trim().equalsIgnoreCase(existing.getName());
        if (renamed) {
            var clash = donorRepo.findByNameIgnoreCaseExcludingId(form.getName().trim(), id);
            if (clash.isPresent()) {
                throw new IllegalArgumentException(
                        "A different donor named \"" + form.getName().trim() + "\" already exists.");
            }
        }
        // Donations are NOT touched: they are an append-only financial record,
        // and a rename is a correction to the donor's name, not to past gifts.
        return donorRepo.save(form);
    }

    /**
     * Deletes a donor. Refused when the donor has given anything, because the
     * gifts are the school's financial history and losing them would make the
     * totals wrong. The check is here for a readable message; the database's
     * ON DELETE RESTRICT is the actual guarantee.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.DONORS, allEntries = true)
    public void delete(Long id) {
        SstsDonor existing = donorRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("That donor no longer exists."));
        if (donationRepo.countByDonorId(id) > 0) {
            throw new IllegalStateException(
                    "\"" + existing.getName() + "\" has recorded donations and cannot be deleted. "
                            + "Deactivate the donor instead, which hides them without losing the history.");
        }
        donorRepo.deleteById(id);
    }

    private static void requireName(SstsDonor form) {
        if (form.getName() == null || form.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("A donor name is required.");
        }
    }

    /** Used by {@link DonationService} so a gift can never point at a missing donor. */
    SstsDonor require(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Choose a donor for this gift.");
        }
        return donorRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("That donor no longer exists."));
    }
}