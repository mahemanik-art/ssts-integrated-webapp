package org.sstamilschool.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import org.sstamilschool.model.SstsUser;

/**
 * Supplies the model attributes the shared navigation fragment
 * (templates/fragments/nav.html) needs, to EVERY controller that renders a view.
 *
 * <p>Before this existed only DashboardController set {@code isSuperAdmin}, so the
 * 21 other templates that show the nav had no way to render the Dashboard / Super
 * Admin / Logout links -- which is how the nav drifted apart from template to
 * template. The attributes live here instead of being defaulted inside the
 * fragment on purpose: a missing value then fails loudly rather than silently
 * rendering a logged-out nav to a logged-in super-admin.
 *
 * <p>{@code isSuperAdmin} is derived from the ROLE (the existing ssts_roles row
 * named 'super_admin', via SstsUser.getAuthorities()), never from userType --
 * userType is routing-only, per AGENTS.md "Roles & auth".
 */
@ControllerAdvice
public class CommonModelAdvice {

    /**
     * True whenever the request carries a real authentication. This is broader
     * than the principal being a resolvable {@link SstsUser}: a non-SstsUser
     * principal (e.g. a {@code @WithMockUser} test) is still "logged in" and
     * should see the Dashboard and Logout links, just not the role-gated module
     * links, which {@link #isSuperAdmin()} cannot resolve.
     */
    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("isSuperAdmin")
    public boolean isSuperAdmin() {
        SstsUser user = sstsUserPrincipal();
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Request path with the context path stripped, so the fragment can mark the
     * current page's link active without any servlet API in the template.
     */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private SstsUser sstsUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getPrincipal() instanceof SstsUser user ? user : null;
    }
}
