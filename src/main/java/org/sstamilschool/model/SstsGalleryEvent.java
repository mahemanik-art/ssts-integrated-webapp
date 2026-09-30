package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * One entry in the public Event Gallery (/gallery).
 *
 * <p>Images follow the same rule as team photos and the school logo: the file
 * lives under src/main/resources/static/images/gallery/ and is served by Spring
 * at /images/..., while this table stores only the relative path in imageUrl.
 * No binary upload, no external URL, no second storage pattern. Leave imageUrl
 * blank and the page falls back to the first letter of the title.
 */
@Entity
@Table(name = "ssts_gallery_events")
public class SstsGalleryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** Relative static path, e.g. '/images/gallery/pongal.jpg'. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Extra photos for the card's hover slider, one per line. Each is a
     * RELATIVE static path under the event's own folder,
     * e.g. '/images/gallery/pongal/2.jpg'. First line order = slide order.
     * NULL or blank rows render as the single-image card.
     */
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Manual ordering. Lower sorts first; the public page then falls back to
     * eventDate descending within the same rank, so admins can pin a featured
     * entry to the front without rewriting dates.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Identifies rows that came from sql/data.sql, so the seed can delete
     * exactly its own rows when re-run. NULL for anything created through
     * /superadmin/gallery. Not rendered anywhere and not editable in the form --
     * it exists purely so the seed cleanup can never match an admin-created
     * entry that happens to share a title.
     */
    @Column(name = "seed_key", length = 64)
    private String seedKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsGalleryEvent() {
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

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }

    /**
     * Slider view of imageUrls: one path per element, blank lines dropped,
     * never null. imageUrl is NOT included automatically -- callers decide.
     * Empty when the event has no uploaded slider photos.
     */
    public List<String> getSlides() {
        if (imageUrls == null || imageUrls.isBlank()) return List.of();
        return Arrays.stream(imageUrls.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Inverse of getSlides; null-safe. */
    public void setSlides(List<String> slides) {
        this.imageUrls = (slides == null || slides.isEmpty()) ? null
                : String.join("\n", slides);
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
