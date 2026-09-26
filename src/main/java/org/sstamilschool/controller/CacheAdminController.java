package org.sstamilschool.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.TeamService;

@RestController
public class CacheAdminController {

    private final TeamService teamService;
    private final CalendarService calendarService;

    public CacheAdminController(TeamService teamService, CalendarService calendarService) {
        this.teamService = teamService;
        this.calendarService = calendarService;
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
}
