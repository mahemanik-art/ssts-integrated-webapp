package org.sstamilschool.util;

import java.time.LocalDate;

/**
 * Academic year helper for the school's Aug-Jul cycle.
 * On or after August 1 the year starts in the current calendar year,
 * otherwise it started in the previous calendar year.
 * Example: 2026-08-01 .. 2027-07-31 -> "2026-2027".
 */
public final class AcademicYear {

    private AcademicYear() {
    }

    public static String of(LocalDate date) {
        int startYear = date.getMonthValue() >= 8 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }

    public static String current() {
        // SchoolTime, not LocalDate.now(): the year rolls over on Aug 1, and the
        // server runs in UTC while the school is US Eastern, so for a few hours
        // on July 31 the server would report the new year early. Kept in step
        // with announcement dates, which are also school-zone.
        return of(SchoolTime.today());
    }

    /** Human-readable label for templates, e.g. "2026 – 2027". */
    public static String label(String academicYear) {
        return academicYear.replace("-", " – ");
    }
}
