package org.sstamilschool.web;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.GalleryAdminController;
import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.service.GalleryAdminService;

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

/** Mirrors CalendarAdminControllerTest for the gallery module. */
@WebMvcTest(GalleryAdminController.class)
@Import(TestSecurityConfig.class)
class GalleryAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GalleryAdminService galleryAdminService;

    @BeforeEach
    void setup() {
        SstsGalleryEvent event = new SstsGalleryEvent();
        event.setId(1L);
        event.setTitle("Pongal Celebration");
        event.setEventDate(LocalDate.of(2026, 1, 15));
        event.setImageUrl("/images/gallery/pongal.jpg");
        event.setDescription("Harvest festival.");
        event.setDisplayOrder(10);
        event.setActive(true);

        Mockito.when(galleryAdminService.findAll()).thenReturn(List.of(event));
        Mockito.when(galleryAdminService.findById(1L)).thenReturn(event);
        Mockito.when(galleryAdminService.newEvent()).thenReturn(new SstsGalleryEvent());
    }

    @Test void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/superadmin/gallery"))
                .andExpect(status().isForbidden());
    }

    @Test void listRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/gallery").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/gallery/list"))
                .andExpect(content().string(containsString("Pongal Celebration")))
                .andExpect(content().string(containsString("/images/gallery/pongal.jpg")))
                .andExpect(content().string(containsString("Harvest festival.")));
    }

    @Test void newFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/gallery/new").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/gallery/form"));
    }

    @Test void editFormRendersForSuperAdmin() throws Exception {
        mvc.perform(get("/superadmin/gallery/1/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/gallery/form"))
                .andExpect(content().string(containsString("/images/gallery/pongal.jpg")));
    }

    @Test void editFormRedirectsWhenEntryMissing() throws Exception {
        Mockito.when(galleryAdminService.findById(99L)).thenReturn(null);
        mvc.perform(get("/superadmin/gallery/99/edit").with(user("ssts_admin").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery"));
    }

    @Test void createWithValidDataRedirectsToList() throws Exception {
        mvc.perform(post("/superadmin/gallery")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Pongal Celebration")
                        .param("eventDate", "2027-01-14")
                        .param("imageUrl", "/images/gallery/pongal.jpg")
                        .param("description", "Harvest festival.")
                        .param("displayOrder", "10")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery"));
    }

    @Test void createWithMissingTitleRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/gallery")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("eventDate", "2027-01-14"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery/new"));
    }

    @Test void createWithoutDateRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/gallery")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "No Date"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery/new"));
    }

    @Test void updateRedirectsToList() throws Exception {
        Mockito.when(galleryAdminService.update(anyLong(), any(), any())).thenReturn(new SstsGalleryEvent());
        mvc.perform(post("/superadmin/gallery/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("title", "Renamed")
                        .param("eventDate", "2026-03-02")
                        .param("displayOrder", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery"));
    }

    @Test void updateWithMissingTitleRedirectsWithError() throws Exception {
        mvc.perform(post("/superadmin/gallery/1")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf())
                        .param("eventDate", "2026-03-02"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery/1/edit"));
        Mockito.verify(galleryAdminService, Mockito.never()).update(anyLong(), any(), any());
    }

    @Test void deleteRedirectsToList() throws Exception {
        Mockito.when(galleryAdminService.delete(1L)).thenReturn(true);
        mvc.perform(post("/superadmin/gallery/1/delete")
                        .with(user("ssts_admin").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/gallery"));
    }

      @Test void nonSuperAdminGetsForbidden() throws Exception {
          mvc.perform(get("/superadmin/gallery").with(user("parent1").roles("USER")))
                  .andExpect(status().isForbidden());
      }

      /**
       * The DB CHECKs chk_ssts_gallery_events_image_url/_image_urls reject any
       * path that is not a relative /images/ path. That is correct behaviour,
       * but an uncaught DataIntegrityViolationException surfaced to the admin
       * as a bare HTTP 500 with no explanation -- see the two-regex bug where
       * the multi-line slider regex could not match a 2+ photo entry at all,
       * making every multi-photo save fail. This locks in the friendly
       * redirect-with-flash-error instead.
       */
      @Test void createTranslatesABadImagePathIntoAFlashErrorNotA500() throws Exception {
          Mockito.when(galleryAdminService.create(any(), any()))
                  .thenThrow(new DataIntegrityViolationException(
                          "chk_ssts_gallery_events_image_url"));
          mvc.perform(post("/superadmin/gallery")
                          .with(user("ssts_admin").roles("SUPER_ADMIN"))
                          .with(csrf())
                          .param("title", "Pongal")
                          .param("eventDate", "2026-03-02")
                          .param("imageUrl", "https://example.com/x.jpg"))
                  .andExpect(status().is3xxRedirection())
                  .andExpect(redirectedUrl("/superadmin/gallery/new"));
      }

      @Test void updateTranslatesABadImagePathIntoAFlashErrorNotA500() throws Exception {
          Mockito.when(galleryAdminService.update(anyLong(), any(), any()))
                  .thenThrow(new DataIntegrityViolationException(
                          "chk_ssts_gallery_events_image_urls"));
          mvc.perform(post("/superadmin/gallery/1")
                          .with(user("ssts_admin").roles("SUPER_ADMIN"))
                          .with(csrf())
                          .param("title", "Pongal")
                          .param("eventDate", "2026-03-02")
                          .param("imageUrl", "/images/gallery/my photo.jpg"))
                  .andExpect(status().is3xxRedirection())
                  .andExpect(redirectedUrl("/superadmin/gallery/1/edit"));
      }
  }
