package org.sstamilschool.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.model.SstsAnnouncement;
import org.sstamilschool.repository.SstsAnnouncementRepository;
import org.sstamilschool.util.SchoolTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests AnnouncementService + AnnouncementAdminService against a mocked
 * repository (no live DB) -- mirrors the other admin service tests.
 *
 * <p>The important behaviour is the visibility rule, so the expiry boundaries
 * around LocalDate.now() are asserted directly.
 */
@SpringJUnitConfig(classes = { AnnouncementAdminServiceTest.TestConfig.class })
class AnnouncementAdminServiceTest {

    @Autowired AnnouncementService announcementService;
    @Autowired AnnouncementAdminService adminService;
    @Autowired SstsAnnouncementRepository repository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsAnnouncementRepository announcementRepository() {
            return mock(SstsAnnouncementRepository.class);
        }

        @Bean
        AnnouncementService announcementService(SstsAnnouncementRepository repository) {
            return new AnnouncementService(repository);
        }

        @Bean
        AnnouncementAdminService announcementAdminService(SstsAnnouncementRepository repository) {
            return new AnnouncementAdminService(repository);
        }
    }

    @BeforeEach
    void setup() {
        clearInvocations(repository);
    }

    @Test void visibleQueryIsGivenTodaysDate() {
        when(repository.findVisibleOn(any())).thenReturn(List.of());
        announcementService.getVisibleAnnouncements();
        verify(repository).findVisibleOn(SchoolTime.today());
    }

    @Test void announcementIsVisibleWhenActiveAndNotExpired() {
        SstsAnnouncement a = announcement("Live", LocalDate.now().plusDays(30), true);
        assertThat(a.isVisibleOn(LocalDate.now())).isTrue();
    }

    @Test void announcementNeverExpiresWhenExpiryIsNull() {
        SstsAnnouncement a = announcement("Forever", null, true);
        assertThat(a.isVisibleOn(LocalDate.now())).isTrue();
        assertThat(a.isVisibleOn(LocalDate.now().plusYears(10))).isTrue();
    }

    @Test void announcementIsHiddenOnceExpiryHasPassed() {
        SstsAnnouncement a = announcement("Old", LocalDate.now().minusDays(1), true);
        assertThat(a.isVisibleOn(LocalDate.now())).isFalse();
    }

    @Test void announcementIsVisibleOnItsExpiryDayThenNotAfter() {
        // expires_on is inclusive: it shows through the expiry date itself.
        SstsAnnouncement a = announcement("Today", LocalDate.now(), true);
        assertThat(a.isVisibleOn(LocalDate.now())).isTrue();
        assertThat(a.isVisibleOn(LocalDate.now().plusDays(1))).isFalse();
    }

    @Test void inactiveAnnouncementIsHiddenEvenWhenUnexpired() {
        SstsAnnouncement a = announcement("Hidden", null, false);
        assertThat(a.isVisibleOn(LocalDate.now())).isFalse();
    }

    @Test void createPersistsAllFields() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Helper signature is (title, expiresOn, active); announceDate is today.
        SstsAnnouncement form = announcement("Registration Open", LocalDate.now().plusDays(14), true);
        form.setMessage("Enrolment is open.");

        SstsAnnouncement saved = adminService.create(form);

        assertThat(saved.getTitle()).isEqualTo("Registration Open");
        assertThat(saved.getMessage()).isEqualTo("Enrolment is open.");
        assertThat(saved.getAnnounceDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getExpiresOn()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(saved.isActive()).isTrue();
    }

    @Test void updateCoversEveryField() {
        SstsAnnouncement existing = announcement("Old", LocalDate.now().minusDays(5), false);
        SstsAnnouncement form = announcement("New", LocalDate.now(), true);
        form.setMessage("New body");
        form.setExpiresOn(LocalDate.now().plusDays(7));
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any())).thenReturn(existing);

        adminService.update(1L, form);

        assertThat(existing.getTitle()).isEqualTo("New");
        assertThat(existing.getMessage()).isEqualTo("New body");
        assertThat(existing.getAnnounceDate()).isEqualTo(LocalDate.now());
        assertThat(existing.getExpiresOn()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(existing.isActive()).isTrue();
    }

    @Test void updateCanClearTheExpiryDate() {
        SstsAnnouncement existing = announcement("Old", LocalDate.now(), true);
        existing.setExpiresOn(LocalDate.now().plusDays(3));
        SstsAnnouncement form = announcement("Old", LocalDate.now(), true);
        form.setExpiresOn(null); // admin left the field blank
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));

        adminService.update(1L, form);

        assertThat(existing.getExpiresOn()).isNull();
    }

    @Test void updateMissingAnnouncementIsNoOp() {
        when(repository.findById(99L)).thenReturn(java.util.Optional.empty());
        assertThat(adminService.update(99L, announcement("Ghost", null, true))).isNull();
        verify(repository, never()).save(any());
    }

    @Test void deleteRemovesTheRow() {
        when(repository.existsById(5L)).thenReturn(true);
        assertThat(adminService.delete(5L)).isTrue();
        verify(repository).deleteById(5L);
    }

    @Test void deleteMissingAnnouncementIsNoOp() {
        when(repository.existsById(99L)).thenReturn(false);
        assertThat(adminService.delete(99L)).isFalse();
        verify(repository, never()).deleteById(any());
    }

    @Test void newAnnouncementDefaultsToTodayAndActive() {
        SstsAnnouncement a = adminService.newAnnouncement();
        assertThat(a.getAnnounceDate()).isEqualTo(SchoolTime.today());
        assertThat(a.isActive()).isTrue();
        assertThat(a.getExpiresOn()).isNull();
    }

    @Test void findAllUsesTheAdminQuerySoHiddenRowsStayEditable() {
        SstsAnnouncement hidden = announcement("Hidden", LocalDate.now(), false);
        when(repository.findAllByOrderByAnnounceDateDesc()).thenReturn(List.of(hidden));
        assertThat(adminService.findAll()).containsExactly(hidden);
        // The admin list must not apply the visibility filter.
        verify(repository, never()).findVisibleOn(any());
    }

    @Test void serviceIsNotCachedSoExpiryTakesEffectImmediately() {
        // Two consecutive reads hit the repository twice: announcements are
        // deliberately uncached, otherwise an expired row would linger for a TTL.
        when(repository.findVisibleOn(any())).thenReturn(List.of());
        announcementService.getVisibleAnnouncements();
        announcementService.getVisibleAnnouncements();
        verify(repository, times(2)).findVisibleOn(any());
    }

    private static SstsAnnouncement announcement(String title, LocalDate expiresOn, boolean active) {
        SstsAnnouncement a = new SstsAnnouncement();
        a.setTitle(title);
        a.setMessage("body");
        a.setAnnounceDate(LocalDate.now());
        a.setExpiresOn(expiresOn);
        a.setActive(active);
        return a;
    }
}
