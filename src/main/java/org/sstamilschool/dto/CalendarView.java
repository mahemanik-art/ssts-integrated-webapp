package org.sstamilschool.dto;

import java.util.List;

import org.sstamilschool.util.AcademicYear;

/**
 * Render-ready view of the school calendar for the current academic year.
 * Historic academic years in the table are filtered out before this is built.
 */
public record CalendarView(
        String academicYear,
        String yearLabel,
        List<MonthGroup> months,
        List<String> holidays,
        List<String> testDates,
        List<KeyEvent> keyEvents,
        int totalEvents) {

    /** One month block on the calendar grid, e.g. "August 2026". */
    public record MonthGroup(String label, List<EventItem> items) {
    }

    /** One row inside a month: day label ("7th" or "21st–25th"), title, type. */
    public record EventItem(String dayLabel, String title, String type) {
    }

    /** Highlighted event for the summary section. */
    public record KeyEvent(String title, String date) {
    }

    public static CalendarView empty() {
        String year = AcademicYear.current();
        return new CalendarView(year, AcademicYear.label(year),
                List.of(), List.of(), List.of(), List.of(), 0);
    }
}
