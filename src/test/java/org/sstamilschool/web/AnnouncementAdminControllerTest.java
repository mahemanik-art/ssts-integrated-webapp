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

import org.sstamilschool.controller.AnnouncementAdminController;
import org.sstamilschool.model.SstsAnnouncement;
import org.sstamilschool.service.AnnouncementAdminService;

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

/** Mirrors CalendarAdminControllerTest for the announcements module. */
@WebMvcTest(AnnouncementAdminController.class)
@Import(TestSecurityConfig.class)
class AnnouncementAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean AnnouncementAdminService announcementAdminService;

    @BeforeEach
    void setup() {
        SstsAnnouncement announcement = new SstsAnnouncement();
        announcement.setId(1L);
        announcement.setTitle("Registration Open");
        announcement.setMessage("Enrolment is open for 2026-27.");
        announcement.setAnnounceDate(LocalDate.of(2026, 8, 1));
        announcement.setActive(true);

        Mockito.when(announcementAdminService.findAll()).thenReturn(List.of(announcement));
        Mockito.when(announcementAdminService.findById(1L)).thenReturn(announcement);
        Mockito.when(announcementAdminService.newAnnouncement()).thenReturn(new SstsAnnouncement());
    }

    @Test void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/superadmin/announcements"))
                .andExpect(status().isForbidden());
    }

    @Test void listRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/announcements").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/announcements/list"))
                .andExpect(content().string(containsString("Registration Open")))
                .andExpect(content().string(containsString("Enrolment is open for 2026-27.")))
                // A null expiry renders as "never" rather than a blank cell.
                .andExpect(content().string(containsString("never")));
    }

    @Test void newFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/announcements/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/announcements/form"))
                .andExpect(content().string(containsString("Expiry date (optional)")));
    }

    @Test void editFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/announcements/1/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/announcements/form"))
                .andExpect(content().string(containsString("value=\"Registration Open\"")));
    }

    @Test void editFormRedirectsWhenAnnouncementMissing() throws Exception {
        Mockito.when(announcementAdminService.findById(99L)).thenReturn(null);
        mvc.perform(get("/superadmin/announcements/99/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements"));
    }

    @Test void createWithValidDataRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/announcements")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Registration Open")
                        .param("message", "Enrolment is open.")
                        .param("announceDate", "2026-08-01")
                        .param("expiresOn", "2026-09-30")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements"));
    }

    @Test void createWithoutExpiryIsAllowed() throws Exception {
        mvc.perform(post("/superadmin/announcements")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Never Expires")
                        .param("message", "Stays up.")
                        .param("announceDate", "2026-08-01")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements"));
        Mockito.verify(announcementAdminService).create(any());
    }

    @Test void createWithMissingMessageRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/announcements")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "No Body")
                        .param("announceDate", "2026-08-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements/new"));
        Mockito.verify(announcementAdminService, Mockito.never()).create(any());
    }

    @Test void createWithoutAnnounceDateRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/announcements")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "No Date")
                        .param("message", "Body"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements/new"));
    }

    @Test void updateRedirectsToList() throws Exception {
        Mockito.when(announcementAdminService.update(anyLong(), any())).thenReturn(new SstsAnnouncement());
        mvc.perform(post("/superadmin/announcements/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Renamed")
                        .param("message", "Updated body")
                        .param("announceDate", "2026-09-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements"));
    }

    @Test void updateWithMissingTitleRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/announcements/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("message", "Body")
                        .param("announceDate", "2026-09-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements/1/edit"));
        Mockito.verify(announcementAdminService, Mockito.never()).update(anyLong(), any());
    }

    @Test void deleteRedirectsToList() throws Exception {
        Mockito.when(announcementAdminService.delete(1L)).thenReturn(true);
        mvc.perform(post("/superadmin/announcements/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements"));
    }

    @Test void nonSuperAdminGetsForbidden() throws Exception {
        mvc.perform(get("/superadmin/announcements").with(user("parent1").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
