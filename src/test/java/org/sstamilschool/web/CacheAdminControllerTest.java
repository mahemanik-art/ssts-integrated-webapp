package org.sstamilschool.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.sstamilschool.controller.CacheAdminController;
import org.sstamilschool.controller.PageController;
import org.sstamilschool.dto.CalendarView;
import org.sstamilschool.service.AnnouncementService;
import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.service.GalleryService;
import org.sstamilschool.service.TeamService;
import org.sstamilschool.util.AcademicYear;

@WebMvcTest({ PageController.class, CacheAdminController.class })
@Import(TestSecurityConfig.class)
class CacheAdminControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DonorService donorService;
    @MockitoBean EmailService emailService;
    @MockitoBean TeamService teamService;
    @MockitoBean CalendarService calendarService;
    @MockitoBean AnnouncementService announcementService;
    // This slice also loads PageController, so it needs a mock for every service
    // that controller injects -- including GalleryService.
    @MockitoBean GalleryService galleryService;

    @Test void teamPageUsesCachedService() throws Exception {
        when(teamService.getPublicTeamMembers()).thenReturn(List.of());

        mvc.perform(get("/team"))
                .andExpect(status().isOk())
                .andExpect(view().name("team"));

        verify(teamService).getPublicTeamMembers();
    }

    @Test void patchReloadsTeamCache() throws Exception {
        when(teamService.getPublicTeamMembers()).thenReturn(List.of());

        mvc.perform(patch("/api/team/cache").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reloaded"))
                .andExpect(jsonPath("$.resource").value("team"))
                .andExpect(jsonPath("$.count").value(0));

        verify(teamService).evictTeamMembers();
        verify(teamService).getPublicTeamMembers();
    }

    @Test void patchReloadsCalendarCache() throws Exception {
        String year = AcademicYear.current();
        when(calendarService.getCurrentCalendar())
                .thenReturn(new CalendarView(year, AcademicYear.label(year),
                        List.of(), List.of(), List.of(), List.of(), 7));

        mvc.perform(patch("/api/calendar/cache").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reloaded"))
                .andExpect(jsonPath("$.resource").value("calendar"))
                .andExpect(jsonPath("$.academicYear").value(year))
                .andExpect(jsonPath("$.count").value(7));

        verify(calendarService).evictCalendarEvents();
        verify(calendarService).getCurrentCalendar();
    }

    @Test void galleryCacheReloadsAndReportsCount() throws Exception {
        when(galleryService.getPublicEvents()).thenReturn(List.of());

        mvc.perform(patch("/api/gallery/cache").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reloaded"))
                .andExpect(jsonPath("$.resource").value("gallery"))
                .andExpect(jsonPath("$.count").value(0));

        verify(galleryService).evictGalleryEvents();
        verify(galleryService).getPublicEvents();
    }
}
