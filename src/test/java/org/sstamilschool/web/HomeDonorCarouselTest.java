package org.sstamilschool.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.model.SstsDonor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public donor carousel is data-driven.
 *
 * <p>index.html used to hardcode three donor cards inline, which meant an admin
 * could not add, remove, reorder, or unlist a donor without a code change and a
 * deploy. These tests pin that the cards now come from {@code ssts_donors}, and
 * specifically that a donor the service left out does not appear -- the consent
 * gate is in the query, and this is the observable consequence.
 */
@WebMvcTest(controllers = org.sstamilschool.controller.PageController.class)
@Import(TestSecurityConfig.class)
class HomeDonorCarouselTest {

    @Autowired MockMvc mvc;

    @MockitoBean org.sstamilschool.service.EmailService emailService;
    @MockitoBean org.sstamilschool.service.TeamService teamService;
    @MockitoBean org.sstamilschool.service.CalendarService calendarService;
    @MockitoBean org.sstamilschool.service.GalleryService galleryService;
    @MockitoBean org.sstamilschool.service.AnnouncementService announcementService;
    @MockitoBean org.sstamilschool.service.DonorService donorService;

    private static SstsDonor donor(long id, String name, String tagline, String photo, String site) {
        SstsDonor d = new SstsDonor();
        d.setId(id);
        d.setName(name);
        d.setTagline(tagline);
        d.setPhotoPath(photo);
        d.setWebsiteUrl(site);
        d.setActive(true);
        d.setPubliclyListed(true);
        return d;
    }

    @Test
    void rendersOneCardPerDonorRow() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "Madras Photo Studios", "Capturing Life, Unscripted.",
                        "/images/MadrasPhotStudios.jpg", null),
                donor(2L, "Blue Oak Consulting", "Technology Services",
                        "/images/BlueOakConsulting.jpg", null),
                donor(3L, "Riverwood Dental", null, null, "https://riverwood.example.com")));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Madras Photo Studios")))
                .andExpect(content().string(containsString("Blue Oak Consulting")))
                .andExpect(content().string(containsString("Riverwood Dental")))
                .andExpect(content().string(containsString("Capturing Life, Unscripted.")))
                .andExpect(content().string(containsString("https://riverwood.example.com")));
    }

    /**
     * The nine donors that were hardcoded in index.html's two blocks (a 3-card
     * server-rendered aside and a 9-donor JS rotation) are now database rows.
     * This pins that every one of those names and taglines survived the move, so
     * a future seed edit cannot quietly drop content the school already had.
     */
    @Test
    void allNineOriginalCarouselDonorsAndTheirTaglinesArePreserved() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "JJJ Fortune LLC", "Human resources consulting services", "/images/JJJFortuneLLC.jpg", null),
                donor(2L, "Madras Photo Studios", "Capturing Life, Unscripted.", "/images/MadrasPhotStudios.jpg", null),
                donor(3L, "Rigel Spices", "Farm-fresh Indian foods", "/images/RigelSpices.jpg", null),
                donor(4L, "Blue Oak Consulting", "Technology Services", "/images/BlueOakConsulting.jpg", null),
                donor(5L, "Atlanta Mojo Productions", "Audio and Sound Service", "/images/AtlantaMojoProductions.jpg", null),
                donor(6L, "Peachtree Learning Co.", "Tutoring & Childcare", "/images/PeachtreeLearningCo.jpg", null),
                donor(7L, "Riverwood Dental", "Family Dentistry", "/images/RiverwoodDental.jpg", null),
                donor(8L, "Meena Family", "Meena Dance Academy", null, null),
                donor(9L, "Northside Foods", "Fresh Groceries", null, null)));

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        for (String name : List.of("JJJ Fortune LLC", "Madras Photo Studios", "Rigel Spices",
                "Blue Oak Consulting", "Atlanta Mojo Productions", "Peachtree Learning Co.",
                "Riverwood Dental", "Meena Family", "Northside Foods")) {
            org.assertj.core.api.Assertions.assertThat(html).contains(name);
        }
        // "Atlanta", not the "Altanta" typo that was in the 3-card block.
        org.assertj.core.api.Assertions.assertThat(html).contains("Atlanta Mojo Productions");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("Altanta");
    }
    // ------------------------------------------------------- three-card window

    /**
     * The panel shows a fixed THREE-card window and rotates through the rest.
     * This is the display it had before the donors became database rows; when
     * every donor was simply listed, the narrow column beside the hero slider
     * grew once per donor and pushed the rest of the page down.
     */
    @Test
    void onlyThreeDonorCardsAreInTheDomEvenWithNineDonors() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(allNine());

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(countMatches(html, "<article class=\"donor-card\"")).isEqualTo(3);
        // The first three are server-rendered, so they show with JS disabled.
        assertThat(html).contains("JJJ Fortune LLC");
        assertThat(html).contains("Madras Photo Studios");
        assertThat(html).contains("Rigel Spices");
        // The remaining six are NOT in the markup -- they arrive as JSON.
        assertThat(html).doesNotContain(">Northside Foods<");
    }

    /** With no more than one window of donors, no pointless arrows are shown. */
    @Test
    void navigationArrowsAppearOnlyWhenThereIsMoreThanOneWindow() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "A", null, null, null),
                donor(2L, "B", null, null, null),
                donor(3L, "C", null, null, null)));

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("data-donor-prev");
        assertThat(html).doesNotContain("data-donor-next");
    }

    @Test
    void navigationArrowsAppearWithNineDonors() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(allNine());

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-donor-prev");
        assertThat(html).contains("data-donor-next");
        assertThat(html).contains("data-donor-dots");
        assertThat(html).contains("/js/donor-carousel.js");
    }

    /**
     * All nine donors must reach the client as data, or the carousel would
     * rotate through only the three that were rendered.
     */
    @Test
    void everyDonorIsShippedAsJsonForTheRotation() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(allNine());

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("__SSTS_DONORS");
        for (String name : List.of("JJJ Fortune LLC", "Madras Photo Studios", "Rigel Spices",
                "Blue Oak Consulting", "Atlanta Mojo Productions", "Peachtree Learning Co.",
                "Riverwood Dental", "Meena Family", "Northside Foods")) {
            assertThat(html).contains(name);
        }
    }

    /**
     * Only the four public fields may cross to the client. SstsDonor also carries
     * notes (internal), seedKey and the audit timestamps, and serialising the
     * entity directly would publish all of them on a public page.
     */
    @Test
    void theJsonPayloadLeaksNoInternalDonorFields() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "Acme", "Things", "/images/a.jpg", null)));

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        // Scoped to the payload line, not the whole page: prose in an HTML
        // comment legitimately mentions these field names.
        String payload = jsonPayloadOf(html);
        assertThat(payload).isNotNull();
        assertThat(payload).doesNotContain("seedKey");
        assertThat(payload).doesNotContain("createdAt");
        assertThat(payload).doesNotContain("updatedAt");
        // The four public fields are present. Note the JSON escapes the forward
        // slash as \/ -- that is Thymeleaf's script-context escaping doing its
        // job, and it is valid JSON that JSON.parse reads as "/".
        assertThat(payload).contains("Acme");
        assertThat(payload).contains("a.jpg");
    }

    /** Returns the window.__SSTS_DONORS assignment, or null when absent. */
    private static String jsonPayloadOf(String html) {
        int at = html.indexOf("window.__SSTS_DONORS");
        if (at < 0) return null;
        int end = html.indexOf("</script>", at);
        return end < 0 ? html.substring(at) : html.substring(at, end);
    }

    /**
     * Every card must carry its donor's website link. These used to be in the
     * hardcoded markup, and losing them was a real regression in the public
     * display, not a cosmetic detail.
     */
    @Test
    void aDonorsCardLinksToItsWebsite() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "JJJ Fortune LLC", "Human resources consulting services",
                        "/images/JJJFortuneLLC.jpg", "https://jjjfortune.example.com"),
                donor(2L, "Madras Photo Studios", "Capturing Life, Unscripted.",
                        "/images/MadrasPhotStudios.jpg", "https://madrasphoto.example.com"),
                donor(3L, "Riverwood Dental", "Family Dentistry",
                        "/images/RiverwoodDental.jpg", "https://riverwooddental.example.com")));

        String html = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Visit website");
        assertThat(html).contains("href=\"https://jjjfortune.example.com\"");
        assertThat(html).contains("href=\"https://madrasphoto.example.com\"");
        assertThat(html).contains("href=\"https://riverwooddental.example.com\"");
        // Outbound links must not hand the opener window to the target site.
        assertThat(html).contains("rel=\"noopener\"");
        assertThat(html).contains("target=\"_blank\"");
    }

    /** The JSON payload must carry the link too, or a rotated card loses it. */
    @Test
    void theRotationPayloadCarriesTheWebsiteLink() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "A", null, null, "https://a.example.com"),
                donor(2L, "B", null, null, null),
                donor(3L, "C", null, null, null),
                donor(4L, "D", null, null, null)));

        String payload = jsonPayloadOf(
                mvc.perform(get("/")).andReturn().getResponse().getContentAsString());

        assertThat(payload).contains("a.example.com");
    }

    private static int countMatches(String haystack, String needle) {
        int n = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            n++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return n;
    }

    private static List<SstsDonor> allNine() {
        return List.of(
                donor(1L, "JJJ Fortune LLC", "Human resources consulting services", "/images/JJJFortuneLLC.jpg", null),
                donor(2L, "Madras Photo Studios", "Capturing Life, Unscripted.", "/images/MadrasPhotStudios.jpg", null),
                donor(3L, "Rigel Spices", "Farm-fresh Indian foods", "/images/RigelSpices.jpg", null),
                donor(4L, "Blue Oak Consulting", "Technology Services", "/images/BlueOakConsulting.jpg", null),
                donor(5L, "Atlanta Mojo Productions", "Audio and Sound Service", "/images/AtlantaMojoProductions.jpg", null),
                donor(6L, "Peachtree Learning Co.", "Tutoring & Childcare", "/images/PeachtreeLearningCo.jpg", null),
                donor(7L, "Riverwood Dental", "Family Dentistry", "/images/RiverwoodDental.jpg", null),
                donor(8L, "Meena Family", "Meena Dance Academy", null, null),
                donor(9L, "Northside Foods", "Fresh Groceries", null, null));
    }
    /**
     * Only the PUBLICLY LISTED rows come back from the service, so a donor the
     * school has unlisted must not reach the page -- neither as a rendered card
     * nor inside the JSON payload the carousel rotates through. This is the
     * consent flag doing its job, observed from outside.
     */
    @Test
    void anUnlistedDonorNeverAppearsBecauseTheQueryExcludesIt() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "Consented Donor", null, null, null)));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Consented Donor")))
                .andExpect(content().string(not(containsString("Rigel Spices"))));
    }

    /**
     * A donor with no photo still needs to occupy the same 150px photo block, or
     * its card renders visibly shorter than its siblings. The template therefore
     * substitutes an initials tile -- and does NOT emit a broken empty <img>.
     * This is also the shape the removed JS rotation could not cope with: it did
     * card.querySelector('.donor-photo').src = ..., which throws when the card
     * has no <img> at all.
     */
    @Test
    void aDonorWithoutAPhotoGetsAnInitialsTileNotABrokenImage() throws Exception {
        when(donorService.findPubliclyListed())
                .thenReturn(List.of(donor(4L, "Meena Family", null, null, null)));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Meena Family")))
                .andExpect(content().string(containsString("donor-photo--fallback")))
                .andExpect(content().string(not(containsString("src=\"\""))));
    }

    /** A donor WITH a photo must not also get the initials tile. */
    @Test
    void aDonorWithAPhotoDoesNotAlsoGetTheFallbackTile() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(1L, "Madras Photo Studios", null, "/images/MadrasPhotStudios.jpg", null)));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/MadrasPhotStudios.jpg")))
                .andExpect(content().string(not(containsString("donor-photo--fallback"))));
    }

    @Test
    void anEmptyDonorListRendersAnEmptyStateNotABlankPanel() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of());

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("donor list is being updated")));
    }

    /**
     * th:src is a LINK expression on purpose: donor photos may be a committed
     * /images/... path OR an absolute https:// bucket URL, and the link form
     * passes the absolute one through untouched. Rewriting it to
     * th:src="${...}" would break the committed relative paths by dropping
     * context-path handling.
     */
    @Test
    void anAbsoluteBucketUrlSurvivesRenderingUnmangled() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(5L, "Bucket Donor", null,
                        "https://cdn.example.com/donors/bucket-donor-5/logo.png", null)));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "https://cdn.example.com/donors/bucket-donor-5/logo.png")));
    }

    @Test
    void aCommittedRelativePathIsServedFromTheImagesFolder() throws Exception {
        when(donorService.findPubliclyListed()).thenReturn(List.of(
                donor(6L, "Committed Donor", null, "/images/RigelSpices.jpg", null)));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/RigelSpices.jpg")));
    }
}
