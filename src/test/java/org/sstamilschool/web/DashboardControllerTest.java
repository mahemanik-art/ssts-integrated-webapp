package org.sstamilschool.web;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.DashboardController;
import org.sstamilschool.controller.SuperAdminController;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.model.SstsRole;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Guards the discoverability of the /superadmin/** modules.
 *
 * <p>After login every admin lands on /dashboard (LoginService redirects by
 * userType), so this template is the ONLY place the super-admin modules can be
 * reached from. isSuperAdmin must be driven by the ROLE, never by userType --
 * a plain 'admin' would otherwise see links that 403.
 */
@WebMvcTest({DashboardController.class, SuperAdminController.class})
@Import(TestSecurityConfig.class)
class DashboardControllerTest {

    @Autowired MockMvc mvc;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String roleName) {
        SstsRole role = new SstsRole();
        role.setName(roleName);
        SstsUser user = new SstsUser();
        user.setId(1L);
        user.setFullName("Test User");
        user.setUserType("admin");
        user.setRole(role);
        authenticate(user);
    }

    private void authenticate(SstsUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        user, "n/a", user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Test void superAdminSeesTheSuperAdminModules() throws Exception {
        authenticate("super_admin");

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("isSuperAdmin", true))
                // The admin dashboard no longer contains the super-admin module cards.
                // Those cards moved to /superadmin. This test now asserts they are ABSENT
                // from the admin dashboard, while the shared nav still provides the links.
                .andExpect(content().string(not(containsString("Super-admin modules"))))
                .andExpect(content().string(not(containsString("Manage Announcements"))))
                .andExpect(content().string(not(containsString("Manage Calendar"))))
                .andExpect(content().string(not(containsString("Manage Team"))))
                .andExpect(content().string(not(containsString("Manage Gallery"))));
    }

    @Test void plainAdminDoesNotSeeSuperAdminLinks() throws Exception {
        // user_type is still 'admin' -- only the role differs. Authorization must
        // come from the role, so these links must stay hidden.
        authenticate("admin");

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("isSuperAdmin", false))
                .andExpect(content().string(not(containsString("href=\"/superadmin"))));
    }

    @Test void staffRoutesToAdminDashboardWithoutSuperAdminLinks() throws Exception {
        SstsRole role = new SstsRole();
        role.setName("teacher");
        SstsUser user = new SstsUser();
        user.setId(2L);
        user.setFullName("Staff User");
        user.setUserType("staff");
        user.setRole(role);
        authenticate(user);

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("isSuperAdmin", false))
                .andExpect(content().string(not(containsString("href=\"/superadmin"))));
    }

    @Test void parentUserSeesParentDashboardWithPageHeading() throws Exception {
        SstsRole role = new SstsRole();
        role.setName("volunteer_coordinator");
        SstsUser user = new SstsUser();
        user.setId(3L);
        user.setFullName("Parent User");
        user.setUserType("parent");
        user.setRole(role);
        authenticate(user);

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("parent/dashboard"))
                .andExpect(model().attribute("isSuperAdmin", false))
                .andExpect(content().string(containsString("class=\"page-heading\"")))
                .andExpect(content().string(containsString("PARENT PORTAL")))
                .andExpect(content().string(containsString("Welcome, Parent User")))
                .andExpect(content().string(not(containsString("template-hero"))));
    }

    @Test void superAdminLandingPageRendersModuleCards() throws Exception {
        authenticate("super_admin");

        mvc.perform(get("/superadmin"))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/dashboard"))
                .andExpect(model().attribute("isSuperAdmin", true))
                // The four module cards with their distinctive headings
                .andExpect(content().string(containsString("Announcements")))
                .andExpect(content().string(containsString("Calendar Events")))
                .andExpect(content().string(containsString("Team Members")))
                .andExpect(content().string(containsString("Gallery")))
                // The four module action links
                .andExpect(content().string(containsString("/superadmin/announcements")))
                .andExpect(content().string(containsString("/superadmin/calendar")))
                .andExpect(content().string(containsString("/superadmin/team")))
                .andExpect(content().string(containsString("/superadmin/gallery")))
                // Card button labels
                .andExpect(content().string(containsString("Manage Announcements")))
                .andExpect(content().string(containsString("Manage Calendar")))
                .andExpect(content().string(containsString("Manage Team")))
                .andExpect(content().string(containsString("Manage Gallery")));
    }
}
