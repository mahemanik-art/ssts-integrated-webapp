package org.sstamilschool.web;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.UserAdminController;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.service.UserAdminService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Mirrors TeamAdminControllerTest for the User Management module.
 * TestSecurityConfig permitAll's every URL, so the real /superadmin/** filter-chain
 * rule is not exercised here -- what is verified is that @PreAuthorize on the
 * controller still denies anonymous and non-super-admin users.
 */
@WebMvcTest(UserAdminController.class)
@Import(TestSecurityConfig.class)
class UserAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserAdminService userAdminService;

    @BeforeEach
    void setup() {
        SstsRole role = new SstsRole();
        role.setId(3L);
        role.setName("volunteer_coordinator");

        SstsUser user = new SstsUser();
        user.setId(1L);
        user.setUsername("test_user");
        user.setEmail("test@sstschool.org");
        user.setFullName("Test User");
        user.setUserType("parent");
        user.setActive(true);
        user.setEmailVerified(false);
        user.setRole(role);
        user.setReceiveNewsletter(true);
        user.setReceiveVolunteerUpdates(true);

        Mockito.when(userAdminService.findAll()).thenReturn(List.of(user));
        Mockito.when(userAdminService.findById(1L)).thenReturn(user);
        Mockito.when(userAdminService.newUser()).thenReturn(newUser());
        Mockito.when(userAdminService.findAllRoles()).thenReturn(List.of(role));
    }

    private static SstsUser newUser() {
        SstsUser u = new SstsUser();
        u.setUserType("parent");
        u.setActive(true);
        u.setEmailVerified(false);
        u.setReceiveNewsletter(true);
        u.setReceiveVolunteerUpdates(true);
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        u.setRole(role);
        return u;
    }

    @Test void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/superadmin/users"))
                .andExpect(status().isForbidden());
    }

    @Test void listRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/users").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/users/list"))
                .andExpect(content().string(containsString("Test User")))
                .andExpect(content().string(containsString("test_user")))
                .andExpect(content().string(containsString("test@sstschool.org")))
                .andExpect(content().string(containsString("parent")))
                .andExpect(content().string(containsString("volunteer_coordinator")))
                .andExpect(content().string(containsString("Active")))
                .andExpect(content().string(containsString("Email not verified")))
                .andExpect(content().string(containsString("/js/inline-delete.js")));
    }

    @Test void newFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/users/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/users/form"))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"roleId\"")));
    }

    @Test void editFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/users/1/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/users/form"))
                .andExpect(content().string(containsString("value=\"Test User\"")))
                .andExpect(content().string(containsString("name=\"newPassword\"")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test void editFormRendersEveryPerPersonField() throws Exception {
        // The users module is the only screen that edits the per-person columns
        // for anyone not on /team, so all of them must be bound by the form.
        // A @WebMvcTest renders the template, so this catches a field that was
        // added to the service but never wired into the markup.
        mvc.perform(get("/superadmin/users/1/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"dateOfBirth\"")))
                .andExpect(content().string(containsString("name=\"occupation\"")))
                .andExpect(content().string(containsString("name=\"yearsInCommunity\"")))
                .andExpect(content().string(containsString("name=\"employer\"")))
                .andExpect(content().string(containsString("name=\"department\"")))
                .andExpect(content().string(containsString("name=\"alternateEmail\"")))
                .andExpect(content().string(containsString("name=\"phone\"")))
                .andExpect(content().string(containsString("name=\"addressLine1\"")))
                .andExpect(content().string(containsString("name=\"addressLine2\"")))
                .andExpect(content().string(containsString("name=\"city\"")))
                .andExpect(content().string(containsString("name=\"state\"")))
                .andExpect(content().string(containsString("name=\"zipCode\"")))
                .andExpect(content().string(containsString("name=\"country\"")))
                .andExpect(content().string(containsString("name=\"avatarUrl\"")))
                .andExpect(content().string(containsString("name=\"bio\"")))
                .andExpect(content().string(containsString("name=\"certifications\"")))
                .andExpect(content().string(containsString("name=\"interests\"")))
                .andExpect(content().string(containsString("name=\"priorEducation\"")))
                .andExpect(content().string(containsString("name=\"priorTamilExperience\"")))
                .andExpect(content().string(containsString("name=\"priorTeachingExperience\"")))
                .andExpect(content().string(containsString("name=\"priorVolunteerExperience\"")))
                // The staff-only marker the script keys off must be present, or
                // the parent restriction cannot be surfaced in the UI.
                .andExpect(content().string(containsString("data-staff-contact")))
                .andExpect(content().string(containsString("/js/user-form.js")));
    }

    @Test void editFormRedirectsWhenUserMissing() throws Exception {
        Mockito.when(userAdminService.findById(99L)).thenReturn(null);
        mvc.perform(get("/superadmin/users/99/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users"));
    }

    @Test void createRejectsAClientSuppliedRole() throws Exception {
        // Defense in depth for the mass-assignment fix: the form object is the
        // bound SstsUser entity, so without @InitBinder a crafted POST could bind
        // role.id. UserAdminService also ignores the bound role, but the binder
        // means the field never reaches the service at all.
        mvc.perform(post("/superadmin/users")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "sneaky")
                        .param("email", "sneaky@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "Sneaky")
                        .param("userType", "staff")
                        .param("roleId", "3")
                        .param("role.id", "2")
                        .param("role.name", "super_admin")
                        .param("passwordHash", "$2a$10$notarealhashnotarealhashnotarealhashnotarealhashnotareal")
                        .param("lastLogin", "2026-01-01T00:00:00")
                        .param("createdAt", "2026-01-01T00:00:00")
                        .param("updatedAt", "2026-01-01T00:00:00"))
                .andExpect(status().is3xxRedirection());

        var captor = org.mockito.ArgumentCaptor.forClass(SstsUser.class);
        Mockito.verify(userAdminService).create(captor.capture(), eq("Str0ngPassw0rd!"), eq(3L));
        SstsUser bound = captor.getValue();
        assertThat(bound.getRole()).as("role must not be mass-assignable").isNull();
        assertThat(bound.getPasswordHash()).as("passwordHash must not be mass-assignable").isNull();
        assertThat(bound.getLastLogin()).as("lastLogin must not be mass-assignable").isNull();
        assertThat(bound.getCreatedAt()).as("createdAt must not be mass-assignable").isNull();
        assertThat(bound.getUpdatedAt()).as("updatedAt must not be mass-assignable").isNull();
    }

    @Test void createRejectsAShortPasswordServerSide() throws Exception {
        // The form's minlength is client-side only; the controller enforces the
        // same minimum so a direct POST cannot create a 1-character login.
        mvc.perform(post("/superadmin/users")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "weakpw")
                        .param("email", "weakpw@sstschool.org")
                        .param("password", "short")
                        .param("fullName", "Weak Password")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users/new"));

        Mockito.verify(userAdminService, Mockito.never()).create(any(), anyString(), anyLong());
    }

    @Test void createWithValidDataRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/users")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "new_user")
                        .param("email", "new.user@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "New User")
                        .param("userType", "staff")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users"));
    }

    @Test void createWithoutPasswordRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/users")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "new_user")
                        .param("email", "new.user@sstschool.org")
                        .param("fullName", "New User")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users/new"));
        Mockito.verify(userAdminService, Mockito.never()).create(any(), any(), anyLong());
    }

    @Test void createWithDuplicateShowsServiceError() throws Exception {
        Mockito.when(userAdminService.create(any(), anyString(), anyLong()))
                .thenThrow(new IllegalArgumentException("That username is already taken."));
        mvc.perform(post("/superadmin/users")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "taken")
                        .param("email", "taken@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "Copy")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users/new"));
    }

    @Test void updateRedirectsToList() throws Exception {
        Mockito.when(userAdminService.update(eq(1L), any(), anyLong(), anyString(), isNull())).thenReturn(new SstsUser());
        mvc.perform(post("/superadmin/users/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("fullName", "Renamed")
                        .param("userType", "staff")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users"));
    }

    @Test void updateWithoutFullNameRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/users/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("userType", "staff")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users/1/edit"));
        Mockito.verify(userAdminService, Mockito.never()).update(anyLong(), any(), anyLong(), anyString(), isNull());
    }

    @Test void updateMissingMemberRedirectsToList() throws Exception {
        Mockito.when(userAdminService.update(eq(99L), any(), anyLong(), anyString(), isNull())).thenReturn(null);
        mvc.perform(post("/superadmin/users/99")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("fullName", "Ghost")
                        .param("roleId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users"));
    }

    @Test void deleteRedirectsToList() throws Exception {
        Mockito.when(userAdminService.delete(eq(1L), isNull())).thenReturn(true);
        mvc.perform(post("/superadmin/users/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users"));
    }

    @Test void deleteRefusedStillRedirectsWithError() throws Exception {
        Mockito.when(userAdminService.delete(eq(1L), isNull())).thenReturn(false);
        mvc.perform(post("/superadmin/users/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/users"));
    }

    @Test void nonSuperAdminGetsForbidden() throws Exception {
        mvc.perform(get("/superadmin/users").with(user("parent1").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
