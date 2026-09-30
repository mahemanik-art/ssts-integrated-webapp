package org.sstamilschool.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.dto.ReportsView;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.repository.SstsAnnouncementRepository;
import org.sstamilschool.repository.SstsCalendarEventRepository;
import org.sstamilschool.repository.SstsGalleryEventRepository;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.util.AcademicYear;
import org.sstamilschool.util.SchoolTime;

/**
 * Read-only aggregation service for the super-admin Reports page.
 *
 * <p>No caching annotations -- reports must always read live data. The codebase
 * already has a precedent for deliberately uncached expiry-sensitive reads
 * (AnnouncementService). This service aggregates in Java from the repository
 * count methods; no JPQL projection queries or GROUP BY are used.
 */
@Service
public class ReportsService {

    private final SstsUserRepository userRepository;
    private final SstsRoleRepository roleRepository;
    private final SstsCalendarEventRepository calendarRepository;
    private final SstsGalleryEventRepository galleryRepository;
    private final SstsAnnouncementRepository announcementRepository;

    public ReportsService(SstsUserRepository userRepository,
                          SstsRoleRepository roleRepository,
                          SstsCalendarEventRepository calendarRepository,
                          SstsGalleryEventRepository galleryRepository,
                          SstsAnnouncementRepository announcementRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.calendarRepository = calendarRepository;
        this.galleryRepository = galleryRepository;
        this.announcementRepository = announcementRepository;
    }

    /**
     * Builds a complete ReportsView from live database counts.
     */
    @Transactional(readOnly = true)
    public ReportsView build() {
        LocalDate today = SchoolTime.today();
        String currentAcademicYear = AcademicYear.current();

        // --- Accounts ---
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByIsActiveTrue();
        long deactivatedUsers = userRepository.countByIsActiveFalse();
        long unverifiedEmail = userRepository.countByEmailVerifiedFalse();

        String[] userTypes = {"parent", "staff", "volunteer", "admin"};
        Map<String, Long> byUserType = Map.of(
                "parent", userRepository.countByUserType("parent"),
                "staff", userRepository.countByUserType("staff"),
                "volunteer", userRepository.countByUserType("volunteer"),
                "admin", userRepository.countByUserType("admin"));
        Map<String, Long> activeByUserType = Map.of(
                "parent", userRepository.countByUserTypeAndIsActive("parent", true),
                "staff", userRepository.countByUserTypeAndIsActive("staff", true),
                "volunteer", userRepository.countByUserTypeAndIsActive("volunteer", true),
                "admin", userRepository.countByUserTypeAndIsActive("admin", true));

        ReportsView.Accounts accounts = new ReportsView.Accounts(
                totalUsers, activeUsers, deactivatedUsers, unverifiedEmail,
                byUserType, activeByUserType);

        // --- Roles ---
        long totalRoles = roleRepository.count();
        long activeRoles = roleRepository.countByIsActiveTrue();
        List<SstsRole> allRoles = roleRepository.findAllByOrderByNameAsc();
        List<ReportsView.RoleBreakdown> roleBreakdown = allRoles.stream()
                .map(role -> new ReportsView.RoleBreakdown(
                        role.getName(),
                        userRepository.countByRoleId(role.getId())))
                .collect(Collectors.toList());

        ReportsView.Roles roles = new ReportsView.Roles(totalRoles, activeRoles, roleBreakdown);

        // --- Content ---
        // Calendar
        long calendarTotalCurrentYear = calendarRepository.countByAcademicYear(currentAcademicYear);
        long calendarActiveCurrentYear = calendarRepository.countByAcademicYearAndActiveTrue(currentAcademicYear);
        long workingCount = calendarRepository.countByEventType("working");
        long holidayCount = calendarRepository.countByEventType("holiday");

        List<String> distinctYears = calendarRepository.findDistinctAcademicYears();
        List<ReportsView.AcademicYearBreakdown> byAcademicYear = distinctYears.stream()
                .map(year -> new ReportsView.AcademicYearBreakdown(
                        year,
                        calendarRepository.countByAcademicYear(year),
                        calendarRepository.countByAcademicYearAndActiveTrue(year)))
                .collect(Collectors.toList());

        ReportsView.CalendarContent calendar = new ReportsView.CalendarContent(
                calendarTotalCurrentYear, calendarActiveCurrentYear,
                workingCount, holidayCount, byAcademicYear);

        // Gallery
        long galleryActive = galleryRepository.countByActiveTrue();
        long galleryInactive = galleryRepository.countByActiveFalse();

        ReportsView.GalleryContent gallery = new ReportsView.GalleryContent(galleryActive, galleryInactive);

        // Announcements
        long announcementsActive = announcementRepository.countByActiveTrue();
        long announcementsNeverExpiring = announcementRepository.countByExpiresOnIsNull();
        long announcementsExpired = announcementRepository.countByExpiresOnBefore(today);

        ReportsView.AnnouncementsContent announcements = new ReportsView.AnnouncementsContent(
                announcementsActive, announcementsNeverExpiring, announcementsExpired);

        ReportsView.Content content = new ReportsView.Content(calendar, gallery, announcements);

        return new ReportsView(today, currentAcademicYear, accounts, roles, content);
    }
}
