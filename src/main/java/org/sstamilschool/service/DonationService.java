package org.sstamilschool.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.model.SstsDonation;
import org.sstamilschool.repository.SstsDonationRepository;
import org.sstamilschool.util.SchoolTime;

/**
 * The donation ledger: record, correct, and read gifts.
 *
 * <p><b>Not cached.</b> Every other cached module here is read-heavy editorial
 * content. This one is money and changes one row at a time, so a TTL cache would
 * show a total that disagrees with the bank. The ledger is a handful of rows and
 * a single indexed read.
 *
 * <p><b>Corrections, never silent edits.</b> A gift amount is a real financial
 * record. The scale-2 check in {@link SstsDonation#setAmount(BigDecimal)} throws
 * rather than rounding, so a typo'd 100.005 is rejected instead of quietly
 * becoming 100.00 or 100.01.
 */
@Service
public class DonationService {

    private final SstsDonationRepository donationRepo;
    private final DonorService donorService;

    public DonationService(SstsDonationRepository donationRepo, DonorService donorService) {
        this.donationRepo = donationRepo;
        this.donorService = donorService;
    }

    // ---------------------------------------------------------------- read

    /** The ledger, newest gift first, with donor names fetched. */
    @Transactional(readOnly = true)
    public List<SstsDonation> findLedger() {
        return donationRepo.findAllForLedger();
    }

    /** One donor's giving history. */
    @Transactional(readOnly = true)
    public List<SstsDonation> findByDonor(Long donorId) {
        return donationRepo.findByDonorIdForLedger(donorId);
    }

    @Transactional(readOnly = true)
    public SstsDonation findById(Long id) {
        return id == null ? null : donationRepo.findById(id).orElse(null);
    }

    // --------------------------------------------------------------- write

    /**
     * Records a gift. The donor is resolved through {@link DonorService} so the
     * reference can never dangle, and the date defaults to today via
     * {@link SchoolTime} -- never {@code LocalDate.now()}, which runs 4-5h ahead
     * of the school on the server and would file an evening gift under tomorrow.
     */
    @Transactional
    public SstsDonation record(SstsDonation form) {
        requireValid(form);
        form.setDonor(donorService.require(form.getDonor() == null ? null : form.getDonor().getId()));
        if (form.getDonatedOn() == null) {
            form.setDonatedOn(SchoolTime.today());
        }
        if (form.getMethod() != null && form.getMethod().isBlank()) {
            form.setMethod(null);
        }
        if (form.getReferenceNo() != null && form.getReferenceNo().isBlank()) {
            form.setReferenceNo(null);
        }
        return donationRepo.save(form);
    }

    /**
     * Corrects an existing gift. The donor is NOT re-assignable here: moving a
     * gift between donors silently rewrites whose history a gift belongs to. To
     * do that deliberately, delete this row and record a new one.
     */
    @Transactional
    public SstsDonation update(Long id, SstsDonation form) {
        requireValid(form);
        SstsDonation existing = donationRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("That donation no longer exists."));
        existing.setAmount(form.getAmount());
        existing.setDonatedOn(form.getDonatedOn() == null ? SchoolTime.today() : form.getDonatedOn());
        existing.setMethod(form.getMethod());
        existing.setReferenceNo(form.getReferenceNo());
        existing.setNotes(form.getNotes());
        return donationRepo.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!donationRepo.existsById(id)) {
            throw new IllegalArgumentException("That donation no longer exists.");
        }
        donationRepo.deleteById(id);
    }

    private static void requireValid(SstsDonation form) {
        if (form.getAmount() == null) {
            throw new IllegalArgumentException("A donation amount is required.");
        }
        if (form.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("A donation amount must be greater than zero.");
        }
        if (form.getAmount().compareTo(new BigDecimal("9999999999.99")) > 0) {
            // numeric(12,2) leaves 10 integer digits; exceeding it would surface
            // as an opaque DB error instead of a readable one.
            throw new IllegalArgumentException("That amount is too large to record.");
        }
    }
}
