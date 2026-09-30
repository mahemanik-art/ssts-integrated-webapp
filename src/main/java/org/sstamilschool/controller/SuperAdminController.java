package org.sstamilschool.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.sstamilschool.model.SstsUser;

/**
 * Placeholder super-admin module. URL-protected by SecurityConfig
 * (.requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")) AND
 * method-guarded here as defense in depth. Actual module pages
 * (user management, audit logs, etc.) will live under this prefix.
 */
@Controller
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    @GetMapping("/superadmin")
    public String superAdminHome(Model model) {
        var user = (SstsUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        model.addAttribute("user", user);
        return "superadmin/dashboard";
    }
}
