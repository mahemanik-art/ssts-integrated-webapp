package org.sstamilschool.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.dto.ReportsView;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.repository.SstsAnnouncementRepository;
import org.sstamilschool.repository.SstsCalendarEventRepository;
import org.sstamilschool.repository.SstsGalleryEventRepository;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.util.SchoolTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = { ReportsServiceTest.TestConfig.class })
class ReportsServiceTest {

    @Autowired ReportsService reportsService;
    @Autowired SstsUserRepository userRepository;
    @Autowired SstsRoleRepository roleRepository;
    @Autowired SstsCalendarEventRepository calendarRepository;
    @Autowired SstsGalleryEventRepository galleryRepository;
    @Autowired SstsAnnouncementRepository announcementRepository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsUserRepository userRepository() {
            return mock(SstsUserRepository.class);
        }

        @Bean
        SstsRoleRepository roleRepository() {
            return mock(SstsRoleRepository.class);
        }

        @Bean
        SstsCalendarEventRepository calendarEventRepository() {
            return mock(SstsCalendarEventRepository.class);
        }

        @Bean
        SstsGalleryEventRepository galleryEventRepository() {
            return mock(SstsGalleryEventRepository.class);
        }

        @Bean
        SstsAnnouncementRepository announcementRepository() {
            return mock(SstsAnnouncementRepository.class);
        }

        @Bean
        ReportsService reportsService(SstsUserRepository userRepository,
                                      SstsRoleRepository roleRepository,
                                      SstsCalendarEventRepository calendarRepository,
                                      SstsGalleryEventRepository galleryRepository,
                                      SstsAnnouncementRepository announcementRepository) {
            return new ReportsService(userRepository, roleRepository, calendarRepository,
                    galleryRepository, announcementRepository);
        }
    }

    @BeforeEach
    void setup() {
        clearInvocations(userRepository);
        clearInvocations(roleRepository);
        clearInvocations(calendarRepository);
        clearInvocations(galleryRepository);
        clearInvocations(announcementRepository);

        // Default stubs for all count methods
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByIsActiveTrue()).thenReturn(85L);
        when(userRepository.countByIsActiveFalse()).thenReturn(15L);
        when(userRepository.countByEmailVerifiedFalse()).thenReturn(10L);
        when(userRepository.countByUserType("parent")).thenReturn(70L);
        when(userRepository.countByUserType("staff")).thenReturn(15L);
        when(userRepository.countByUserType("volunteer")).thenReturn(10L);
        when(userRepository.countByUserType("admin")).thenReturn(5L);
        when(userRepository.countByUserTypeAndIsActive("parent", true)).thenReturn(65L);
        when(userRepository.countByUserTypeAndIsActive("staff", true)).thenReturn(13L);
        when(userRepository.countByUserTypeAndIsActive("volunteer", true)).thenReturn(8L);
        when(userRepository.countByUserTypeAndIsActive("admin", true)).thenReturn(5L);

        when(roleRepository.count()).thenReturn(4L);
        when(roleRepository.countByIsActiveTrue()).thenReturn(4L);
        // Roles returned by findAllByOrderByNameAsc() -- alphabetical order
        SstsRole roleParent = new SstsRole();
        roleParent.setName("parent");
        roleParent.setId(4L);
        SstsRole roleStaff = new SstsRole();
        roleStaff.setName("staff");
        roleStaff.setId(3L);
        SstsRole roleSuperAdmin = new SstsRole();
        roleSuperAdmin.setName("super_admin");
        roleSuperAdmin.setId(1L);
        SstsRole roleVolCoord = new SstsRole();
        roleVolCoord.setName("volunteer_coordinator");
        roleVolCoord.setId(2L);
        when(roleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(roleParent, roleStaff, roleSuperAdmin, roleVolCoord));
        when(userRepository.countByRoleId(1L)).thenReturn(2L);
        when(userRepository.countByRoleId(2L)).thenReturn(10L);
        when(userRepository.countByRoleId(3L)).thenReturn(15L);
        when(userRepository.countByRoleId(4L)).thenReturn(70L);

        when(calendarRepository.countByAcademicYear("2026-2027")).thenReturn(40L);
        when(calendarRepository.countByAcademicYearAndActiveTrue("2026-2027")).thenReturn(38L);
        when(calendarRepository.countByEventType("working")).thenReturn(30L);
        when(calendarRepository.countByEventType("holiday")).thenReturn(10L);
        when(calendarRepository.findDistinctAcademicYears()).thenReturn(List.of("2025-2026", "2026-2027"));
        when(calendarRepository.countByAcademicYear("2025-2026")).thenReturn(35L);
        when(calendarRepository.countByAcademicYearAndActiveTrue("2025-2026")).thenReturn(35L);

        when(galleryRepository.countByActiveTrue()).thenReturn(25L);
        when(galleryRepository.countByActiveFalse()).thenReturn(5L);

        when(announcementRepository.countByActiveTrue()).thenReturn(8L);
        when(announcementRepository.countByExpiresOnIsNull()).thenReturn(3L);
        // Must key off SchoolTime.today(), NOT a literal date. ReportsService
        // asks for countByExpiresOnBefore(SchoolTime.today()), so a hardcoded
        // date silently stops matching the moment that day passes in the
        // school's timezone and the mock returns 0 for an unstubbed call. That
        // is what happened here on 2026-09-27 -- the suite was green on the
        // 26th and red on the 27th with no code change in between.
        when(announcementRepository.countByExpiresOnBefore(SchoolTime.today())).thenReturn(2L);
    }

    @Test
    void buildAssemblesCompleteView() {
        ReportsView view = reportsService.build();

        assertThat(view.generatedOn()).isNotNull();
        assertThat(view.currentAcademicYear()).isEqualTo("2026-2027");

        // Accounts
        assertThat(view.accounts().total()).isEqualTo(100L);
        assertThat(view.accounts().active()).isEqualTo(85L);
        assertThat(view.accounts().deactivated()).isEqualTo(15L);
        assertThat(view.accounts().unverifiedEmail()).isEqualTo(10L);
        assertThat(view.accounts().byUserType()).containsEntry("parent", 70L);
        assertThat(view.accounts().byUserType()).containsEntry("staff", 15L);
        assertThat(view.accounts().byUserType()).containsEntry("volunteer", 10L);
        assertThat(view.accounts().byUserType()).containsEntry("admin", 5L);
        assertThat(view.accounts().activeByUserType()).containsEntry("parent", 65L);
        assertThat(view.accounts().activeByUserType()).containsEntry("staff", 13L);
        assertThat(view.accounts().activeByUserType()).containsEntry("volunteer", 8L);
        assertThat(view.accounts().activeByUserType()).containsEntry("admin", 5L);

        // Roles
        assertThat(view.roles().total()).isEqualTo(4L);
        assertThat(view.roles().active()).isEqualTo(4L);
        assertThat(view.roles().breakdown()).hasSize(4);
        assertThat(view.roles().breakdown().get(0).roleName()).isEqualTo("parent");
        assertThat(view.roles().breakdown().get(0).userCount()).isEqualTo(70L);
        assertThat(view.roles().breakdown().get(1).roleName()).isEqualTo("staff");
        assertThat(view.roles().breakdown().get(1).userCount()).isEqualTo(15L);
        assertThat(view.roles().breakdown().get(2).roleName()).isEqualTo("super_admin");
        assertThat(view.roles().breakdown().get(2).userCount()).isEqualTo(2L);
        assertThat(view.roles().breakdown().get(3).roleName()).isEqualTo("volunteer_coordinator");
        assertThat(view.roles().breakdown().get(3).userCount()).isEqualTo(10L);

        // Content - Calendar
        assertThat(view.content().calendar().totalCurrentYear()).isEqualTo(40L);
        assertThat(view.content().calendar().activeCurrentYear()).isEqualTo(38L);
        assertThat(view.content().calendar().workingCount()).isEqualTo(30L);
        assertThat(view.content().calendar().holidayCount()).isEqualTo(10L);
        assertThat(view.content().calendar().byAcademicYear()).hasSize(2);

        // Content - Gallery
        assertThat(view.content().gallery().active()).isEqualTo(25L);
        assertThat(view.content().gallery().inactive()).isEqualTo(5L);

        // Content - Announcements
        assertThat(view.content().announcements().active()).isEqualTo(8L);
        assertThat(view.content().announcements().neverExpiring()).isEqualTo(3L);
        assertThat(view.content().announcements().expiredSoFar()).isEqualTo(2L);
    }
}
