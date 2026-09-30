package org.sstamilschool.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

/**
 * "Today" for school business logic, resolved in the SCHOOL's timezone rather
 * than the server's.
 *
 * <p>Do not call {@link LocalDate#now()} for anything a member of staff looks at.
 * The app runs on Render in UTC while the school is US Eastern, so the server's
 * date runs 4-5 hours AHEAD of the school's for part of every evening. That
 * matters wherever a date is compared against stored data rather than merely
 * displayed:
 *
 * <ul>
 *   <li>Announcements expire on {@code expires_on >= today}. In the 20:00-24:00
 *       window an announcement set to expire on date D would be hidden from
 *       20:00 on D instead of at the end of D.</li>
 *   <li>The academic year rolls over on August 1, so for a few hours each
 *       July 31 evening the server would report the new year while the school
 *       was still in the old one.</li>
 * </ul>
 *
 * <p>Override with the {@value #ZONE_ENV_VAR} environment variable; an
 * unparseable value falls back to {@value #DEFAULT_ZONE} rather than failing
 * the request.
 */
public final class SchoolTime {

    public static final String ZONE_ENV_VAR = "APP_TIMEZONE";
    public static final String DEFAULT_ZONE = "America/New_York";

    private SchoolTime() {
    }

    public static ZoneId zone() {
        String configured = System.getenv(ZONE_ENV_VAR);
        if (configured == null || configured.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (ZoneRulesException e) {
            return ZoneId.of(DEFAULT_ZONE);
        }
    }

    /** The current date at the school. */
    public static LocalDate today() {
        return LocalDate.now(zone());
    }
}
