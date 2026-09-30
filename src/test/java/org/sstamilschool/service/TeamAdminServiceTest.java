package org.sstamilschool.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache + CRUD test for TeamAdminService, wired with CacheConfig and mocked
 * repositories (no live DB) -- mirrors CalendarAdminServiceTest.
 *
 * <p>The point of the cache tests: a write through TeamAdminService must evict
 * TEAM_MEMBERS so the public /team page stops serving the stale list.
 */
@SpringJUnitConfig(classes = { CacheConfig.class, TeamAdminServiceTest.TestConfig.class })
class TeamAdminServiceTest {

    @Autowired TeamAdminService adminService;
    @Autowired TeamService teamService;
    @Autowired SstsUserRepository userRepository;
    @Autowired SstsRoleRepository roleRepository;

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
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        TeamService teamService(SstsUserRepository userRepository) {
            return new TeamService(userRepository);
        }

        @Bean
        TeamAdminService teamAdminService(SstsUserRepository userRepository,
                                          SstsRoleRepository roleRepository,
                                          PasswordEncoder passwordEncoder) {
            return new TeamAdminService(userRepository, roleRepository, passwordEncoder);
        }
    }

    @BeforeEach
    void clearCache() {
        teamService.evictTeamMembers();
        // One call per mock: clearInvocations takes a varargs Object[], and passing
        // several JpaRepository subtypes at once trips an unchecked generic array warning.
        clearInvocations(userRepository);
        clearInvocations(roleRepository);
        SstsUser seeded = new SstsUser();
        seeded.setFullName("Seeded Teacher");
        when(userRepository.findPublicTeamMembers()).thenReturn(List.of(seeded));
    }

    @Test void repeatedReadsHitDatabaseOnlyOnce() {
        teamService.getPublicTeamMembers();
        teamService.getPublicTeamMembers();
        verify(userRepository, times(1)).findPublicTeamMembers();
    }

    @Test void updateEvictsTeamCache() {
        SstsUser existing = teamMember(1L, "Old Name", "Teacher");
        SstsUser form = teamMember(1L, "New Name", "Coordinator");
        form.setBio("Updated bio"); form.setAvatarUrl("/images/team/new.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        teamService.getPublicTeamMembers();   // warm cache
        adminService.update(1L, form);       // write -> evicts

        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(existing.getDesignation()).isEqualTo("Coordinator");
        assertThat(existing.getBio()).isEqualTo("Updated bio");
        assertThat(existing.getAvatarUrl()).isEqualTo("/images/team/new.jpg");

        teamService.getPublicTeamMembers();
        verify(userRepository, times(2)).findPublicTeamMembers();
    }

    @Test void updateCoversEveryFieldTheTeamPageRenders() {
        SstsUser existing = teamMember(3L, "Before", null);
        SstsUser form = teamMember(3L, "After", "Lead");
        form.setDesignation("Lead");
        form.setUserType("volunteer");
        form.setActive(false);
        form.setBio("Bio text"); form.setAvatarUrl("/images/team/after.png");
        when(userRepository.findById(3L)).thenReturn(Optional.of(existing));

        adminService.update(3L, form);

        // fullName + designation render on the card; bio + avatarUrl come from the
        // profile join; userType/active decide whether the card is served at all.
        assertThat(existing.getFullName()).isEqualTo("After");
        assertThat(existing.getDesignation()).isEqualTo("Lead");
        assertThat(existing.getUserType()).isEqualTo("volunteer");
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getBio()).isEqualTo("Bio text");
        assertThat(existing.getAvatarUrl()).isEqualTo("/images/team/after.png");
    }

    @Test void updateLeavesLoginCredentialsUntouched() {
        SstsUser existing = teamMember(4L, "Name", "Teacher");
        existing.setUsername("teacher_user");
        existing.setEmail("teacher@sstschool.org");
        when(userRepository.findById(4L)).thenReturn(Optional.of(existing));

        SstsUser form = teamMember(4L, "Renamed", "Teacher");
        form.setUsername("hacker");
        form.setEmail("hacker@evil.test");
        adminService.update(4L, form);

        assertThat(existing.getUsername()).isEqualTo("teacher_user");
        assertThat(existing.getEmail()).isEqualTo("teacher@sstschool.org");
    }

    @Test void updateMissingMemberIsNoOp() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(adminService.update(99L, teamMember(99L, "Ghost", null))).isNull();
    }

    @Test void updateSetsProfileDetailThatWasPreviouslyNull() {
        SstsUser existing = teamMember(5L, "No Bio", "Teacher");
        existing.setBio(null);
        existing.setAvatarUrl(null);
        SstsUser form = teamMember(5L, "No Bio", "Teacher");
        form.setBio("Fresh bio"); form.setAvatarUrl(null);
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));

        adminService.update(5L, form);

        // No profile row to create any more -- the fields are columns on the user.
        assertThat(existing.getBio()).isEqualTo("Fresh bio");
    }

    @Test void createEvictsTeamCacheAndSavesProfileDetailInOneRow() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findByName("volunteer_coordinator")).thenReturn(Optional.of(role));
        when(userRepository.save(ArgumentMatchers.<SstsUser>any())).thenAnswer(inv -> {
            SstsUser u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        SstsUser form = teamMember(null, "New Teacher", "Class Teacher");
        form.setUsername("new_teacher");
        form.setEmail("new.teacher@sstschool.org");
        form.setUserType("staff");
        form.setActive(true);
        form.setBio("Brand new"); form.setAvatarUrl("/images/team/new.png");

        teamService.getPublicTeamMembers();   // warm cache
        SstsUser saved = adminService.create(form, "Str0ngPassw0rd!");

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getRole()).isSameAs(role);
        // BCrypt, never the raw password.
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Str0ngPassw0rd!");
        // bio/avatarUrl are columns on ssts_users, so one save carries them to /team.
        assertThat(saved.getBio()).isEqualTo("Brand new");
        assertThat(saved.getAvatarUrl()).isEqualTo("/images/team/new.png");

        teamService.getPublicTeamMembers();
        verify(userRepository, times(2)).findPublicTeamMembers();
    }

    @Test void createIgnoresAClientSuppliedRoleAndAlwaysUsesTheDefault() {
        // Regression: the form object is the bound SstsUser entity, so a client
        // can post role.id=<super_admin's role id> even though the form never
        // renders a role field. Honouring it let any super-admin mint a second
        // SUPER_ADMIN account. New members must always get DEFAULT_ROLE.
        SstsRole defaultRole = new SstsRole();
        defaultRole.setName(TeamAdminService.DEFAULT_ROLE);
        when(roleRepository.findByName(TeamAdminService.DEFAULT_ROLE))
                .thenReturn(Optional.of(defaultRole));

        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        superAdminRole.setId(2L);

        when(userRepository.save(ArgumentMatchers.<SstsUser>any())).thenAnswer(inv -> inv.getArgument(0));

        SstsUser form = teamMember(null, "Backdoor", "Teacher");
        form.setUsername("backdoor");
        form.setEmail("backdoor@example.test");
        form.setUserType("staff");
        form.setActive(true);
        form.setBio("bio"); form.setAvatarUrl(null);
        form.setRole(superAdminRole);   // what a crafted POST would bind

        SstsUser saved = adminService.create(form, "Str0ngPassw0rd!");

        assertThat(saved.getRole()).isSameAs(defaultRole);
        assertThat(saved.getRole().getName()).isEqualTo(TeamAdminService.DEFAULT_ROLE);
    }

    @Test void createRejectsDuplicateUsername() {        when(userRepository.existsByUsername("taken")).thenReturn(true);
        SstsUser form = teamMember(null, "Copy", "Teacher");
        form.setUsername("taken");
        form.setEmail("free@example.test");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.test")).thenReturn(true);
        SstsUser form = teamMember(null, "Copy", "Teacher");
        form.setUsername("free_username");
        form.setEmail("taken@example.test");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test void deleteEvictsTeamCacheAndRemovesTheUserInOneCall() {
        SstsUser existing = teamMember(7L, "Departing", "Teacher");
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));

        teamService.getPublicTeamMembers();
        assertThat(adminService.delete(7L, 1L)).isTrue();

        // There is no child row to clear first any more: bio/avatarUrl are
        // columns on ssts_users, so a single delete is the whole operation.
        verify(userRepository).deleteById(7L);

        teamService.getPublicTeamMembers();
        verify(userRepository, times(2)).findPublicTeamMembers();
    }

    @Test void deleteRefusesToDeleteActingUser() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(teamMember(7L, "Me", "Admin")));
        assertThat(adminService.delete(7L, 7L)).isFalse();
        verify(userRepository, never()).deleteById(any());
    }

    @Test void deleteMissingMemberIsNoOp() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThat(adminService.delete(99L, 1L)).isFalse();
        verify(userRepository, never()).deleteById(any());
    }

    @Test void newMemberHasSensibleDefaults() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findByName("volunteer_coordinator")).thenReturn(Optional.of(role));

        SstsUser member = adminService.newMember();
        assertThat(member.getUserType()).isEqualTo("staff");
        assertThat(member.isActive()).isTrue();
        assertThat(member.getRole()).isSameAs(role);
        // A profile exists up front so bio/avatarUrl are bindable on the new form.
    }

    @Test void findAllUsesAdminQueryNotThePublicOne() {
        SstsUser staff = teamMember(1L, "Staff", "Teacher");
        when(userRepository.findByUserTypeInOrderByFullNameAsc(List.of("staff", "volunteer"))).thenReturn(List.of(staff));
        assertThat(adminService.findAll()).containsExactly(staff);
        // The public query filters isActive = true; the admin list must not.
        verify(userRepository, never()).findPublicTeamMembers();
    }

    private static SstsUser teamMember(Long id, String fullName, String designation) {
        SstsUser user = new SstsUser();
        user.setId(id);
        user.setFullName(fullName);
        user.setDesignation(designation);
        user.setUserType("staff");
        user.setActive(true);
        return user;
    }

}
