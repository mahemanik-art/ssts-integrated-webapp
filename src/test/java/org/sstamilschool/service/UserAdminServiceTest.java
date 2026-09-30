package org.sstamilschool.service;

import java.time.LocalDate;
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

import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.util.SchoolTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for UserAdminService, wired with mocked repositories (no live DB).
 * No CacheConfig -- this module is not cached.
 */
@SpringJUnitConfig(classes = { UserAdminServiceTest.TestConfig.class })
class UserAdminServiceTest {

    @Autowired UserAdminService adminService;
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
        UserAdminService userAdminService(SstsUserRepository userRepository,
                                          SstsRoleRepository roleRepository,
                                          PasswordEncoder passwordEncoder) {
            return new UserAdminService(userRepository, roleRepository, passwordEncoder);
        }
    }

    @BeforeEach
    void setup() {
        // reset() clears both invocations AND stubbings, preventing cross-test pollution
        reset(userRepository, roleRepository);
    }

    @Test void newUserHasSensibleDefaults() {
        SstsRole role = new SstsRole();
        role.setName(UserAdminService.DEFAULT_ROLE);
        when(roleRepository.findByName(UserAdminService.DEFAULT_ROLE)).thenReturn(Optional.of(role));

        SstsUser user = adminService.newUser();

        assertThat(user.getUserType()).isEqualTo("parent");
        assertThat(user.isActive()).isTrue();
        assertThat(user.isEmailVerified()).isFalse();
        // School date, not the server's: UserAdminService.newUser() stamps
        // SchoolTime.today(), and the server runs 4-5h ahead of the school.
        assertThat(user.getJoiningDate()).isEqualTo(SchoolTime.today());
        assertThat(user.getRole()).isSameAs(role);
    }

    @Test void createAssignsRoleFromRoleIdNotBoundRole() {
        SstsRole defaultRole = new SstsRole();
        defaultRole.setName(UserAdminService.DEFAULT_ROLE);
        when(roleRepository.findByName(UserAdminService.DEFAULT_ROLE)).thenReturn(Optional.of(defaultRole));

        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        superAdminRole.setId(2L);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(superAdminRole));

        when(userRepository.save(ArgumentMatchers.<SstsUser>any())).thenAnswer(inv -> {
            SstsUser u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        SstsUser form = user("newuser", "new@example.test", "New User");
        form.setUserType("staff");
        form.setActive(true);
        form.setRole(superAdminRole); // what a crafted POST would bind

        SstsUser saved = adminService.create(form, "Str0ngPassw0rd!", 2L);

        assertThat(saved.getRole()).isSameAs(superAdminRole);
        assertThat(saved.getRole().getName()).isEqualTo("super_admin");
    }

    @Test void createRejectsAClientSuppliedRole() {
        // Regression: the form object is the bound SstsUser entity, so a client
        // can post role.id=<super_admin's role id> even though the form renders
        // a separate roleId select. Honouring the bound role would let any super-admin
        // mint a second SUPER_ADMIN account. The service must always use the
        // validated roleId parameter.
        SstsRole volunteerRole = new SstsRole();
        volunteerRole.setName("volunteer_coordinator");
        volunteerRole.setId(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(volunteerRole));

        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        superAdminRole.setId(2L);

        when(userRepository.save(ArgumentMatchers.<SstsUser>any())).thenAnswer(inv -> inv.getArgument(0));

        SstsUser form = user("backdoor", "backdoor@example.test", "Backdoor");
        form.setUserType("staff");
        form.setActive(true);
        form.setRole(superAdminRole); // bound via mass assignment

        SstsUser saved = adminService.create(form, "Str0ngPassw0rd!", 3L);

        assertThat(saved.getRole()).isSameAs(volunteerRole);
        assertThat(saved.getRole().getName()).isEqualTo("volunteer_coordinator");
    }

    @Test void createRejectsUnknownRoleId() {
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        SstsUser form = user("test", "test@example.test", "Test User");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selected role does not exist");
    }

    @Test void createRejectsDuplicateUsername() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        SstsUser form = user("taken", "free@example.test", "Copy User");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @Test void createRejectsDuplicateEmail() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(userRepository.existsByEmail("taken@example.test")).thenReturn(true);

        SstsUser form = user("free_username", "taken@example.test", "Copy User");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test void createRejectsInvalidUserType() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("test", "test@example.test", "Test User");
        form.setUserType("invalid_type");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid user type");
    }

    @Test void createRejectsUsernameTooLong() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("a".repeat(51), "test@example.test", "Test User");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username must not exceed 50 characters");
    }

    @Test void createRejectsEmailTooLong() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("test", "a".repeat(90) + "@example.test", "Test User");

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email must not exceed 100 characters");
    }

    @Test void createRejectsFullNameTooLong() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("test", "test@example.test", "a".repeat(101));

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name must not exceed 100 characters");
    }

    @Test void createRejectsDesignationTooLong() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("test", "test@example.test", "Test User");
        form.setDesignation("a".repeat(101));

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Designation must not exceed 100 characters");
    }

    @Test void updateLeavesPasswordHashAloneWhenNewPasswordBlank() {
        SstsUser existing = user("existing", "existing@example.test", "Existing User");
        existing.setId(1L);
        existing.setPasswordHash("$2a$10$oldhash");
        SstsRole existingRole = new SstsRole();
        existingRole.setName("volunteer_coordinator");
        existing.setRole(existingRole);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(existing));

        SstsRole sameRole = new SstsRole();
        sameRole.setName("volunteer_coordinator");
        sameRole.setId(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(sameRole));

        SstsUser form = user("existing", "existing@example.test", "Renamed User");
        form.setUserType("staff");

        adminService.update(1L, form, 3L, "", 2L); // blank newPassword

        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$oldhash");
        assertThat(existing.getFullName()).isEqualTo("Renamed User");
    }

    @Test void updateChangesPasswordHashWhenNewPasswordProvided() {
        SstsUser existing = user("existing", "existing@example.test", "Existing User");
        existing.setId(1L);
        existing.setPasswordHash("$2a$10$oldhash");
        SstsRole existingRole = new SstsRole();
        existingRole.setName("volunteer_coordinator");
        existing.setRole(existingRole);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(existing));

        SstsRole sameRole = new SstsRole();
        sameRole.setName("volunteer_coordinator");
        sameRole.setId(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(sameRole));

        SstsUser form = user("existing", "existing@example.test", "Renamed User");

        adminService.update(1L, form, 3L, "NewStr0ngPassw0rd!", 2L);

        assertThat(existing.getPasswordHash()).isNotEqualTo("$2a$10$oldhash");
        assertThat(existing.getPasswordHash()).startsWith("$2");
    }

    @Test void deleteRefusesActingUser() {
        when(userRepository.findByIdWithRole(7L)).thenReturn(Optional.of(user("me", "me@example.test", "Me")));
        assertThat(adminService.delete(7L, 7L)).isFalse();
        verify(userRepository, never()).deleteById(any());
    }

    @Test void deleteRefusesLastSuperAdmin() {
        SstsUser superAdmin = user("sole_admin", "admin@example.test", "Sole Admin");
        superAdmin.setId(1L);
        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        superAdmin.setRole(superAdminRole);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(superAdmin));
        when(userRepository.countByRoleName("super_admin")).thenReturn(1L);

        assertThatThrownBy(() -> adminService.delete(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only super admin");
    }

    @Test void deleteRemovesTheUserInOneCall() {
        SstsUser existing = user("departing", "departing@example.test", "Departing User");
        existing.setId(7L);
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(7L)).thenReturn(Optional.of(existing));
        when(userRepository.countByRoleName("super_admin")).thenReturn(0L);

        assertThat(adminService.delete(7L, 1L)).isTrue();

        // No child row to clear first: bio/avatarUrl are columns on ssts_users.
        verify(userRepository).deleteById(7L);
    }

    @Test void updateRefusesSelfDeactivate() {
        SstsUser existing = user("me", "me@example.test", "Me");
        existing.setId(7L);
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(7L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("me", "me@example.test", "Me");
        form.setActive(false);

        assertThatThrownBy(() -> adminService.update(7L, form, 3L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot deactivate your own account");
    }

    @Test void updateRefusesSelfRoleChangeWhenSuperAdmin() {
        SstsUser existing = user("me", "me@example.test", "Me");
        existing.setId(7L);
        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        existing.setRole(superAdminRole);
        when(userRepository.findByIdWithRole(7L)).thenReturn(Optional.of(existing));

        SstsRole volunteerRole = new SstsRole();
        volunteerRole.setName("volunteer_coordinator");
        volunteerRole.setId(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(volunteerRole));
        when(userRepository.countByRoleName("super_admin")).thenReturn(1L);

        SstsUser form = user("me", "me@example.test", "Me");

        assertThatThrownBy(() -> adminService.update(7L, form, 3L, null, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot change your own role");
    }

    @Test void updateRefusesDemoteLastSuperAdmin() {
        SstsUser existing = user("target", "target@example.test", "Target");
        existing.setId(5L);
        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        existing.setRole(superAdminRole);
        when(userRepository.findByIdWithRole(5L)).thenReturn(Optional.of(existing));

        SstsRole volunteerRole = new SstsRole();
        volunteerRole.setName("volunteer_coordinator");
        volunteerRole.setId(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(volunteerRole));
        when(userRepository.countByRoleName("super_admin")).thenReturn(1L);

        SstsUser form = user("target", "target@example.test", "Target");

        assertThatThrownBy(() -> adminService.update(5L, form, 3L, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only super admin");
    }

    @Test void updateRefusesDeactivateLastSuperAdmin() {
        SstsUser existing = user("target", "target@example.test", "Target");
        existing.setId(5L);
        SstsRole superAdminRole = new SstsRole();
        superAdminRole.setName("super_admin");
        existing.setRole(superAdminRole);
        when(userRepository.findByIdWithRole(5L)).thenReturn(Optional.of(existing));

        SstsRole volunteerRole = new SstsRole();
        volunteerRole.setName("volunteer_coordinator");
        volunteerRole.setId(3L);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(volunteerRole));
        when(userRepository.countByRoleName("super_admin")).thenReturn(1L);

        SstsUser form = user("target", "target@example.test", "Target");
        form.setActive(false);

        assertThatThrownBy(() -> adminService.update(5L, form, 3L, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only super admin");
    }

    @Test void findAllUsesAdminQuery() {
        SstsUser u1 = user("a", "a@test", "User A");
        u1.setUserType("admin");
        SstsUser u2 = user("b", "b@test", "User B");
        u2.setUserType("parent");
        when(userRepository.findAllForAdmin()).thenReturn(List.of(u2, u1)); // ordered by userType ASC, fullName ASC

        assertThat(adminService.findAll()).containsExactly(u2, u1);
    }

    @Test void findByIdReturnsNullWhenMissing() {
        when(userRepository.findByIdWithRole(99L)).thenReturn(Optional.empty());
        assertThat(adminService.findById(99L)).isNull();
    }

    @Test void updateMissingMemberReturnsNull() {
        when(userRepository.findByIdWithRole(99L)).thenReturn(Optional.empty());
        assertThat(adminService.update(99L, user("ghost", "g@test", "Ghost"), 3L, null, 1L)).isNull();
    }

    @Test void deleteMissingMemberReturnsFalse() {
        when(userRepository.findByIdWithRole(99L)).thenReturn(Optional.empty());
        assertThat(adminService.delete(99L, 1L)).isFalse();
        verify(userRepository, never()).deleteById(any());
    }

    @Test void updateSetsProfileDetailThatWasPreviouslyNull() {
        SstsUser existing = user("noprofile", "np@test", "No Profile");
        existing.setId(5L);
        existing.setBio(null);
        existing.setAvatarUrl(null);
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(5L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("noprofile", "np@test", "No Profile");
        form.setBio("Fresh bio"); form.setAvatarUrl("/images/team/new.png");

        adminService.update(5L, form, 3L, null, 1L);

        // No profile row to create any more -- the fields are columns on the user.
        assertThat(existing.getBio()).isEqualTo("Fresh bio");
        assertThat(existing.getAvatarUrl()).isEqualTo("/images/team/new.png");
    }

    @Test void updateCopiesEveryPerPersonFieldForStaff() {
        SstsUser existing = user("s1", "s1@test", "Staff One");
        existing.setId(11L);
        existing.setUserType("staff");
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(11L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("s1", "s1@test", "Staff One");
        form.setUserType("staff");
        form.setDateOfBirth(LocalDate.of(1985, 4, 2));
        form.setBio("Teaches Level 1.");
        form.setOccupation("Engineer");
        form.setEmployer("Acme");
        form.setDepartment("Academics");
        form.setYearsInCommunity(6);
        form.setAlternateEmail("s1.alt@test");
        form.setAvatarUrl("/images/teachers/t01.jpg");
        form.setPriorEducation("BSc");
        form.setPriorTamilExperience("Volunteer, 2019");
        form.setPriorTeachingExperience("Tutor, 2020");
        form.setPriorVolunteerExperience("Chore, 2021");
        form.setCertifications("Tamil Academy L1");
        form.setInterests("Cricket");
        form.setPhone("404-555-0101");
        form.setAddressLine1("1 Main St");
        form.setAddressLine2("Apt 2");
        form.setCity("Atlanta");
        form.setState("GA");
        form.setZipCode("30328");
        form.setCountry("USA");

        adminService.update(11L, form, 3L, null, 1L);

        // These are columns on ssts_users, so ONE save has to carry all of them.
        assertThat(existing.getDateOfBirth()).isEqualTo(LocalDate.of(1985, 4, 2));
        assertThat(existing.getBio()).isEqualTo("Teaches Level 1.");
        assertThat(existing.getOccupation()).isEqualTo("Engineer");
        assertThat(existing.getEmployer()).isEqualTo("Acme");
        assertThat(existing.getDepartment()).isEqualTo("Academics");
        assertThat(existing.getYearsInCommunity()).isEqualTo(6);
        assertThat(existing.getAlternateEmail()).isEqualTo("s1.alt@test");
        assertThat(existing.getAvatarUrl()).isEqualTo("/images/teachers/t01.jpg");
        assertThat(existing.getPriorEducation()).isEqualTo("BSc");
        assertThat(existing.getPriorTamilExperience()).isEqualTo("Volunteer, 2019");
        assertThat(existing.getPriorTeachingExperience()).isEqualTo("Tutor, 2020");
        assertThat(existing.getPriorVolunteerExperience()).isEqualTo("Chore, 2021");
        assertThat(existing.getCertifications()).isEqualTo("Tamil Academy L1");
        assertThat(existing.getInterests()).isEqualTo("Cricket");
        assertThat(existing.getPhone()).isEqualTo("404-555-0101");
        assertThat(existing.getAddressLine1()).isEqualTo("1 Main St");
        assertThat(existing.getAddressLine2()).isEqualTo("Apt 2");
        assertThat(existing.getCity()).isEqualTo("Atlanta");
        assertThat(existing.getState()).isEqualTo("GA");
        assertThat(existing.getZipCode()).isEqualTo("30328");
        assertThat(existing.getCountry()).isEqualTo("USA");
    }

    @Test void createCopiesPerPersonFieldsForStaff() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(userRepository.save(ArgumentMatchers.<SstsUser>any())).thenAnswer(inv -> inv.getArgument(0));

        SstsUser form = user("newstaff", "ns@test", "New Staff");
        form.setUserType("staff");
        form.setDepartment("Outreach");
        form.setPhone("770-555-0102");
        form.setCity("Marietta");
        form.setInterests("Tamil music");

        SstsUser saved = adminService.create(form, "Str0ngPassw0rd!", 3L);

        assertThat(saved.getDepartment()).isEqualTo("Outreach");
        assertThat(saved.getPhone()).isEqualTo("770-555-0102");
        assertThat(saved.getCity()).isEqualTo("Marietta");
        assertThat(saved.getInterests()).isEqualTo("Tamil music");
    }

    @Test void createDoesNotWritePhoneOrAddressForAParent() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(userRepository.save(ArgumentMatchers.<SstsUser>any())).thenAnswer(inv -> inv.getArgument(0));

        SstsUser form = user("newparent", "np2@test", "New Parent");
        form.setUserType("parent");
        form.setPhone("404-555-9999");
        form.setAddressLine1("9 Wrong St");
        form.setCity("Wrongville");
        form.setBio("A parent, not a staff member.");

        SstsUser saved = adminService.create(form, "Str0ngPassw0rd!", 3L);

        // ssts_families is the authoritative source for a parent's contact
        // details, so these must not be written to ssts_users at all.
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getAddressLine1()).isNull();
        assertThat(saved.getCity()).isNull();
        // Everything else about the person IS still written.
        assertThat(saved.getBio()).isEqualTo("A parent, not a staff member.");
    }

    @Test void updateDoesNotClearAnExistingPhoneWhenDemotedToParent() {
        SstsUser existing = user("s2", "s2@test", "Was Staff");
        existing.setId(12L);
        existing.setUserType("staff");
        existing.setPhone("404-555-0103");
        existing.setCity("Atlanta");
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(12L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("s2", "s2@test", "Was Staff");
        form.setUserType("parent");
        form.setPhone(null);
        form.setCity(null);

        adminService.update(12L, form, 3L, null, 1L);

        // Skipping the copy must not mean CLEARING: a demotion is not a
        // destruction of data that may still be needed.
        assertThat(existing.getPhone()).isEqualTo("404-555-0103");
        assertThat(existing.getCity()).isEqualTo("Atlanta");
    }

    @Test void createRejectsAnOverlongDepartment() {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("toolong", "tl@test", "Too Long");
        form.setUserType("staff");
        form.setDepartment("D".repeat(101));  // column is VARCHAR(100)

        assertThatThrownBy(() -> adminService.create(form, "Str0ngPassw0rd!", 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Department");
    }

    @Test void updateRejectsAnOverlongOccupation() {
        SstsUser existing = user("s3", "s3@test", "Staff Three");
        existing.setId(13L);
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(13L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("s3", "s3@test", "Staff Three");
        form.setOccupation("O".repeat(101));  // column is VARCHAR(100)

        // update does not run validateCreate, so the per-person limits need
        // enforcing on this path too or a crafted POST reaches the database.
        assertThatThrownBy(() -> adminService.update(13L, form, 3L, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Occupation");
    }

    @Test void updateRejectsNegativeYearsInCommunity() {
        SstsUser existing = user("s4", "s4@test", "Staff Four");
        existing.setId(14L);
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        existing.setRole(role);
        when(userRepository.findByIdWithRole(14L)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        SstsUser form = user("s4", "s4@test", "Staff Four");
        form.setYearsInCommunity(-1);  // chk_ssts_users_years_in_community

        // The form has min="0", which is client-side only -- a crafted POST
        // would otherwise reach the CHECK and fail as a raw constraint error.
        assertThatThrownBy(() -> adminService.update(14L, form, 3L, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Years in community");
    }

    private static SstsUser user(String username, String email, String fullName) {
        SstsUser user = new SstsUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setUserType("parent");
        user.setActive(true);
        return user;
    }

}
