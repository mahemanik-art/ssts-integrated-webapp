package org.sstamilschool.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * One gift received from a {@link SstsDonor}. This is the append-only financial
 * ledger.
 *
 * <p><b>Money is {@link BigDecimal}, not double or float.</b> Neither can
 * represent 0.10 or 19.99 exactly in binary, so summing float amounts yields a
 * total that is off by cents and does not reconcile against a bank statement.
 * The column is {@code numeric(12,2)} and is read back through
 * {@link BigDecimal#setScale(int, RoundingMode)} with UNNECESSARY, which
 * throws rather than silently rounding if a scale-2 value is ever violated.
 *
 * <p>The donor link is {@code LAZY} and deliberately not cascaded: deleting a
 * donor that has given money is refused by the database (ON DELETE RESTRICT),
 * so the cascade is unreachable anyway. Not making it EAGER also keeps the
 * donations list from issuing a query per row.
 */
@Entity
@Table(name = "ssts_donations")
public class SstsDonation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private SstsDonor donor;

    /** Gift amount. BigDecimal, scale 2. Never a primitive double. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "donated_on", nullable = false)
    private LocalDate donatedOn;

    /**
     * cash / check / online / in_kind. An open vocabulary the school extends --
     * only constrained to be a real value, so a legitimate new method is not
     * rejected by a hardcoded list.
     */
    @Column(length = 40)
    private String method;

    /** Check number, transaction id, or similar free text. */
    @Column(name = "reference_no", length = 120)
    private String referenceNo;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Marks seed-created rows so the seed can delete exactly its own rows. */
    @Column(name = "seed_key", length = 64)
    private String seedKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsDonation() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SstsDonor getDonor() { return donor; }
    public void setDonor(SstsDonor donor) { this.donor = donor; }

    public BigDecimal getAmount() { return amount; }

    /**
     * Enforces scale 2 on the way in. UNNECESSARY throws ArithmeticException
     * on a third decimal place rather than quietly rounding a gift, because a
     * silently altered donation amount is worse than a rejected edit.
     *
     * <p>There is deliberately only ONE setter. An extra {@code setAmount(String)}
     * would make the property ambiguous for Spring's data binder, which could
     * then pick either overload; Spring converts the posted string to
     * BigDecimal itself and calls this method, so the scale check still runs.
     */
    public void setAmount(BigDecimal amount) {
        this.amount = amount == null ? null : amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public LocalDate getDonatedOn() { return donatedOn; }
    public void setDonatedOn(LocalDate donatedOn) { this.donatedOn = donatedOn; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getSeedKey() { return seedKey; }
    public void setSeedKey(String seedKey) { this.seedKey = seedKey; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}