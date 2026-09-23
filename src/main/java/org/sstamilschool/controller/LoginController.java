package org.sstamilschool.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.sstamilschool.service.LoginService;
import org.sstamilschool.model.SstsUser;

@Controller
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/login")
    public String showLogin(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam String username,
                               @RequestParam String password,
                               Model model) {
        try {
            if (loginService.authenticate(username, password)) {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                var user = (SstsUser) auth.getPrincipal();
                return loginService.redirectBasedOnUserType(user.getId());
            }
            model.addAttribute("error", "Invalid username or password");
            return "login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }
}