package org.sstamilschool.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Immutable view object for the super-admin Reports page.
 * Built by ReportsService.build() from live repository counts.
 */
public record ReportsView(
        LocalDate generatedOn,
        String currentAcademicYear,
        Accounts accounts,
        Roles roles,
        Content content) {

    public record Accounts(
            long total,
            long active,
            long deactivated,
            long unverifiedEmail,
            Map<String, Long> byUserType,
            Map<String, Long> activeByUserType) {
    }

    public record Roles(
            long total,
            long active,
            List<RoleBreakdown> breakdown) {
    }

    public record RoleBreakdown(String roleName, long userCount) {
    }

    public record Content(
            CalendarContent calendar,
            GalleryContent gallery,
            AnnouncementsContent announcements) {
    }

    public record CalendarContent(
            long totalCurrentYear,
            long activeCurrentYear,
            long workingCount,
            long holidayCount,
            List<AcademicYearBreakdown> byAcademicYear) {
    }

    public record AcademicYearBreakdown(String academicYear, long total, long active) {
    }

    public record GalleryContent(
            long active,
            long inactive) {
    }

    public record AnnouncementsContent(
            long active,
            long neverExpiring,
            long expiredSoFar) {
    }
}
