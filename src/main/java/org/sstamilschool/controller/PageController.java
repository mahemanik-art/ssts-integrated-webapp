package org.sstamilschool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.service.TeamService;

import java.util.Map;

@Controller
public class PageController {
    private final EmailService emailService;
    private final TeamService teamService;
    private final CalendarService calendarService;

    public PageController(EmailService emailService, TeamService teamService,
                          CalendarService calendarService) {
        this.emailService = emailService;
        this.teamService = teamService;
        this.calendarService = calendarService;
    }

    private static final Map<String, Page> PAGES = Map.of(
            "team", new Page("Our team", "A community made possible by volunteers.", "Teachers, coordinators, and families work together to make every Friday evening meaningful for our learners."),
            "calendar", new Page("School calendar", "Learn, celebrate, and grow together.", "Classes are held Fridays from 7:00–8:15 PM. School events and family celebrations are shared with enrolled families."),
            "contact", new Page("Contact us", "We would love to hear from you.", "Visit us at 4896 N Peachtree Rd, Dunwoody, GA 30338 during school hours, or use the school’s existing contact channels for enrollment questions.")
    );

    @GetMapping("/")
    public String home() { return "index"; }

    @GetMapping("/about")
    public String about() { return "about"; }

    @GetMapping("/contact")
    public String contact(@RequestParam(required = false) String sent,
                          @RequestParam(required = false) String error,
                          Model model) {
        model.addAttribute("page", PAGES.get("contact"));
        model.addAttribute("sent", sent != null);
        model.addAttribute("error", error != null);
        return "contact";
    }

    @PostMapping("/contact")
    public String contactSubmit(@RequestParam String name,
                                @RequestParam String email,
                                @RequestParam(required = false) String subject,
                                @RequestParam String message) {
        try {
            emailService.sendContactMessage(name, email, subject, message);
        } catch (Exception e) {
            return "redirect:/contact?error=true";
        }
        return "redirect:/contact?sent=true";
    }

    @GetMapping("/calendar")
    public String calendar(Model model) {
        model.addAttribute("page", PAGES.get("calendar"));
        model.addAttribute("calendar", calendarService.getCurrentCalendar());
        return "calendar";
    }

    @GetMapping("/team")
    public String team(Model model) {
        model.addAttribute("page", PAGES.get("team"));
        model.addAttribute("teamMembers", teamService.getPublicTeamMembers());
        return "team";
    }

    record Page(String title, String intro, String body) { }
}
