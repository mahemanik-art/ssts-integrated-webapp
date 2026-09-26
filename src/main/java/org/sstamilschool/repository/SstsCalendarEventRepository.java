package org.sstamilschool.repository;

import java.util.List;

import org.sstamilschool.model.SstsCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SstsCalendarEventRepository extends JpaRepository<SstsCalendarEvent, Long> {

    /**
     * Active events for one academic year only, ordered for display.
     * Historic years present in the table are never returned unless asked for.
     */
    List<SstsCalendarEvent> findByAcademicYearAndActiveTrueOrderByEventDateAsc(String academicYear);
}
