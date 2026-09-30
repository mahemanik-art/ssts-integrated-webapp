package org.sstamilschool.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.DonorAdminController;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.service.DonorPhotoStorageService;
import org.sstamilschool.service.DonorService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * @WebMvcTest slice for the donors module.
 *
 * <p>Two things are pinned here beyond routing. First, the list page really
 * RENDERS -- a @WebMvcTest resolves the template, so a broken Thymeleaf
 * expression in donors/list.html fails these tests rather than production.
 * Second, the three failure paths each redirect with a DIFFERENT message: a
 * service validation error, a unique-name race that the database caught, and a
 * duplicate-donor delete refusal. All three would otherwise be a bare HTTP 500
 * or a silent success.
 */
@WebMvcTest(DonorAdminController.class)
@Import(TestSecurityConfig.class)
class DonorAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean DonorService donorService;
    @MockitoBean DonorPhotoStorageService photoStorage;

    /**
     * TestSecurityConfig permits every URL but keeps @EnableMethodSecurity on, so
     * the controller's own @PreAuthorize("hasRole('SUPER_ADMIN')") is what gates
     * it -- the same defence-in-depth split as the other admin slices.
     */
    private static final org.springframework.test.web.servlet.request.RequestPostProcessor SUPER_ADMIN =
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user("ssts_admin").roles("SUPER_ADMIN");

    private static SstsDonor donor(long id, String name, boolean isPublic) {
        SstsDonor d = new SstsDonor();
        d.setId(id);
        d.setName(name);
        d.setTagline("Tagline " + id);
        d.setPhotoPath("/images/donors/" + id + ".jpg");
        d.setDisplayOrder((int) id);
        d.setActive(true);
        d.setPubliclyListed(isPublic);
        return d;
    }

    private void stubList(SstsDonor... donors) {
        when(donorService.findAllForAdmin()).thenReturn(List.of(donors));
        when(donorService.totalFor(any())).thenReturn(new BigDecimal("100.00"));
        when(photoStorage.isEnabled()).thenReturn(true);
    }

    // ---------------------------------------------------------------- list

    @Test
    void listRendersEveryDonorAndItsTotal() throws Exception {
        stubList(donor(1L, "Madras Photo Studios", true), donor(2L, "Rigel Spices", false));

        mvc.perform(get("/superadmin/donors").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/donors/list"))
                .andExpect(content().string(containsString("Madras Photo Studios")))
                .andExpect(content().string(containsString("Rigel Spices")))
                .andExpect(content().string(containsString("100.00")));
    }

    /**
     * The consent flag is the whole point of the module, so the list has to
     * distinguish "listed publicly" from "hidden" rather than showing one column
     * for both.
     */
    @Test
    void listShowsThePublicAndActiveFlagsSeparately() throws Exception {
        stubList(donor(1L, "Listed", true), donor(2L, "Unlisted", false));

        String html = mvc.perform(get("/superadmin/donors").with(SUPER_ADMIN))
                .andReturn().getResponse().getContentAsString();
        // Two separate flag columns, so the header row must carry both. The
        // consent flag is the load-bearing one: it decides whether a donor's
        // name reaches the public site.
        org.assertj.core.api.Assertions.assertThat(html).contains(">Public<");
        org.assertj.core.api.Assertions.assertThat(html).contains(">Active<");
    }

    @Test
    void listRendersAnEmptyState() throws Exception {
        when(donorService.findAllForAdmin()).thenReturn(List.of());
        when(photoStorage.isEnabled()).thenReturn(true);

        mvc.perform(get("/superadmin/donors").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No donors yet")));
    }

    @Test
    void listExplainsADisabledUploadBucket() throws Exception {
        stubList(donor(1L, "Acme", true));
        when(photoStorage.isEnabled()).thenReturn(false);

        mvc.perform(get("/superadmin/donors").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("APP_STORAGE_ENABLED")));
    }

    // ---------------------------------------------------------------- form

    @Test
    void newFormRendersAnEmptyDonor() throws Exception {
        when(photoStorage.isEnabled()).thenReturn(true);

        mvc.perform(get("/superadmin/donors/new").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/donors/form"))
                .andExpect(content().string(containsString("New donor")));
    }

    @Test
    void editFormRendersTheStoredValues() throws Exception {
        when(donorService.findById(1L)).thenReturn(donor(1L, "Acme Corp", true));
        when(photoStorage.isEnabled()).thenReturn(true);

        mvc.perform(get("/superadmin/donors/1/edit").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Acme Corp\"")))
                .andExpect(content().string(containsString("Tagline 1")));
    }

    /** A missing row is "not found", not a 404 -- the app's stated convention. */
    @Test
    void editingAMissingDonorRedirectsToTheList() throws Exception {
        when(donorService.findById(404L)).thenReturn(null);

        mvc.perform(get("/superadmin/donors/404/edit").with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donors"));
    }

    // -------------------------------------------------------------- create

    @Test
    void createSavesAndRedirects() throws Exception {
        mvc.perform(post("/superadmin/donors").with(SUPER_ADMIN).with(csrf())
                        .param("name", "Acme Corp")
                        .param("tagline", "Things"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donors"));

        verify(donorService).create(any(SstsDonor.class));
    }

    @Test
    void createTurnsAServiceValidationErrorIntoAReadableFlash() throws Exception {
        doThrow(new IllegalArgumentException("A donor named \"Acme Corp\" already exists."))
                .when(donorService).create(any(SstsDonor.class));

        mvc.perform(post("/superadmin/donors").with(csrf()).param("name", "Acme Corp").with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donors"))
                .andExpect(flashMessageContains("already exists"));
    }

    /**
     * The pre-check in the service is not atomic, so two admins saving the same
     * name at once is decided by the database. Without this catch that surfaces
     * as a bare 500 instead of a message the admin can act on.
     */
    @Test
    void createTurnsAUniqueConstraintRaceIntoAReadableFlash() throws Exception {
        doThrow(new DataIntegrityViolationException("uq_ssts_donors_name_lower"))
                .when(donorService).create(any(SstsDonor.class));

        mvc.perform(post("/superadmin/donors").with(csrf()).param("name", "Acme Corp").with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(flashMessageContains("already taken"));
    }

    @Test
    void createRefusesAFileUploadBeforeTheDonorHasAnId() throws Exception {
        org.springframework.mock.web.MockMultipartFile upload =
                new org.springframework.mock.web.MockMultipartFile(
                        "photo", "x.jpg", "image/jpeg", new byte[] { 1 });

        // .file(...) only exists on the multipart builder, which is reached via
        // .multipart(...). It has to come BEFORE .with(csrf()): with() is
        // declared to return the base MockHttpServletRequestBuilder, so a
        // .file() chained after it does not compile.
        mvc.perform(multipart("/superadmin/donors")
                        .file(upload)
                        .param("name", "Acme")
                        .with(SUPER_ADMIN)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flashMessageContains("Save the donor first"));
    }

    // -------------------------------------------------------------- update

    @Test
    void updateSavesAndRedirects() throws Exception {
        when(donorService.findById(1L)).thenReturn(donor(1L, "Acme Corp", true));

        mvc.perform(post("/superadmin/donors/1").with(SUPER_ADMIN).with(csrf())
                        .param("id", "1")
                        .param("name", "Acme Corp Renamed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donors"));

        verify(donorService).update(org.mockito.ArgumentMatchers.eq(1L), any(SstsDonor.class));
    }

    @Test
    void updatingAMissingDonorRedirectsWithAnError() throws Exception {
        when(donorService.findById(404L)).thenReturn(null);

        mvc.perform(post("/superadmin/donors/404").with(csrf()).param("name", "Ghost").with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(flashMessageContains("no longer exists"));
    }

    // -------------------------------------------------------------- delete

    @Test
    void deleteRemovesTheDonorAndItsPhoto() throws Exception {
        SstsDonor existing = donor(1L, "Acme Corp", true);
        when(donorService.findById(1L)).thenReturn(existing);

        mvc.perform(post("/superadmin/donors/1/delete").with(csrf()).with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donors"));

        verify(donorService).delete(1L);
        verify(photoStorage).delete("/images/donors/1.jpg");
    }

    @Test
    void deleteOfADonorWithGivingHistoryExplainsWhyItIsRefused() throws Exception {
        when(donorService.findById(1L)).thenReturn(donor(1L, "Acme Corp", true));
        doThrow(new IllegalStateException("\"Acme Corp\" has recorded donations and cannot be deleted."))
                .when(donorService).delete(1L);

        mvc.perform(post("/superadmin/donors/1/delete").with(csrf()).with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(flashMessageContains("cannot be deleted"));
    }

    @Test
    void aRefusedDeleteMustNotAlsoDeleteThePhoto() throws Exception {
        when(donorService.findById(1L)).thenReturn(donor(1L, "Acme Corp", true));
        doThrow(new IllegalStateException("has recorded donations")).when(donorService).delete(1L);

        mvc.perform(post("/superadmin/donors/1/delete").with(csrf()).with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection());

        // The row survived, so its photo must survive too.
        org.mockito.Mockito.verify(photoStorage, org.mockito.Mockito.never()).delete(any());
    }

    // --------------------------------------------------------- authorization

    /**
     * TestSecurityConfig permits every URL so the controller's own
     * @PreAuthorize is what must reject a non-super-admin. This pins that
     * defence-in-depth layer, which is separate from SecurityConfig's
     * /superadmin/** rule.
     */
    @Test
    void aPlainAdminIsRefusedTheDonorsModule() throws Exception {
        mvc.perform(get("/superadmin/donors")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("plain_admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousVisitorIsRefusedTheDonorsModule() throws Exception {
        mvc.perform(get("/superadmin/donors")).andExpect(status().isForbidden());
    }

    /**
     * A crafted POST must not be able to set the seed marker, and an ordinary
     * save must not ERASE it either. The form has no seedKey field, so without
     * the binder it would bind as null and the merge would blank the marker on
     * every seeded donor -- invisible until someone depends on it.
     */
    @Test
    void aClientCannotSetTheSeedMarker() throws Exception {
        mvc.perform(post("/superadmin/donors").with(SUPER_ADMIN).with(csrf())
                        .param("name", "Acme Corp")
                        .param("seedKey", "attacker-controlled"))
                .andExpect(status().is3xxRedirection());

        org.mockito.ArgumentCaptor<SstsDonor> captor =
                org.mockito.ArgumentCaptor.forClass(SstsDonor.class);
        verify(donorService).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSeedKey()).isNull();
    }

    /** A crafted id must not retarget the save onto a different donor's row. */
    @Test
    void aClientCannotRetargetTheUpdateWithAPostedId() throws Exception {
        when(donorService.findById(1L)).thenReturn(donor(1L, "Acme Corp", true));

        mvc.perform(post("/superadmin/donors/1").with(SUPER_ADMIN).with(csrf())
                        .param("id", "99")
                        .param("name", "Acme Corp"))
                .andExpect(status().is3xxRedirection());

        org.mockito.ArgumentCaptor<SstsDonor> captor =
                org.mockito.ArgumentCaptor.forClass(SstsDonor.class);
        verify(donorService).update(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        // The id comes from the PATH, not the body.
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    private static org.springframework.test.web.servlet.ResultMatcher flashMessageContains(String fragment)
            throws Exception {
        return result -> org.assertj.core.api.Assertions
                .assertThat(result.getFlashMap().get("error").toString())
                .contains(fragment);
    }
}
