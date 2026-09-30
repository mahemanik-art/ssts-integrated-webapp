package org.sstamilschool.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.CalendarAdminController;
import org.sstamilschool.model.SstsCalendarEvent;
import org.sstamilschool.service.CalendarAdminService;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CalendarAdminController.class)
@Import(TestSecurityConfig.class)
class CalendarAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CalendarAdminService calendarAdminService;
    // CacheAdminController shares /superadmin-free /api routes but is not loaded
    // here; no other service dependencies exist for this controller.

    @BeforeEach
    void setup() {
        SstsCalendarEvent event = new SstsCalendarEvent();
        event.setId(1L);
        event.setTitle("Pongal");
        event.setEventDate(LocalDate.of(2027, 1, 14));
        event.setAcademicYear("2026-2027");
        Mockito.when(calendarAdminService.findAll()).thenReturn(List.of(event));
        Mockito.when(calendarAdminService.findById(1L)).thenReturn(event);
        Mockito.when(calendarAdminService.newEvent()).thenReturn(new SstsCalendarEvent());
    }

    @Test void listRequiresAuthentication() throws Exception {
        // TestSecurityConfig permitAll's URLs; @PreAuthorize on the controller
        // still blocks anonymous access. Anonymous users are "denied" with 403
        // here rather than redirected (the saved-request redirect lives in the
        // real SecurityConfig filter chain, verified in the live end-to-end run).
        mvc.perform(get("/superadmin/calendar"))
                .andExpect(status().isForbidden());
    }

    @Test void listRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/calendar").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/calendar/list"))
                // @WebMvcTest renders the real template, so status()+view() alone
                // would not catch a broken expression. Assert the joined event and
                // the shared delete-confirm script actually reach the response.
                .andExpect(content().string(containsString("Pongal")))
                .andExpect(content().string(containsString("2026-2027")))
                .andExpect(content().string(containsString("/js/inline-delete.js")));
    }

    @Test void newFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/calendar/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/calendar/form"));
    }

    @Test void editFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/calendar/1/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/calendar/form"));
    }

    @Test void editFormRedirectsWhenEventMissing() throws Exception {
        Mockito.when(calendarAdminService.findById(99L)).thenReturn(null);
        mvc.perform(get("/superadmin/calendar/99/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/calendar"));
    }

    @Test void createWithValidDataRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/calendar")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Pongal")
                        .param("eventDate", "2027-01-14")
                        .param("eventType", "holiday")
                        .param("academicYear", "2026-2027"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/calendar"));
    }

    @Test void createWithMissingTitleRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/calendar")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("eventDate", "2027-01-14"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/calendar/new"));
    }

    @Test void updateRedirectsToList() throws Exception {
        Mockito.when(calendarAdminService.update(anyLong(), any())).thenReturn(new SstsCalendarEvent());
        mvc.perform(post("/superadmin/calendar/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Pongal Renamed")
                        .param("eventDate", "2027-01-15")
                        .param("academicYear", "2026-2027"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/calendar"));
    }

    @Test void updateWithMissingTitleRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/calendar/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("eventDate", "2027-01-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/calendar/1/edit"));
        Mockito.verify(calendarAdminService, Mockito.never()).update(anyLong(), any());
    }

    @Test void deleteRedirectsToList() throws Exception {
        Mockito.when(calendarAdminService.delete(1L)).thenReturn(true);
        mvc.perform(post("/superadmin/calendar/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/calendar"));
    }

    @Test void nonSuperAdminGetsForbidden() throws Exception {
        mvc.perform(get("/superadmin/calendar").with(user("parent1").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
