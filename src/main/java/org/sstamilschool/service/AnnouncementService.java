package org.sstamilschool.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.model.SstsAnnouncement;
import org.sstamilschool.repository.SstsAnnouncementRepository;
import org.sstamilschool.util.SchoolTime;

/**
 * Read side of the public "School Announcements" marquee on the home page.
 *
 * <p>DELIBERATELY NOT CACHED, unlike TeamService / CalendarService /
 * GalleryService. Those three back pages whose data changes rarely, so a 24h
 * Caffeine TTL is harmless. Announcements are the opposite: expiresOn is
 * time-based, so a cached list would keep rendering an announcement for up to a
 * full TTL AFTER it expired (cached 09:00, expires 12:00, still visible until
 * 09:00 next day), and a newly posted notice would not appear for up to 24h.
 * That breaks the "old announcements stop showing automatically" requirement.
 *
 * <p>The query is a single indexed read over a handful of rows, which is cheap
 * on the site's highest-traffic page. If that ever matters, cache it with a
 * short TTL (minutes, not hours) rather than reusing app.cache.*-ttl-hours.
 */
@Service
public class AnnouncementService {

    private final SstsAnnouncementRepository repository;

    public AnnouncementService(SstsAnnouncementRepository repository) {
        this.repository = repository;
    }

    /** Active, non-expired announcements, most recent first. */
    @Transactional(readOnly = true)
    public List<SstsAnnouncement> getVisibleAnnouncements() {
        // SchoolTime, not LocalDate.now(): expires_on is compared against "today",
        // and the server runs in UTC while the school is US Eastern.
        return repository.findVisibleOn(SchoolTime.today());
    }
}
