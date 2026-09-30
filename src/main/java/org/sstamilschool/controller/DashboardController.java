package org.sstamilschool.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.sstamilschool.model.SstsUser;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        var user = (SstsUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        model.addAttribute("user", user);
        model.addAttribute("userType", user.getUserType());
        // isSuperAdmin is NOT set here: CommonModelAdvice supplies it (along with
        // currentUser / isAuthenticated / currentPath) to every view, which is what
        // lets the shared nav fragment render on non-dashboard pages too. It is
        // derived from the ROLE (the existing ssts_roles row named 'super_admin'),
        // never from userType -- userType is routing-only, per AGENTS.md.

        return switch (user.getUserType().toLowerCase()) {
            case "admin", "staff" -> "admin/dashboard";
            case "volunteer" -> "volunteer/dashboard";
            default -> "parent/dashboard";
        };
    }
}