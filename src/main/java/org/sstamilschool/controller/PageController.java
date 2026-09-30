package org.sstamilschool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.sstamilschool.service.AnnouncementService;
import org.sstamilschool.dto.DonorCard;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.service.DonorService;
import java.util.List;
import java.util.Map;
import org.sstamilschool.service.CalendarService;
import org.sstamilschool.service.EmailService;
import org.sstamilschool.service.GalleryService;
import org.sstamilschool.service.TeamService;

import java.util.Map;

@Controller
public class PageController {
    private final EmailService emailService;
    private final TeamService teamService;
    private final CalendarService calendarService;
    private final GalleryService galleryService;
    private final AnnouncementService announcementService;
    private final DonorService donorService;

    public PageController(EmailService emailService, TeamService teamService,
                          CalendarService calendarService, GalleryService galleryService,
                          AnnouncementService announcementService, DonorService donorService) {
        this.emailService = emailService;
        this.teamService = teamService;
        this.calendarService = calendarService;
        this.galleryService = galleryService;
        this.announcementService = announcementService;
        this.donorService = donorService;
    }

    /** How many donor cards the public panel shows at once. */
    private static final int DONOR_WINDOW = 3;

    private static final Map<String, Page> PAGES = Map.of(
            "team", new Page("Our team", "A community made possible by volunteers.", "Teachers, coordinators, and families work together to make every Friday evening meaningful for our learners."),
            "calendar", new Page("School calendar", "Learn, celebrate, and grow together.", "Classes are held Fridays from 7:00–8:15 PM. School events and family celebrations are shared with enrolled families."),
            "contact", new Page("Contact us", "We would love to hear from you.", "Visit us at 4896 N Peachtree Rd, Dunwoody, GA 30338 during school hours, or use the school’s existing contact channels for enrollment questions.")
    );

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("announcements", announcementService.getVisibleAnnouncements());
        // Real donor rows, not hardcoded markup. The query is already restricted
        // to active + publicly listed, which is the consent gate.
        List<SstsDonor> donors = donorService.findPubliclyListed();
        model.addAttribute("donors", donors);

        // The panel shows a fixed three-card window and rotates through the rest
        // client-side, so the narrow column beside the hero slider keeps its
        // original height no matter how many donors exist. The window is cut
        // here rather than in the template because Thymeleaf has no portable
        // "take first N" utility, and these three are also the no-JS fallback.
        model.addAttribute("donorWindow", donors.subList(0, Math.min(DONOR_WINDOW, donors.size())));
        model.addAttribute("donorWindowSize", DONOR_WINDOW);
        // Every publicly listed donor as data, so the carousel rotates through
        // the real rows. Only public-safe fields go across; see DonorCard.
        model.addAttribute("donorCards",
                donors.stream().map(d -> new DonorCard(
                        d.getName(), d.getTagline(), d.getPhotoPath(), d.getWebsiteUrl())).toList());
        return "index";
    }

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

    /**
     * Public Event Gallery. Entries live in ssts_gallery_events and are managed
     * from /superadmin/gallery; GalleryService serves only the active ones and
     * caches them for ~24h.
     */
    @GetMapping("/gallery")
    public String gallery(Model model) {
        model.addAttribute("events", galleryService.getPublicEvents());
        return "gallery";
    }
}
