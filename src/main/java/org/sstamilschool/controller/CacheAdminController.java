package org.sstamilschool.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.GalleryService;
import org.sstamilschool.service.TeamService;

@RestController
public class CacheAdminController {

    private final TeamService teamService;
    private final CalendarService calendarService;
    private final GalleryService galleryService;

    public CacheAdminController(TeamService teamService, CalendarService calendarService,
                                GalleryService galleryService) {
        this.teamService = teamService;
        this.calendarService = calendarService;
        this.galleryService = galleryService;
    }

    @PatchMapping("/api/team/cache")
    public ResponseEntity<Map<String, Object>> reloadTeamCache() {
        teamService.evictTeamMembers();
        int count = teamService.getPublicTeamMembers().size();
        return ResponseEntity.ok(Map.of(
                "status", "reloaded",
                "resource", "team",
                "count", count,
                "reloadedAt", Instant.now().toString()));
    }

    @PatchMapping("/api/calendar/cache")
    public ResponseEntity<Map<String, Object>> reloadCalendarCache() {
        calendarService.evictCalendarEvents();
        var calendar = calendarService.getCurrentCalendar();
        return ResponseEntity.ok(Map.of(
                "status", "reloaded",
                "resource", "calendar",
                "academicYear", calendar.academicYear(),
                "count", calendar.totalEvents(),
                "reloadedAt", Instant.now().toString()));
    }

    /**
     * Gallery is cached the same way team and calendar are, so it needs the same
     * escape hatch: CRUD under /superadmin/** only evicts the cache on the
     * instance that handled the write, so a hand-run SQL edit (or a multi-instance
     * deploy) otherwise leaves stale rows served for up to the 24h TTL with no
     * way to clear them out of band.
     */
    @PatchMapping("/api/gallery/cache")
    public ResponseEntity<Map<String, Object>> reloadGalleryCache() {
        galleryService.evictGalleryEvents();
        int count = galleryService.getPublicEvents().size();
        return ResponseEntity.ok(Map.of(
                "status", "reloaded",
                "resource", "gallery",
                "count", count,
                "reloadedAt", Instant.now().toString()));
    }
}
