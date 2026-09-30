package org.sstamilschool.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.sstamilschool.controller.PageController;
import org.sstamilschool.model.SstsAnnouncement;
import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.service.AnnouncementService;
import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.service.GalleryService;
import org.sstamilschool.service.TeamService;

import java.time.LocalDate;
import java.util.List;

@WebMvcTest(PageController.class)
@Import(TestSecurityConfig.class)
class PageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DonorService donorService;
    @MockitoBean EmailService emailService;
    @MockitoBean TeamService teamService;
    @MockitoBean CalendarService calendarService;
    @MockitoBean AnnouncementService announcementService;
    // PageController injects one service per public page; a @MockitoBean is
    // required for EVERY one of them or the slice fails to load (AGENTS.md).
    @MockitoBean GalleryService galleryService;

    @Test void homeShowsSchoolWelcome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sandy Springs Tamil School")));
    }

    @Test void homeRendersAnnouncementsInTheMarquee() throws Exception {
        SstsAnnouncement registration = new SstsAnnouncement();
        registration.setId(1L);
        registration.setTitle("Registration Open");
        registration.setMessage("Enrolment is open for 2026-27.");
        registration.setAnnounceDate(LocalDate.of(2026, 8, 1));
        registration.setActive(true);

        SstsAnnouncement volunteer = new SstsAnnouncement();
        volunteer.setId(2L);
        volunteer.setTitle("Volunteer Sign-ups");
        volunteer.setMessage("Sign-ups open at the front desk.");
        volunteer.setAnnounceDate(LocalDate.of(2026, 9, 1));
        volunteer.setActive(true);

        // Most recent first, exactly as the service returns them.
        org.mockito.Mockito.when(announcementService.getVisibleAnnouncements())
                .thenReturn(List.of(volunteer, registration));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                // Proves index.html rendered the DB-driven marquee, and that the
                // sequence is emitted twice (seamless CSS loop, 2nd copy aria-hidden).
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registration Open — Enrolment is open for 2026-27.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Volunteer Sign-ups — Sign-ups open at the front desk.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"School announcements\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-hidden=\"true\"")));

        int first = firstIndexOf("Registration Open — Enrolment is open for 2026-27.");
        int second = firstIndexOf("Registration Open — Enrolment is open for 2026-27.", first + 1);
        org.assertj.core.api.Assertions.assertThat(second).isGreaterThan(first);
    }

    @Test void homeHidesMarqueeWhenNoAnnouncements() throws Exception {
        org.mockito.Mockito.when(announcementService.getVisibleAnnouncements()).thenReturn(List.of());
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                // th:if on the section means no empty ticker is left on the page.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("aria-label=\"School announcements\""))));
    }

    /** Named to avoid clashing with the statically imported content() matcher. */
    private int firstIndexOf(String needle) throws Exception {
        return mvc.perform(get("/")).andReturn().getResponse().getContentAsString().indexOf(needle);
    }

    private int firstIndexOf(String needle, int from) throws Exception {
        return mvc.perform(get("/")).andReturn().getResponse().getContentAsString().indexOf(needle, from);
    }

    @Test void aboutRendersAboutPage() throws Exception {
        mvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(view().name("about"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("About us")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Our foundation")));
    }

    @Test void galleryRendersEntriesFromTheDatabase() throws Exception {
        SstsGalleryEvent withPhoto = new SstsGalleryEvent();
        withPhoto.setId(1L);
        withPhoto.setTitle("Pongal Celebration");
        withPhoto.setEventDate(LocalDate.of(2026, 1, 15));
        withPhoto.setImageUrl("/images/gallery/pongal.jpg");
        withPhoto.setDescription("Harvest festival.");
        withPhoto.setActive(true);

        SstsGalleryEvent withoutPhoto = new SstsGalleryEvent();
        withoutPhoto.setId(2L);
        withoutPhoto.setTitle("Annual Day");
        withoutPhoto.setEventDate(LocalDate.of(2025, 5, 10));
        withoutPhoto.setActive(true);

        org.mockito.Mockito.when(galleryService.getPublicEvents())
                .thenReturn(List.of(withPhoto, withoutPhoto));

        mvc.perform(get("/gallery"))
                .andExpect(status().isOk())
                .andExpect(view().name("gallery"))
                // Proves the template rendered, the date formatted via #temporals,
                // and the photo path emitted.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pongal Celebration")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/images/gallery/pongal.jpg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("January 2026")))
                // No image_url -> first-letter fallback, not a broken <img>.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event-fallback")));
    }

    @Test void galleryRendersEmptyState() throws Exception {
        org.mockito.Mockito.when(galleryService.getPublicEvents()).thenReturn(List.of());
        mvc.perform(get("/gallery"))
                .andExpect(status().isOk())
                .andExpect(view().name("gallery"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("coming soon")));
    }
}
