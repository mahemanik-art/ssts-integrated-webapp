package org.sstamilschool.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.dto.CalendarView;
import org.sstamilschool.dto.CalendarView.EventItem;
import org.sstamilschool.dto.CalendarView.KeyEvent;
import org.sstamilschool.dto.CalendarView.MonthGroup;
import org.sstamilschool.model.SstsCalendarEvent;
import org.sstamilschool.repository.SstsCalendarEventRepository;
import org.sstamilschool.util.AcademicYear;

/**
 * Builds the render-ready calendar for the /calendar page.
 * Only the current academic year is ever shown — historic rows in the
 * table are filtered out by the academic_year column, not by deletion.
 * Results are cached for ~24h (app.cache.calendar-ttl-hours) because the
 * calendar changes rarely within an academic year.
 */
@Service
public class CalendarService {

    private static final Locale LOCALE = Locale.US;

    private final SstsCalendarEventRepository repository;

    public CalendarService(SstsCalendarEventRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = CacheConfig.CALENDAR_EVENTS, key = "'current'")
    @Transactional(readOnly = true)
    public CalendarView getCurrentCalendar() {        String year = AcademicYear.current();
        List<SstsCalendarEvent> events =
                repository.findByAcademicYearAndActiveTrueOrderByEventDateAsc(year);

        if (events.isEmpty()) {
            return CalendarView.empty();
        }

        // Group by month, preserving chronological order
        Map<String, List<EventItem>> months = new LinkedHashMap<>();
        List<String> holidays = new ArrayList<>();
        List<String> testDates = new ArrayList<>();
        List<KeyEvent> keyEvents = new ArrayList<>();

        for (SstsCalendarEvent event : events) {
            String monthLabel = monthLabel(event.getEventDate());
            months.computeIfAbsent(monthLabel, k -> new ArrayList<>())
                    .add(toItem(event));

            String type = event.getEventType();
            String dayLabel = dayLabel(event);
            String title = event.getTitle();

            if ("holiday".equals(type)) {
                holidays.add(dayLabel + " " + monthLabel.replace(" 20", ", "));
            } else if (title.startsWith("Test")) {
                testDates.add(dayLabel + " " + monthLabel.replace(" 20", ", ") + " — " + title);
            }
            if (isKeyEvent(title)) {
                keyEvents.add(new KeyEvent(title, dayLabel + " " + monthLabel));
            }
        }

        List<MonthGroup> monthGroups = months.entrySet().stream()
                .map(e -> new MonthGroup(e.getKey(), List.copyOf(e.getValue())))
                .toList();

        return new CalendarView(year, AcademicYear.label(year), monthGroups,
                List.copyOf(holidays), List.copyOf(testDates), List.copyOf(keyEvents),
                events.size());
    }

    @CacheEvict(value = CacheConfig.CALENDAR_EVENTS, allEntries = true)
    public void evictCalendarEvents() {
    }

    private static EventItem toItem(SstsCalendarEvent event) {
        String description = event.getDescription();
        String title = description == null || description.isBlank()
                ? event.getTitle()
                : event.getTitle() + " · " + description;
        return new EventItem(dayLabel(event), title, event.getEventType());
    }

    /** "7th" for single-day, "21st–25th" for multi-day events. */
    private static String dayLabel(SstsCalendarEvent event) {
        LocalDate start = event.getEventDate();
        LocalDate end = event.getEndDate();
        if (end == null || end.equals(start)) {
            return ordinal(start.getDayOfMonth());
        }
        // Same month: compact range; cross-month: full both sides
        if (end.getMonth() == start.getMonth()) {
            return ordinal(start.getDayOfMonth()) + "–" + ordinal(end.getDayOfMonth());
        }
        return start.getMonth().getDisplayName(TextStyle.SHORT, LOCALE) + " "
                + start.getDayOfMonth() + " – "
                + end.getMonth().getDisplayName(TextStyle.SHORT, LOCALE) + " "
                + end.getDayOfMonth();
    }

    private static String ordinal(int day) {
        if (day >= 11 && day <= 13) return day + "th";
        return switch (day % 10) {
            case 1 -> day + "st";
            case 2 -> day + "nd";
            case 3 -> day + "rd";
            default -> day + "th";
        };
    }

    private static String monthLabel(LocalDate date) {
        return date.getMonth().getDisplayName(TextStyle.FULL, LOCALE) + " " + date.getYear();
    }

    /** Titles that deserve a spot in the "Key Events" summary card. */
    private static boolean isKeyEvent(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("term begins") || lower.contains("term ends")
                || lower.contains("celebration") || lower.contains("annual day")
                || lower.contains("graduation") || lower.contains("final exam");
    }
}
