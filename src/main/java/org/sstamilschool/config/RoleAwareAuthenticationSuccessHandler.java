package org.sstamilschool.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class RoleAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";
    private static final String SUPER_ADMIN_DEFAULT_URL = "/superadmin";
    private static final String DEFAULT_URL = "/dashboard";
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        // First, check for a saved request (same precedence as parent class)
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            return savedRequest.getRedirectUrl();
        }
        // No saved request: choose default based on role
        return isSuperAdmin(authentication) ? SUPER_ADMIN_DEFAULT_URL : DEFAULT_URL;
    }

    private boolean isSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> SUPER_ADMIN_AUTHORITY.equals(a.getAuthority()));
    }
}
