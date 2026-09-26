package org.sstamilschool.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.sstamilschool.dto.RegisterRequest;
import org.sstamilschool.service.LoginService;
import org.sstamilschool.service.PasswordResetService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.model.SstsUser;

@Controller
public class LoginController {

    private final LoginService loginService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;

    public LoginController(LoginService loginService, PasswordResetService passwordResetService,
                           EmailService emailService) {
        this.loginService = loginService;
        this.passwordResetService = passwordResetService;
        this.emailService = emailService;
    }

    @GetMapping("/login")
    public String showLogin(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String registered,
                            @RequestParam(required = false) String reset, Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        if (registered != null) {
            model.addAttribute("message", "Your account was created. You can now log in.");
        }
        if (reset != null) {
            model.addAttribute("message", "Your password has been reset. You can now log in.");
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

    @GetMapping("/register")
    public String showRegister(Model model) {
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@ModelAttribute RegisterRequest request,
                                  Model model) {
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }
        try {
            loginService.register(request);
            return "redirect:/login?registered";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/forgot-password")
    public String showForgotPassword(@RequestParam(required = false) String sent, Model model) {
        if (sent != null) {
            model.addAttribute("message", "If that account exists, a reset link has been emailed to you.");
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email) {
        passwordResetService.createTokenForEmail(email)
            .ifPresent(token -> emailService.sendPasswordResetLink(email, token));
        // Always redirect to the same "sent" page to avoid user enumeration.
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, Model model) {
        if (passwordResetService.findValidToken(token).isEmpty()) {
            model.addAttribute("error", "That password reset link is invalid, expired, or has already been used.");
            return "reset-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String password,
                                       @RequestParam String confirmPassword,
                                       Model model) {
        if (passwordResetService.findValidToken(token).isEmpty()) {
            model.addAttribute("error", "That password reset link is invalid, expired, or has already been used.");
            return "reset-password";
        }
        if (password == null || !password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match.");
            return "reset-password";
        }
        if (passwordResetService.consumeToken(token, password)) {
            return "redirect:/login?reset";
        }
        model.addAttribute("token", token);
        model.addAttribute("error", "Could not reset your password. Please try again.");
        return "reset-password";
    }
}