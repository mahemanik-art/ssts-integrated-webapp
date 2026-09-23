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

        return switch (user.getUserType().toLowerCase()) {
            case "admin", "staff" -> "admin/dashboard";
            case "volunteer" -> "volunteer/dashboard";
            default -> "parent/dashboard";
        };
    }
}