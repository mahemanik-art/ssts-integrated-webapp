package org.sstamilschool.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsCalendarEvent;
import org.sstamilschool.repository.SstsCalendarEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache + CRUD test for CalendarAdminService, wired with CacheConfig and a
 * mocked repository (no live DB), mirroring TeamServiceCacheTest.
 */
@SpringJUnitConfig(classes = { CacheConfig.class, CalendarAdminServiceTest.TestConfig.class })
class CalendarAdminServiceTest {

    @Autowired CalendarAdminService adminService;
    @Autowired CalendarService calendarService;
    @Autowired SstsCalendarEventRepository repository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsCalendarEventRepository calendarEventRepository() {
            return mock(SstsCalendarEventRepository.class);
        }

        @Bean
        CalendarService calendarService(SstsCalendarEventRepository repository) {
            return new CalendarService(repository);
        }

        @Bean
        CalendarAdminService calendarAdminService(SstsCalendarEventRepository repository) {
            return new CalendarAdminService(repository);
        }
    }

    @BeforeEach
    void clearCache() {
        calendarService.evictCalendarEvents();
        clearInvocations(repository);
        SstsCalendarEvent seeded = new SstsCalendarEvent();
        seeded.setTitle("Seeded");
        seeded.setEventDate(LocalDate.of(2026, 9, 4));
        when(repository.findByAcademicYearAndActiveTrueOrderByEventDateAsc(any()))
                .thenReturn(List.of(seeded));
    }

    @Test void repeatedReadsHitDatabaseOnlyOnce() {
        calendarService.getCurrentCalendar();
        calendarService.getCurrentCalendar();
        verify(repository, times(1)).findByAcademicYearAndActiveTrueOrderByEventDateAsc(any());
    }

    @Test void createEvictsCacheSoPublicCalendarReloads() {
        SstsCalendarEvent event = new SstsCalendarEvent();
        event.setTitle("Pongal");
        event.setEventDate(LocalDate.of(2027, 1, 14));
        when(repository.save(any())).thenReturn(event);

        calendarService.getCurrentCalendar(); // warm cache
        adminService.create(event);           // write -> evicts

        calendarService.getCurrentCalendar();
        verify(repository, times(2)).findByAcademicYearAndActiveTrueOrderByEventDateAsc(any());
    }

    @Test void updateEvictsCache() {
        SstsCalendarEvent existing = new SstsCalendarEvent();
        existing.setId(1L);
        existing.setTitle("Old");
        existing.setEventDate(LocalDate.of(2026, 12, 1));
        SstsCalendarEvent form = new SstsCalendarEvent();
        form.setTitle("New");
        form.setEventDate(LocalDate.of(2026, 12, 2));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        calendarService.getCurrentCalendar();
        adminService.update(1L, form);

        assertThat(existing.getTitle()).isEqualTo("New");
        calendarService.getCurrentCalendar();
        verify(repository, times(2)).findByAcademicYearAndActiveTrueOrderByEventDateAsc(any());
    }

    @Test void deleteEvictsCache() {
        when(repository.existsById(5L)).thenReturn(true);

        calendarService.getCurrentCalendar();
        adminService.delete(5L);

        verify(repository).deleteById(5L);
        calendarService.getCurrentCalendar();
        verify(repository, times(2)).findByAcademicYearAndActiveTrueOrderByEventDateAsc(any());
    }

    @Test void deleteMissingEventIsNoOp() {
        when(repository.existsById(99L)).thenReturn(false);
        assertThat(adminService.delete(99L)).isFalse();
        verify(repository, times(0)).deleteById(99L);
    }

    @Test void newEventHasSensibleDefaults() {
        SstsCalendarEvent event = adminService.newEvent();
        assertThat(event.getEventType()).isEqualTo("working");
        assertThat(event.isActive()).isTrue();
        assertThat(event.getAcademicYear()).isNotBlank();
    }
}
