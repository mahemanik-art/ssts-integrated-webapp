package org.sstamilschool.repository;

import java.util.List;

import org.sstamilschool.model.SstsCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SstsCalendarEventRepository extends JpaRepository<SstsCalendarEvent, Long> {

    /**
     * Active events for one academic year only, ordered for display.
     * Historic years present in the table are never returned unless asked for.
     */
    List<SstsCalendarEvent> findByAcademicYearAndActiveTrueOrderByEventDateAsc(String academicYear);

    /**
     * Distinct academic years present in the table, for the Reports breakdown.
     */
    @Query("SELECT DISTINCT e.academicYear FROM SstsCalendarEvent e ORDER BY e.academicYear")
    List<String> findDistinctAcademicYears();

    /** Total calendar events for a given academic year. */
    long countByAcademicYear(String academicYear);

    /** Active calendar events for a given academic year. */
    long countByAcademicYearAndActiveTrue(String academicYear);

    /** Calendar events by event type (working | holiday) across all years. */
    long countByEventType(String eventType);
}
