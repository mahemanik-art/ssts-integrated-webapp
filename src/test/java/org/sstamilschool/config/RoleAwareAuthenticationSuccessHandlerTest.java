package org.sstamilschool.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAwareAuthenticationSuccessHandlerTest {

    private RoleAwareAuthenticationSuccessHandler handler;
    private RequestCache requestCache;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        handler = new RoleAwareAuthenticationSuccessHandler();
        requestCache = new HttpSessionRequestCache();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);
    }

    @Test
    void superAdminNoSavedRequestRedirectsToSuperadmin() throws Exception {
        Authentication auth = createAuthWithAuthority("ROLE_SUPER_ADMIN");

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/superadmin");
    }

    @Test
    void nonSuperAdminNoSavedRequestRedirectsToDashboard() throws Exception {
        Authentication auth = createAuthWithAuthority("ROLE_ADMIN");

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    void superAdminWithSavedRequestRedirectsToSavedUrl() throws Exception {
        Authentication auth = createAuthWithAuthority("ROLE_SUPER_ADMIN");

        // Set up a saved request in the cache by simulating a request to a protected URL
        request.setRequestURI("/superadmin/calendar");
        request.setMethod("GET");
        requestCache.saveRequest(request, response);

        // Verify the saved request is in the cache
        assertThat(requestCache.getRequest(request, response)).isNotNull();

        handler.onAuthenticationSuccess(request, response, auth);

        // Should redirect to the saved request URL, not the default /superadmin
        // The saved request returns a full URL with query params; assert it contains the expected path
        assertThat(response.getRedirectedUrl()).contains("/superadmin/calendar");
        // And crucially, it should NOT be just the default /superadmin
        assertThat(response.getRedirectedUrl()).isNotEqualTo("/superadmin");
    }

    @Test
    void anonymousPrincipalRedirectsToDashboard() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymous", "credentials", List.of());

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    void emptyAuthoritiesRedirectsToDashboard() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user", "credentials", List.of());

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
    }

    @Test
    void principalNotSstsUserButWithSuperAdminAuthorityRedirectsToSuperadmin() throws Exception {
        // Test that the handler works with any principal, not just SstsUser
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "some-principal", "credentials", List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getRedirectedUrl()).isEqualTo("/superadmin");
    }

    private Authentication createAuthWithAuthority(String authority) {
        SstsRole role = new SstsRole();
        role.setName(authority.replace("ROLE_", "").toLowerCase());
        SstsUser user = new SstsUser();
        user.setId(1L);
        user.setFullName("Test User");
        user.setRole(role);
        return new UsernamePasswordAuthenticationToken(user, "credentials", user.getAuthorities());
    }
}
