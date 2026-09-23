package org.sstamilschool.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@Controller
public class PageController {
    private static final Map<String, Page> PAGES = Map.of(
            "about", new Page("About us", "Nurturing Tamil language, culture, and community in the greater Atlanta area.", "Our volunteer-led school creates a welcoming place for children to build confidence in Tamil and stay connected to a living classical tradition."),
            "team", new Page("Our team", "A community made possible by volunteers.", "Teachers, coordinators, and families work together to make every Friday evening meaningful for our learners."),
            "calendar", new Page("School calendar", "Learn, celebrate, and grow together.", "Classes are held Fridays from 7:00–8:15 PM. School events and family celebrations are shared with enrolled families."),
            "contact", new Page("Contact us", "We would love to hear from you.", "Visit us at 1978 Mt. Vernon Rd, Dunwoody, GA 30338 during school hours, or use the school’s existing contact channels for enrollment questions.")
    );

    @GetMapping("/")
    public String home() { return "index"; }

    @GetMapping("/{page:about|team|calendar|contact}")
    public String page(@PathVariable String page, Model model) {
        model.addAttribute("page", PAGES.get(page));
        return "page";
    }

    record Page(String title, String intro, String body) { }
}
