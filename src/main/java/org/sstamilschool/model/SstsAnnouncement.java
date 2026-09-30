package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A school announcement shown in the "School Announcements" marquee at the top
 * of the public home page.
 *
 * <p>Visibility has two independent levers:
 * <ul>
 *   <li>{@code active} -- manual switch, the same is_active column the calendar,
 *       gallery and team modules use.</li>
 *   <li>{@code expiresOn} -- optional auto-expiry. A row with a NULL expiresOn
 *       never expires; otherwise the announcement stops rendering the day after
 *       expiresOn, with no admin action needed.</li>
 * </ul>
 */
@Entity
@Table(name = "ssts_announcements")
public class SstsAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Date the announcement goes up; also the sort key for "most recent first". */
    @Column(name = "announce_date", nullable = false)
    private LocalDate announceDate;

    /** Optional auto-expiry. NULL = never expires. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Identifies rows that came from sql/data.sql, so the seed can delete
     * exactly its own rows when re-run. NULL for anything created through
     * /superadmin/announcements. Not rendered and not editable in the form.
     * See SstsGalleryEvent.seedKey.
     */
    @Column(name = "seed_key", length = 64)
    private String seedKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsAnnouncement() {
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

    /**
     * Single definition of "should this show on the home page", shared by the
     * repository query and the admin list so the two can never disagree.
     */
    public boolean isVisibleOn(LocalDate today) {
        return active && (expiresOn == null || !expiresOn.isBefore(today));
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDate getAnnounceDate() { return announceDate; }
    public void setAnnounceDate(LocalDate announceDate) { this.announceDate = announceDate; }

    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
