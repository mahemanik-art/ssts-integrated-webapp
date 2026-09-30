package org.sstamilschool.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.sstamilschool.dto.ReportsView;
import org.sstamilschool.service.ReportsService;
import org.sstamilschool.util.SchoolTime;

import java.util.List;

/**
 * Super-admin Reports module -- read-only aggregate statistics.
 * Protected by SecurityConfig's .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
 * and @PreAuthorize here as defense in depth.
 *
 * <p>This module performs NO writes and has NO forms. It only displays
 * aggregate statistics computed live from the database.
 */
@Controller
@RequestMapping("/superadmin/reports")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ReportsAdminController {

    private final ReportsService reportsService;

    public ReportsAdminController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping
    public String reports(Model model) {
        ReportsView view = reportsService.build();
        model.addAttribute("view", view);
        // Thymeleaf's #temporals has no today(); supply the current date for formatting.
        model.addAttribute("today", SchoolTime.today());
        // Fixed user types for the "Accounts by User Type" table.
        model.addAttribute("userTypes", List.of("parent", "staff", "volunteer", "admin"));
        return "superadmin/reports/index";
    }
}
