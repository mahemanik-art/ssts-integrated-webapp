package org.sstamilschool.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.TeamAdminController;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.service.TeamAdminService;

import java.util.List;

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
 * Mirrors CalendarAdminControllerTest. TestSecurityConfig permitAll's every URL,
 * so the real /superadmin/** filter-chain rule is not exercised here -- what is
 * verified is that @PreAuthorize on the controller still denies anonymous and
 * non-super-admin users.
 */
@WebMvcTest(TeamAdminController.class)
@Import(TestSecurityConfig.class)
class TeamAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean TeamAdminService teamAdminService;

    @BeforeEach
    void setup() {
        SstsUser member = new SstsUser();
        member.setId(1L);
        member.setUsername("teacher_user");
        member.setEmail("teacher@sstschool.org");
        member.setFullName("Suresh Menon");
        member.setDesignation("Class Teacher");
        member.setUserType("staff");
        member.setActive(true);
        // bio/avatarUrl are columns on ssts_users now, so the fixture sets them
        // directly instead of through a separate profile row.
        member.setBio("Teaches Level 1.");
        member.setAvatarUrl("/images/team/suresh.jpg");

        Mockito.when(teamAdminService.findAll()).thenReturn(List.of(member));
        Mockito.when(teamAdminService.findById(1L)).thenReturn(member);
        Mockito.when(teamAdminService.newMember()).thenReturn(newMember());
    }

    private static SstsUser newMember() {
        SstsUser user = new SstsUser();
        user.setUserType("staff");
        user.setActive(true);
        return user;
    }

    @Test void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/superadmin/team"))
                .andExpect(status().isForbidden());
    }

    @Test void listRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/team").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/team/list"))
                // Content assertions prove the Thymeleaf template actually rendered
                // (and that the profile join surfaced), not just that the view resolved.
                .andExpect(content().string(containsString("Suresh Menon")))
                .andExpect(content().string(containsString("Class Teacher")))
                .andExpect(content().string(containsString("Teaches Level 1.")))
                .andExpect(content().string(containsString("/images/team/suresh.jpg")));
    }

    @Test void newFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/team/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/team/form"));
    }

    @Test void editFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/team/1/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/team/form"))
                // member.profile?.avatarUrl / ?.bio must resolve on the edit form.
                .andExpect(content().string(containsString("value=\"Suresh Menon\"")))
                .andExpect(content().string(containsString("/images/team/suresh.jpg")))
                .andExpect(content().string(containsString("Teaches Level 1.")))
                // Credentials are read-only on edit, so username/password inputs are absent.
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test void newFormRendersWithoutProfileValues() throws Exception {
        // Guards the null-safe path: a brand new member has a profile but no
        // avatarUrl/bio yet, so ?. must not blow up on the new form.
        mvc.perform(get("/superadmin/team/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/team/form"))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test void createRejectsAClientSuppliedRole() throws Exception {
        // Defense in depth for the mass-assignment fix: the form object is the
        // bound SstsUser entity, so without @InitBinder a crafted POST could bind
        // role.id. TeamAdminService also ignores the bound role, but the binder
        // means the field never reaches the service at all.
        mvc.perform(post("/superadmin/team")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "sneaky")
                        .param("email", "sneaky@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "Sneaky")
                        .param("userType", "staff")
                        .param("role.id", "1")
                        .param("role.name", "super_admin")
                        .param("passwordHash", "$2a$10$notarealhashnotarealhashnotarealhashnotarealhashnotareal")
                        .param("emailVerified", "true"))
                .andExpect(status().is3xxRedirection());

        var captor = org.mockito.ArgumentCaptor.forClass(SstsUser.class);
        Mockito.verify(teamAdminService).create(captor.capture(), eq("Str0ngPassw0rd!"));
        SstsUser bound = captor.getValue();
        assertThat(bound.getRole()).as("role must not be mass-assignable").isNull();
        assertThat(bound.getPasswordHash()).as("passwordHash must not be mass-assignable").isNull();
        assertThat(bound.isEmailVerified()).as("emailVerified must not be mass-assignable").isFalse();
    }

    @Test void createRejectsAShortPasswordServerSide() throws Exception {
        // The form's minlength is client-side only; the controller enforces the
        // same minimum so a direct POST cannot create a 1-character login.
        mvc.perform(post("/superadmin/team")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "weakpw")
                        .param("email", "weakpw@sstschool.org")
                        .param("password", "short")
                        .param("fullName", "Weak Password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team/new"));

        Mockito.verify(teamAdminService, Mockito.never()).create(any(), anyString());
    }

    @Test void editFormRedirectsWhenMemberMissing() throws Exception {
        Mockito.when(teamAdminService.findById(99L)).thenReturn(null);
        mvc.perform(get("/superadmin/team/99/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team"));
    }

    @Test void createWithValidDataRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/team")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "new_teacher")
                        .param("email", "new.teacher@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "New Teacher")
                        .param("userType", "staff")
                        .param("designation", "Class Teacher")
                        .param("bio", "New bio")
                        .param("avatarUrl", "/images/team/new.png")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team"));
    }

    @Test void createPassesAllTeamPageFieldsToTheService() throws Exception {
        mvc.perform(post("/superadmin/team")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "new_teacher")
                        .param("email", "new.teacher@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "New Teacher")
                        .param("userType", "volunteer")
                        .param("designation", "Class Teacher")
                        .param("bio", "New bio")
                        .param("avatarUrl", "/images/team/new.png")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection());

        var captor = org.mockito.ArgumentCaptor.forClass(SstsUser.class);
        Mockito.verify(teamAdminService).create(captor.capture(), eq("Str0ngPassw0rd!"));
        SstsUser sent = captor.getValue();
        assert sent.getFullName().equals("New Teacher");
        assert sent.getDesignation().equals("Class Teacher");
        assert sent.getUserType().equals("volunteer");
        assert sent.getBio().equals("New bio");
        assert sent.getAvatarUrl().equals("/images/team/new.png");
    }

    @Test void createWithoutPasswordRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/team")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "new_teacher")
                        .param("email", "new.teacher@sstschool.org")
                        .param("fullName", "New Teacher"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team/new"));
        Mockito.verify(teamAdminService, Mockito.never()).create(any(), any());
    }

    @Test void createWithDuplicateShowsServiceError() throws Exception {
        Mockito.when(teamAdminService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("That username is already taken."));
        mvc.perform(post("/superadmin/team")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("username", "taken")
                        .param("email", "taken@sstschool.org")
                        .param("password", "Str0ngPassw0rd!")
                        .param("fullName", "Copy"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team/new"));
    }

    @Test void updateRedirectsToList() throws Exception {
        Mockito.when(teamAdminService.update(eq(1L), any())).thenReturn(new SstsUser());
        mvc.perform(post("/superadmin/team/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("fullName", "Renamed")
                        .param("userType", "staff")
                        .param("designation", "Lead")
                        .param("bio", "Updated bio")
                        .param("avatarUrl", "/images/team/updated.png"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team"));
    }

    @Test void updateWithoutFullNameRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/team/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("userType", "staff"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team/1/edit"));
        Mockito.verify(teamAdminService, Mockito.never()).update(anyLong(), any());
    }

    @Test void updateMissingMemberRedirectsToList() throws Exception {
        Mockito.when(teamAdminService.update(eq(99L), any())).thenReturn(null);
        mvc.perform(post("/superadmin/team/99")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("fullName", "Ghost"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team"));
    }

    @Test void deleteRedirectsToList() throws Exception {
        Mockito.when(teamAdminService.delete(eq(1L), isNull())).thenReturn(true);
        mvc.perform(post("/superadmin/team/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team"));
    }

    @Test void deleteRefusedStillRedirectsWithError() throws Exception {
        Mockito.when(teamAdminService.delete(eq(1L), isNull())).thenReturn(false);
        mvc.perform(post("/superadmin/team/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/team"));
    }

    @Test void nonSuperAdminGetsForbidden() throws Exception {
        mvc.perform(get("/superadmin/team").with(user("parent1").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
