package org.sstamilschool.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.PageController;
import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.service.AnnouncementService;
import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.service.GalleryService;
import org.sstamilschool.service.TeamService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public gallery renders each photo with {@code th:src="@{...}"}, a
 * Thymeleaf LINK expression. That matters because a stored photo is now either
 * a relative {@code /images/...} path (a committed file) or an absolute
 * {@code https://} URL (a bucket object), and the two must be emitted
 * differently:
 *
 * <ul>
 *   <li>relative -> {@code /images/gallery/x.jpg} (context path prepended)</li>
 *   <li>absolute -> {@code https://cdn.example.com/gallery/x.jpg} untouched</li>
 * </ul>
 *
 * <p>If a LINK expression mangled an absolute URL -- prepending a context path,
 * say -- every uploaded photo would render broken while the committed ones kept
 * working, which looks like an upload bug rather than a template bug. This
 * pins both forms through the real template engine.
 *
 * <p>The mock beans are required: a @WebMvcTest slice needs one for every
 * service PageController injects.
 */
@WebMvcTest(PageController.class)
@Import(TestSecurityConfig.class)
class GalleryImageUrlRenderingTest {

    @Autowired MockMvc mvc;

    @MockitoBean DonorService donorService;

    @MockitoBean EmailService emailService;
    @MockitoBean TeamService teamService;
    @MockitoBean CalendarService calendarService;
    @MockitoBean GalleryService galleryService;
    @MockitoBean AnnouncementService announcementService;

    private static SstsGalleryEvent eventWith(String url) {
        SstsGalleryEvent e = new SstsGalleryEvent();
        e.setId(1L);
        e.setTitle("Pongal");
        e.setEventDate(LocalDate.of(2026, 1, 15));
        e.setImageUrl(url);
        return e;
    }

    @Test
    void rendersAnAbsoluteBucketUrlWithoutManglingIt() throws Exception {
        when(galleryService.getPublicEvents()).thenReturn(
                List.of(eventWith("https://cdn.example.com/gallery/pongal-1/1.jpg")));

        String html = mvc.perform(get("/gallery"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("an absolute https URL must be emitted verbatim, not treated as a "
                        + "context-relative path")
                .contains("https://cdn.example.com/gallery/pongal-1/1.jpg")
                .doesNotContain("src=\"/https://");
    }

    @Test
    void stillRendersACommittedRelativePath() throws Exception {
        when(galleryService.getPublicEvents()).thenReturn(
                List.of(eventWith("/images/gallery/pongal-celebration-1/1.png")));

        String html = mvc.perform(get("/gallery"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/images/gallery/pongal-celebration-1/1.png");
    }

    @Test
    void rendersEverySlideOfAMultiPhotoEntry() throws Exception {
        SstsGalleryEvent e = eventWith(null);
        e.setSlides(List.of(
                "https://cdn.example.com/gallery/pongal-1/1.jpg",
                "https://cdn.example.com/gallery/pongal-1/2.jpg",
                "https://cdn.example.com/gallery/pongal-1/3.jpg"));
        when(galleryService.getPublicEvents()).thenReturn(List.of(e));

        String html = mvc.perform(get("/gallery"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("https://cdn.example.com/gallery/pongal-1/1.jpg")
                .contains("https://cdn.example.com/gallery/pongal-1/2.jpg")
                .contains("https://cdn.example.com/gallery/pongal-1/3.jpg");
    }
}
