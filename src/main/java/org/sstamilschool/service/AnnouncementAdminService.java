package org.sstamilschool.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.model.SstsAnnouncement;
import org.sstamilschool.repository.SstsAnnouncementRepository;
import org.sstamilschool.util.SchoolTime;

/**
 * CRUD for the public announcement marquee, under the super-admin module.
 *
 * <p>The form covers every field the marquee renders (title, message) plus the
 * two visibility levers confirmed with the user: the announce date, the optional
 * expiry date, and the manual is_active toggle.
 *
 * <p>No @CacheEvict here, unlike CalendarAdminService / TeamAdminService /
 * GalleryAdminService: AnnouncementService is intentionally uncached (see its
 * comment for why a 24h TTL would break expiry), so there is nothing to evict.
 */
@Service
public class AnnouncementAdminService {

    private final SstsAnnouncementRepository repository;

    public AnnouncementAdminService(SstsAnnouncementRepository repository) {
        this.repository = repository;
    }

    /** Every row, including inactive/expired ones, so they stay editable. */
    @Transactional(readOnly = true)
    public List<SstsAnnouncement> findAll() {
        return repository.findAllByOrderByAnnounceDateDesc();
    }

    @Transactional(readOnly = true)
    public SstsAnnouncement findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /** New announcement with sensible defaults for the admin form. */
    public SstsAnnouncement newAnnouncement() {
        SstsAnnouncement announcement = new SstsAnnouncement();
        // School date, not the server's: a notice posted at 22:00 Eastern would
        // otherwise be stamped with tomorrow's date.
        announcement.setAnnounceDate(SchoolTime.today());
        announcement.setActive(true);
        return announcement;
    }

    @Transactional
    public SstsAnnouncement create(SstsAnnouncement announcement) {
        return repository.save(announcement);
    }

    @Transactional
    public SstsAnnouncement update(Long id, SstsAnnouncement form) {
        SstsAnnouncement existing = repository.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setTitle(form.getTitle());
        existing.setMessage(form.getMessage());
        existing.setAnnounceDate(form.getAnnounceDate());
        existing.setExpiresOn(form.getExpiresOn());
        existing.setActive(form.isActive());
        return repository.save(existing);
    }

    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
