package org.sstamilschool.repository;
import org.sstamilschool.model.SstsAnnouncement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SstsAnnouncementRepository extends JpaRepository<SstsAnnouncement, Long> {

    /**
     * What the public home-page marquee renders: active rows that have not
     * expired, most recent announcement first.
     *
     * <p>Written as an explicit @Query rather than a derived method because the
     * "expires_on IS NULL OR expires_on >= :today" condition would otherwise
     * need an unreadably long derived name (and a misleading
     * AndExpiresOnIsNullOr... precedence).
     */
    @Query("SELECT a FROM SstsAnnouncement a "
         + "WHERE a.active = true AND (a.expiresOn IS NULL OR a.expiresOn >= :today) "
         + "ORDER BY a.announceDate DESC")
    List<SstsAnnouncement> findVisibleOn(@Param("today") LocalDate today);

    /** Admin list: every row regardless of active/expiry, newest first. */
    List<SstsAnnouncement> findAllByOrderByAnnounceDateDesc();

    /** Active announcements for Reports. */
    long countByActiveTrue();

    /** Never-expiring announcements (expiresOn IS NULL) for Reports. */
    long countByExpiresOnIsNull();

    /** Expired announcements (expiresOn < date) for Reports. */
    long countByExpiresOnBefore(LocalDate date);
}
