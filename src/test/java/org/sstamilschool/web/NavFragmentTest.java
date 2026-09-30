package org.sstamilschool.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.sstamilschool.controller.PageController;
import org.sstamilschool.controller.TeamAdminController;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.service.AnnouncementService;
import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.service.GalleryService;
import org.sstamilschool.service.TeamAdminService;
import org.sstamilschool.service.TeamService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Locks in that there is exactly ONE navigation, and that it adapts to the viewer
 * on a PUBLIC page -- not only on /dashboard.
 *
 * <p>The nav used to be hand-copied into all 22 templates and had drifted apart:
 * the public pages showed neither Dashboard nor Logout, and the super-admin
 * calendar/team screens had lost the Gallery and Announcements module links
 * entirely. Every template now renders fragments/nav.html, and CommonModelAdvice
 * supplies the attributes it needs to every controller, so these tests deliberately
 * assert on /about (a public page) rather than /dashboard -- that combination is
 * the regression that actually happened.
 */
@WebMvcTest(controllers = {PageController.class, TeamAdminController.class})
@Import(TestSecurityConfig.class)
class NavFragmentTest {

    @Autowired MockMvc mvc;
    @MockitoBean DonorService donorService;
    @MockitoBean EmailService emailService;
    @MockitoBean TeamService teamService;
    @MockitoBean CalendarService calendarService;
    @MockitoBean AnnouncementService announcementService;
    @MockitoBean GalleryService galleryService;
    // Only for the /superadmin/team screen below, so the nav can be checked on a
    // module page as well as on a public page.
    @MockitoBean TeamAdminService teamAdminService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String roleName, String userType) {
        SstsRole role = new SstsRole();
        role.setName(roleName);
        SstsUser user = new SstsUser();
        user.setId(99L);
        user.setFullName("Nav Tester");
        user.setUserType(userType);
        user.setRole(role);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private String aboutPage() throws Exception {
        return mvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(view().name("about"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test void anonymousSeesOnlyThePublicLinks() throws Exception {
        String html = aboutPage();

        // All six public destinations are present...
        for (String href : new String[] {"/", "/about", "/calendar", "/gallery", "/team", "/contact"}) {
            assertThat(html).contains("href=\"" + href + "\"");
        }
        // ...and nothing account-scoped leaks out to a logged-out visitor.
        assertThat(html).doesNotContain("href=\"/dashboard\"");
        assertThat(html).doesNotContain("href=\"/superadmin\"");
        // The whole super-admin dropdown wrapper is absent too, not just the link.
        // This used to be asserted as doesNotContain("nav-submenu"), but that was
        // only ever a PROXY for "the super-admin module dropdown must not leak":
        // the PUBLIC About dropdown is now also a .nav-submenu and renders for
        // every visitor, so the proxy no longer holds. The intent survives --
        // nav-superadmin is the super-admin-only wrapper -- and the
        // href="/superadmin" assertion above still covers the link itself.
        assertThat(html).doesNotContain("nav-superadmin");
        assertThat(html).doesNotContain("logout-button");
    }

    @Test void superAdminSeesTheModulesFromAPublicPage() throws Exception {
        authenticate("super_admin", "admin");
        String html = aboutPage();

        // The regression this test exists for: on /about -- not /dashboard -- a
        // super-admin still gets Super Admin and all eight modules.
        // A super-admin no longer gets a Dashboard link: they now land on
        // /superadmin after login (RoleAwareAuthenticationSuccessHandler), so a
        // Dashboard link would only bounce them back to /dashboard and then
        // re-route to /superadmin.
        assertThat(html).doesNotContain("href=\"/dashboard\"");
        assertThat(html).contains("href=\"/superadmin\"");
        assertThat(html).contains("href=\"/superadmin/calendar\"");
        assertThat(html).contains("href=\"/superadmin/team\"");
        assertThat(html).contains("href=\"/superadmin/gallery\"");
        assertThat(html).contains("href=\"/superadmin/announcements\"");
        assertThat(html).contains("href=\"/superadmin/users\"");
        assertThat(html).contains("href=\"/superadmin/roles\"");
        assertThat(html).contains("href=\"/superadmin/reports\"");
        // The eight module links live inside the dropdown wrapper, not flat.
        assertThat(html).contains("nav-submenu");

        // The eight module links are INSIDE <ul class="nav-submenu">, which is
        // inside the .nav-superadmin wrapper, which is a flex item of the <nav>
        // -- not five flat siblings. Guarded on DOCUMENT ORDER so collapsing
        // them back out of the dropdown is what fails, not just the class name.
        int wrapper = html.indexOf("class=\"nav-superadmin\"");
        // The search for the submenu STARTS at the super-admin wrapper: the
        // public About dropdown is also a .nav-submenu and comes earlier in the
        // document, so a plain indexOf would anchor the loop on the About
        // submenu and assert the wrong thing.
        int submenu = html.indexOf("class=\"nav-submenu\"", wrapper);
        int parent = html.indexOf("href=\"/superadmin\"");
        assertThat(wrapper).isGreaterThanOrEqualTo(0);
        assertThat(parent).isGreaterThan(wrapper);
        assertThat(submenu).isGreaterThan(parent);
        for (String href : new String[] {"/superadmin/calendar", "/superadmin/team",
                "/superadmin/gallery", "/superadmin/announcements",
                "/superadmin/users", "/superadmin/roles",
                "/superadmin/reports"}) {
            assertThat(html.indexOf("href=\"" + href + "\"")).isGreaterThan(submenu);
        }
    }

    /**
     * Locks down that /about and /team are grouped inside the PUBLIC About
     * dropdown rather than rendered as two flat siblings in the {@code <nav>}.
     *
     * <p>The regression: the "Our Team" link used to sit next to the "About" link
     * directly in the nav, so the two were peers. Grouping them under a
     * click-to-toggle "About" parent means "Our Team" must now live INSIDE the
     * {@code .nav-about} wrapper -- if the wrapper were dropped and the two links
     * flattened back out, the parent anchor would lose its submenu and
     * "Our Team" would follow the dropdown in document order instead of sitting
     * inside it.
     */
    @Test void aboutDropdownGroupsAboutUsAndOurTeam() throws Exception {
        // Anonymous: the About dropdown is PUBLIC, so it must render for a
        // logged-out visitor (unlike the super-admin dropdown).
        String html = aboutPage();

        // The wrapper is matched WITHOUT its closing quote: th:classappend
        // appends nav-about--current on /about, so the rendered attribute is
        // class="nav-about nav-about--current" and the exact class="nav-about"
        // substring does not occur.
        int wrapper = html.indexOf("class=\"nav-about");
        int submenu = html.indexOf("class=\"nav-submenu\"", wrapper);
        assertThat(wrapper).isGreaterThanOrEqualTo(0);
        assertThat(submenu).isGreaterThan(wrapper);

        // BOTH destinations are submenu items -- they appear after the submenu in
        // document order, not as flat siblings before/after the dropdown.
        assertThat(html.indexOf("href=\"/about\"", submenu)).isGreaterThan(submenu);
        assertThat(html.indexOf("href=\"/team\"", submenu)).isGreaterThan(submenu);

        // The toggle parent anchor is still labelled exactly "About" -- the
        // "About Us" subitem must not be mistaken for it.
        assertThat(anchorIsActive(html, "About")).isTrue();

        // The "Our Team" anchor sits INSIDE the About wrapper, i.e. after the
        // wrapper opens, rather than beside it as a flat sibling.
        int ourTeam = anchorIndex(html, "Our Team");
        assertThat(ourTeam).isGreaterThan(wrapper);
        assertThat(ourTeam).isGreaterThan(submenu);
    }

    @Test void logoutIsAPostFormNotALink() throws Exception {
        authenticate("super_admin", "admin");
        String html = aboutPage();

        // Spring Security 6 form logout 404/405s on GET, so logout must stay a
        // POST <form>. Thymeleaf injects the CSRF token into it.
        assertThat(html).contains("<form");
        assertThat(html).contains("action=\"/logout\"");
        assertThat(html).contains("method=\"post\"");
        assertThat(html).contains("logout-button");
        assertThat(html).doesNotContain("href=\"/logout\"");
        assertThat(html).contains("name=\"_csrf\"");
    }

    @Test void plainAdminSeesDashboardButNoModuleLinks() throws Exception {
        // user_type is still 'admin'; only the role differs. Authorization comes
        // from the role, so the module links must stay hidden.
        authenticate("admin", "admin");
        String html = aboutPage();

        assertThat(html).contains("href=\"/dashboard\"");
        assertThat(html).contains("logout-button");
        assertThat(html).doesNotContain("href=\"/superadmin\"");
        // The whole dropdown wrapper is absent too, not just the link.
        assertThat(html).doesNotContain("nav-superadmin");
    }

    @Test void parentSeesDashboardAndLogoutButNoModuleLinks() throws Exception {
        authenticate("parent", "parent");
        String html = aboutPage();

        assertThat(html).contains("href=\"/dashboard\"");
        assertThat(html).contains("logout-button");
        assertThat(html).doesNotContain("href=\"/superadmin\"");
        // The whole dropdown wrapper is absent too, not just the link.
        assertThat(html).doesNotContain("nav-superadmin");
    }

    @Test void currentPageLinkIsMarkedActive() throws Exception {
        String html = aboutPage();

        assertThat(anchorIsActive(html, "About")).isTrue();
        // NB: the link is labelled "Our Team", not "Team".
        assertThat(anchorIsActive(html, "Our Team")).isFalse();
    }

    @Test
    @WithAnonymousUser
    void anonymousPrincipalDoesNotUnlockModules() throws Exception {
        // @WithAnonymousUser installs a real AnonymousAuthenticationToken, which
        // is the production logged-out case the advice must reject.
        String html = aboutPage();

        assertThat(html).doesNotContain("href=\"/dashboard\"");
        assertThat(html).doesNotContain("href=\"/superadmin\"");
        // The whole super-admin dropdown wrapper is absent too, not just the
        // link. This replaced a doesNotContain("nav-submenu") assertion, which
        // was only a proxy for the same intent and is no longer valid because the
        // PUBLIC About dropdown is a .nav-submenu too.
        assertThat(html).doesNotContain("nav-superadmin");
        assertThat(html).doesNotContain("logout-button");
    }

    @Test
    @WithMockUser
    void nonSstsUserPrincipalIsLoggedInButNotSuperAdmin() throws Exception {
        // A principal that is not an SstsUser cannot be role-checked, so the
        // account links show but nothing privileged does.
        String html = aboutPage();

        assertThat(html).contains("href=\"/dashboard\"");
        assertThat(html).contains("logout-button");
        assertThat(html).doesNotContain("href=\"/superadmin\"");
        // The whole dropdown wrapper is absent too, not just the link.
        assertThat(html).doesNotContain("nav-superadmin");
    }

    @Test void moduleScreenLinksToEveryOtherModule() throws Exception {
        authenticate("super_admin", "admin");
        String html = teamListPage();

        // The drift that motivated all of this: /superadmin/team used to offer
        // only Calendar Events. Every module must be reachable from any module
        // page. The count is the Overview link plus the nine modules below;
        // adding a module without adding it here is the drift this guards.
        assertThat(html).contains("href=\"/superadmin\"");
        assertThat(html).contains("href=\"/superadmin/calendar\"");
        assertThat(html).contains("href=\"/superadmin/team\"");
        assertThat(html).contains("href=\"/superadmin/gallery\"");
        assertThat(html).contains("href=\"/superadmin/announcements\"");
        assertThat(html).contains("href=\"/superadmin/users\"");
        assertThat(html).contains("href=\"/superadmin/roles\"");
        assertThat(html).contains("href=\"/superadmin/donors\"");
        assertThat(html).contains("href=\"/superadmin/donations\"");
        assertThat(html).contains("href=\"/superadmin/reports\"");
        // The public pages are still reachable from inside a module page.
        assertThat(html).contains("href=\"/about\"");
        assertThat(html).contains("href=\"/calendar\"");
        assertThat(html).contains("href=\"/gallery\"");
        assertThat(html).contains("href=\"/team\"");
        assertThat(html).contains("href=\"/contact\"");
    }

    @Test void moduleScreenHighlightsItsOwnModuleOnly() throws Exception {
        authenticate("super_admin", "admin");
        String html = teamListPage();

        // Module links match on a path PREFIX, so /superadmin/team lights up
        // "Team Members" -- and must NOT also light up "/superadmin" itself,
        // which is a prefix of every module URL.
        assertThat(anchorIsActive(html, "Team Members")).isTrue();
        assertThat(anchorIsActive(html, "Super Admin")).isFalse();
        assertThat(anchorIsActive(html, "Calendar Events")).isFalse();
        // The wrapper is marked current on ANY module page, which forces the
        // dropdown visibly open so the active module link stays visible. The
        // parent anchor itself only gets class="active" on the exact /superadmin
        // URL, never on a module page.
        // The marker lands on the WRAPPER's class attribute, as a th:classappend
        // onto the existing "nav-superadmin" -- not on the parent anchor, whose
        // own active semantics are unchanged.
        assertThat(html).contains("class=\"nav-superadmin nav-superadmin--current\"");
        assertThat(html).doesNotContain("class=\"nav-superadmin--current\"");
    }

    private String teamListPage() throws Exception {
        return mvc.perform(get("/superadmin/team"))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/team/list"))
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Finds the {@code <a ...>label</a>} whose visible text is exactly {@code label}
     * and reports whether it carries the active class.
     *
     * <p>Prettier wraps long anchors as {@code </a\n  >}, so the closing tag is
     * normalized before matching -- otherwise this test would pass or fail purely
     * on how the template happens to be line-wrapped.
     */
    private boolean anchorIsActive(String html, String label) {
        Matcher m = anchorMatcher(html, label);
        assertThat(m.find()).as("anchor labelled '%s' is present", label).isTrue();
        return m.group().contains("class=\"active\"");
    }

    /** Document offset of the {@code <a ...>label</a>} whose text is {@code label}. */
    private int anchorIndex(String html, String label) {
        Matcher m = anchorMatcher(html, label);
        assertThat(m.find()).as("anchor labelled '%s' is present", label).isTrue();
        return m.start();
    }

    private Matcher anchorMatcher(String html, String label) {
        String normalized = html.replaceAll("</a\\s*>", "</a>");
        return Pattern.compile("<a[^>]*>\\s*" + Pattern.quote(label) + "\\s*</a>").matcher(normalized);
    }
}
