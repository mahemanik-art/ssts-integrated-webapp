package org.sstamilschool.web;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.RoleAdminController;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.service.RoleAdminService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Mirrors AnnouncementAdminControllerTest / TeamAdminControllerTest for the
 * /superadmin/roles module. TestSecurityConfig permitAll's every URL, so the
 * real SecurityConfig filter chain is not exercised here -- @PreAuthorize on
 * the controller still denies anonymous and non-super-admin users (the
 * saved-request redirect lives in the real filter chain, verified elsewhere).
 *
 * <p>The role list template calls {@code @roleAdminService.enabledPrivilegeCount(role)},
 * so the mock is registered under the bean name "roleAdminService" (matching the
 * production @Service bean name) rather than relying on the default field-name
 * fallback, which Spring's override-based @MockitoBean does not apply when
 * adding a brand-new bean in a slice context.
 */
@WebMvcTest(RoleAdminController.class)
@Import(TestSecurityConfig.class)
class RoleAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean(name = "roleAdminService") RoleAdminService roleAdminService;

    @BeforeEach
    void setup() {
        SstsRole role = new SstsRole();
        role.setId(1L);
        role.setName("content_editor");
        role.setDescription("Custom editor role");
        role.setCanCreateUsers(true);
        role.setCanViewDashboard(true);
        role.setActive(true);

        Mockito.when(roleAdminService.findAll()).thenReturn(List.of(role));
        Mockito.when(roleAdminService.findById(1L)).thenReturn(role);
        Mockito.when(roleAdminService.newRole()).thenReturn(new SstsRole());
        Mockito.when(roleAdminService.update(eq(1L), any())).thenReturn(new SstsRole());
    }

    @Test void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/superadmin/roles"))
                .andExpect(status().isForbidden());
    }

    @Test void nonSuperAdminGetsForbidden() throws Exception {
        mvc.perform(get("/superadmin/roles").with(user("parent1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test void listRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/roles").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/roles/list"))
                // @WebMvcTest renders the real Thymeleaf template, so content
                // assertions prove the joined role (and the shared delete-confirm
                // script) actually reached the response, not just that the view
                // name resolved.
                .andExpect(content().string(containsString("content_editor")))
                .andExpect(content().string(containsString("Custom editor role")))
                .andExpect(content().string(containsString("of 21")))
                .andExpect(content().string(containsString("/js/inline-delete.js")));
    }

    @Test void newFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/roles/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/roles/form"))
                .andExpect(content().string(containsString("New role")))
                .andExpect(content().string(containsString("Role name")));
    }

    @Test void createWithValidDataRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/roles")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("name", "content_editor")
                        .param("canViewDashboard", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/roles"));
        Mockito.verify(roleAdminService).create(any(), eq("content_editor"));
    }

    @Test void createWithInvalidNameRedirectsWithError() throws Exception {
        // The controller's isBlank(name) guard short-circuits before the service
        // is reached, so the service is never called for a missing/blank name.
        mvc.perform(post("/superadmin/roles")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/roles/new"));
        Mockito.verify(roleAdminService, Mockito.never()).create(any(), any());
    }

    @Test void createRejectsAClientSuppliedNameOnUpdate() throws Exception {
        // The @InitBinder("role") deny-list includes "name", so a crafted name
        // param on the UPDATE path cannot rename a role: the SstsRole form handed
        // to the service still has name == null.
        mvc.perform(post("/superadmin/roles/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("name", "super_admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/roles"));

        var captor = ArgumentCaptor.forClass(SstsRole.class);
        Mockito.verify(roleAdminService).update(eq(1L), captor.capture());
        assertThat(captor.getValue().getName())
                .as("name must not be mass-assignable on update")
                .isNull();
    }

    @Test void updateRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/roles/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("description", "Updated description")
                        .param("canViewDashboard", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/roles"));
    }

    @Test void deleteRedirectsToList() throws Exception {
        Mockito.when(roleAdminService.delete(1L)).thenReturn(true);
        mvc.perform(post("/superadmin/roles/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/roles"));
    }

    @Test void deleteOfProtectedRoleIsRefused() throws Exception {
        Mockito.when(roleAdminService.delete(1L))
                .thenThrow(new IllegalArgumentException("The super_admin role cannot be deleted."));
        mvc.perform(post("/superadmin/roles/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/roles"));
    }
}
