package org.sstamilschool.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.model.SstsRole;
import org.sstamilschool.repository.SstsRoleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RoleAdminService against a mocked SstsRoleRepository (no live DB),
 * mirroring the other admin service tests (e.g. AnnouncementAdminServiceTest).
 */
@SpringJUnitConfig(classes = { RoleAdminServiceTest.TestConfig.class })
class RoleAdminServiceTest {

    @Autowired RoleAdminService roleAdminService;
    @Autowired SstsRoleRepository roleRepository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsRoleRepository roleRepository() {
            return mock(SstsRoleRepository.class);
        }

        @Bean
        RoleAdminService roleAdminService(SstsRoleRepository repository) {
            return new RoleAdminService(repository);
        }
    }

    @BeforeEach
    void setup() {
        clearInvocations(roleRepository);
    }

    @Test void createAcceptsValidSnakeCaseName() {
        when(roleRepository.existsByName("content_editor")).thenReturn(false);
        when(roleRepository.countByNameIgnoreCase("content_editor")).thenReturn(0L);
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SstsRole saved = roleAdminService.create(role("ignored"), "content_editor");

        assertThat(saved.getName()).isEqualTo("content_editor");
        assertThat(saved.isActive()).isTrue();
        verify(roleRepository).save(any());
    }

    @Test void createRejectsBlankName() {
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsUppercaseName() {
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "Content_Editor"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsSpaceInName() {
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "content editor"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsDotInName() {
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "content.editor"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsDashInName() {
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "content-editor"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsLeadingDigit() {
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "1editor"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsNameLongerThan50Chars() {
        String tooLong = "a".repeat(51);
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsExistingName() {
        when(roleRepository.existsByName("content_editor")).thenReturn(true);
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "content_editor"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createRejectsCaseOnlyCollision() {
        when(roleRepository.existsByName("admin")).thenReturn(false);
        when(roleRepository.countByNameIgnoreCase("admin")).thenReturn(1L);
        assertThatThrownBy(() -> roleAdminService.create(role("ignored"), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void createCopiesAll21PrivilegeFlagsOntoSavedEntity() {
        when(roleRepository.existsByName("content_editor")).thenReturn(false);
        when(roleRepository.countByNameIgnoreCase("content_editor")).thenReturn(0L);
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SstsRole form = new SstsRole();
        setAllFlags(form, true);
        form.setDescription("All privileges");

        SstsRole saved = roleAdminService.create(form, "content_editor");

        assertThat(saved.getDescription()).isEqualTo("All privileges");
        assertThat(saved.isCanCreateUsers()).isTrue();
        assertThat(saved.isCanDeleteUsers()).isTrue();
        assertThat(saved.isCanViewDashboard()).isTrue();
        assertThat(saved.isCanSendNewsletters()).isTrue();
        assertThat(roleAdminService.enabledPrivilegeCount(saved)).isEqualTo(21);
    }

    @Test void updateRefusesToChangeSuperAdminFlags() {
        SstsRole protectedRole = new SstsRole();
        protectedRole.setId(1L);
        protectedRole.setName("super_admin");
        protectedRole.setActive(true);
        SstsRole form = role("any_name");
        setAllFlags(form, true);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(protectedRole));

        assertThatThrownBy(() -> roleAdminService.update(1L, form))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void updateRefusesToDeactivateSuperAdmin() {
        SstsRole protectedRole = new SstsRole();
        protectedRole.setId(1L);
        protectedRole.setName("super_admin");
        protectedRole.setActive(true);
        SstsRole form = new SstsRole();
        form.setActive(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(protectedRole));

        assertThatThrownBy(() -> roleAdminService.update(1L, form))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test void updateNeverChangesTheName() {
        SstsRole existing = role("editor");
        existing.setId(1L);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SstsRole form = new SstsRole();
        form.setName("trying_to_rename");
        form.setDescription("updated");
        form.setCanManageContent(true);

        roleAdminService.update(1L, form);

        assertThat(existing.getName()).isEqualTo("editor");
        verify(roleRepository).save(existing);
    }

    @Test void updateMissingIdReturnsNullAndNeverSaves() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(roleAdminService.update(99L, role("ghost"))).isNull();
        verify(roleRepository, never()).save(any());
    }

    @Test void deleteRefusesProtectedRole() {
        SstsRole protectedRole = new SstsRole();
        protectedRole.setId(1L);
        protectedRole.setName("super_admin");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(protectedRole));

        assertThatThrownBy(() -> roleAdminService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).delete(any());
    }

    @Test void deleteMissingIdReturnsFalse() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(roleAdminService.delete(99L)).isFalse();
        verify(roleRepository, never()).delete(any());
    }

    @Test void deleteOfNormalRoleReturnsTrueAndDeletes() {
        SstsRole existing = role("content_editor");
        existing.setId(1L);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(roleAdminService.delete(1L)).isTrue();
        // The service deletes by entity (roleRepository.delete(existing)), not
        // deleteById(id); verify the actual call the service makes.
        verify(roleRepository).delete(existing);
    }

    @Test void findAllDelegatesToOrderByQuery() {
        List<SstsRole> roles = List.of(role("admin"), role("editor"));
        when(roleRepository.findAllByOrderByNameAsc()).thenReturn(roles);
        assertThat(roleAdminService.findAll()).containsExactlyElementsOf(roles);
        verify(roleRepository).findAllByOrderByNameAsc();
    }

    @Test void enabledPrivilegeCountCountsTrueFlags() {
        SstsRole zero = new SstsRole();
        assertThat(roleAdminService.enabledPrivilegeCount(zero)).isZero();

        SstsRole one = new SstsRole();
        one.setCanCreateUsers(true);
        assertThat(roleAdminService.enabledPrivilegeCount(one)).isEqualTo(1);

        SstsRole all = new SstsRole();
        setAllFlags(all, true);
        assertThat(roleAdminService.enabledPrivilegeCount(all)).isEqualTo(21);
    }

    private static SstsRole role(String name) {
        SstsRole r = new SstsRole();
        r.setName(name);
        r.setDescription("A role");
        r.setCanCreateUsers(true);
        r.setCanViewDashboard(true);
        r.setActive(true);
        return r;
    }

    private static void setAllFlags(SstsRole r, boolean v) {
        r.setCanCreateUsers(v);
        r.setCanEditUsers(v);
        r.setCanDeleteUsers(v);
        r.setCanViewUsers(v);
        r.setCanManageContent(v);
        r.setCanEditPages(v);
        r.setCanPublishContent(v);
        r.setCanManageLevels(v);
        r.setCanManageClasses(v);
        r.setCanManageDonors(v);
        r.setCanViewDonors(v);
        r.setCanManageEvents(v);
        r.setCanViewCalendar(v);
        r.setCanManageVolunteers(v);
        r.setCanViewFinancials(v);
        r.setCanViewReports(v);
        r.setCanManageSettings(v);
        r.setCanViewAuditLogs(v);
        r.setCanSendAnnouncements(v);
        r.setCanSendNewsletters(v);
        r.setCanViewDashboard(v);
    }
}
