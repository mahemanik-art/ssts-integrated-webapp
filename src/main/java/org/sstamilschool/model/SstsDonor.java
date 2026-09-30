package org.sstamilschool.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A donor the school thanks -- a person or a business.
 *
 * <p>Deliberately NOT an {@code SstsUser} and deliberately NOT related to one.
 * A donor is an external party with no portal login, and creating a login row
 * for them would put an account they never asked for on the users table. Donors
 * are also sometimes anonymous, which a user row cannot express.
 *
 * <p>NO total-giving column lives here. It is derivable by summing
 * {@link SstsDonation}, and a stored total can only ever drift from the ledger.
 * Totals are computed at read time by {@code DonorService}.
 *
 * <p>{@code active} and {@code publiclyListed} are separate and both matter:
 * active means "still a donor we work with", publiclyListed means "this donor
 * has consented to appear by NAME on the public home page". Defaulting the
 * second to false is deliberate -- publishing a donor's name without consent is
 * a real-world problem, not a UI detail.
 */
@Entity
@Table(name = "ssts_donors")
public class SstsDonor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Person or business name as it should appear. Not split into first/last. */
    @Column(nullable = false, length = 255)
    private String name;

    /** Short descriptor under the name, e.g. "Technology Services". */
    @Column(length = 255)
    private String tagline;

    /**
     * Optional external link, https-only (enforced by
     * chk_ssts_donors_website). The public page renders this as an anchor, so
     * allowing arbitrary schemes would make stored XSS trivial.
     */
    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    /**
     * Relative {@code /images/...} path or an {@code https://} bucket URL --
     * the same two forms the gallery accepts.
     */
    @Column(name = "photo_path", length = 500)
    private String photoPath;

    /** Curated carousel order; lower sorts first. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Consent to appear by name on the public site. Defaults to FALSE. */
    @Column(name = "is_public", nullable = false)
    private boolean publiclyListed = false;

    /** Internal notes. Never rendered publicly. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Marks rows created by sql/data.sql so the seed can delete exactly its own
     * rows on a re-run. NULL for anything made through /superadmin/donors.
     * Names are user-supplied and collide, so seeding must never key on them.
     */
    @Column(name = "seed_key", length = 64)
    private String seedKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsDonor() {
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isPubliclyListed() { return publiclyListed; }
    public void setPubliclyListed(boolean publiclyListed) { this.publiclyListed = publiclyListed; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getSeedKey() { return seedKey; }
    public void setSeedKey(String seedKey) { this.seedKey = seedKey; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}