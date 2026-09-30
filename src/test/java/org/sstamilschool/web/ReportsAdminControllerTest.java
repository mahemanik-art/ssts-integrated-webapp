package org.sstamilschool.web;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.ReportsAdminController;
import org.sstamilschool.dto.ReportsView;
import org.sstamilschool.service.ReportsService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReportsAdminController.class)
@Import(TestSecurityConfig.class)
class ReportsAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ReportsService reportsService;

    @BeforeEach
    void setup() {
        ReportsView view = new ReportsView(
                LocalDate.of(2026, 9, 26),
                "2026-2027",
                new ReportsView.Accounts(
                        100L, 85L, 15L, 10L,
                        java.util.Map.of("parent", 70L, "staff", 15L, "volunteer", 10L, "admin", 5L),
                        java.util.Map.of("parent", 65L, "staff", 13L, "volunteer", 8L, "admin", 5L)),
                new ReportsView.Roles(
                        4L, 4L,
                        List.of(
                                new ReportsView.RoleBreakdown("parent", 70L),
                                new ReportsView.RoleBreakdown("staff", 15L),
                                new ReportsView.RoleBreakdown("super_admin", 2L),
                                new ReportsView.RoleBreakdown("volunteer_coordinator", 10L))),
                new ReportsView.Content(
                        new ReportsView.CalendarContent(
                                40L, 38L, 30L, 10L,
                                List.of(
                                        new ReportsView.AcademicYearBreakdown("2025-2026", 35L, 35L),
                                        new ReportsView.AcademicYearBreakdown("2026-2027", 40L, 38L))),
                        new ReportsView.GalleryContent(25L, 5L),
                        new ReportsView.AnnouncementsContent(8L, 3L, 2L)));

        Mockito.when(reportsService.build()).thenReturn(view);
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/superadmin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonSuperAdminGetsForbidden() throws Exception {
        mvc.perform(get("/superadmin/reports").with(user("parent1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsRenderForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/reports").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/reports/index"))
                .andExpect(content().string(containsString("Reports")))
                .andExpect(content().string(containsString("Total users")))
                .andExpect(content().string(containsString("100")))
                .andExpect(content().string(containsString("Active")))
                .andExpect(content().string(containsString("85")))
                .andExpect(content().string(containsString("Deactivated")))
                .andExpect(content().string(containsString("15")))
                .andExpect(content().string(containsString("Unverified email")))
                .andExpect(content().string(containsString("10")))
                .andExpect(content().string(containsString("Accounts by User Type")))
                .andExpect(content().string(containsString("parent")))
                .andExpect(content().string(containsString("70")))
                .andExpect(content().string(containsString("staff")))
                .andExpect(content().string(containsString("15")))
                .andExpect(content().string(containsString("Roles")))
                .andExpect(content().string(containsString("Total roles")))
                .andExpect(content().string(containsString("4")))
                .andExpect(content().string(containsString("Role Breakdown")))
                .andExpect(content().string(containsString("super_admin")))
                .andExpect(content().string(containsString("2")))
                .andExpect(content().string(containsString("Calendar Events")))
                .andExpect(content().string(containsString("40")))
                .andExpect(content().string(containsString("38")))
                .andExpect(content().string(containsString("Working days")))
                .andExpect(content().string(containsString("30")))
                .andExpect(content().string(containsString("Holidays")))
                .andExpect(content().string(containsString("10")))
                .andExpect(content().string(containsString("Calendar Events by Academic Year")))
                .andExpect(content().string(containsString("2025-2026")))
                .andExpect(content().string(containsString("35")))
                .andExpect(content().string(containsString("Gallery Events")))
                .andExpect(content().string(containsString("25")))
                .andExpect(content().string(containsString("Inactive")))
                .andExpect(content().string(containsString("5")))
                .andExpect(content().string(containsString("Announcements")))
                .andExpect(content().string(containsString("8")))
                .andExpect(content().string(containsString("Never expiring")))
                .andExpect(content().string(containsString("3")))
                .andExpect(content().string(containsString("Expired so far")))
                .andExpect(content().string(containsString("2")));
    }
}
