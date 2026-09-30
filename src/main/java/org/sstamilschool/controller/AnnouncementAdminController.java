package org.sstamilschool.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.sstamilschool.model.SstsAnnouncement;
import org.sstamilschool.service.AnnouncementAdminService;
import org.sstamilschool.util.SchoolTime;


/**
 * Super-admin CRUD for the home-page announcement marquee, under the
 * /superadmin/** URL space. Protected by SecurityConfig's
 * .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN") and @PreAuthorize
 * here as defense in depth -- the same Stage 1 pattern the calendar, team and
 * gallery modules use.
 *
 * <p>URL is plural (/superadmin/announcements) because this module manages a
 * collection; the other three use singular mass nouns.
 */
@Controller
@RequestMapping("/superadmin/announcements")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AnnouncementAdminController {

    private final AnnouncementAdminService announcementAdminService;

    public AnnouncementAdminController(AnnouncementAdminService announcementAdminService) {
        this.announcementAdminService = announcementAdminService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("announcements", announcementAdminService.findAll());
        // Thymeleaf's #temporals has format()/formatISO() but no today(), so the
        // "Showing" column needs the current date supplied by the controller.
        // SchoolTime so the admin sees the same "today" the public feed uses.
        model.addAttribute("today", SchoolTime.today());
        return "superadmin/announcements/list";
    }

    @GetMapping("/new")
    public String newAnnouncement(Model model) {
        model.addAttribute("announcement", announcementAdminService.newAnnouncement());
        return "superadmin/announcements/form";
    }

    @PostMapping
    public String create(@ModelAttribute("announcement") SstsAnnouncement announcement,
                         RedirectAttributes redirect) {
        if (isBlank(announcement.getTitle()) || isBlank(announcement.getMessage())
                || announcement.getAnnounceDate() == null) {
            redirect.addFlashAttribute("error", "Title, message and announce date are required.");
            return "redirect:/superadmin/announcements/new";
        }
        announcementAdminService.create(announcement);
        redirect.addFlashAttribute("message", "Announcement created.");
        return "redirect:/superadmin/announcements";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        SstsAnnouncement announcement = announcementAdminService.findById(id);
        if (announcement == null) {
            return "redirect:/superadmin/announcements";
        }
        model.addAttribute("announcement", announcement);
        return "superadmin/announcements/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("announcement") SstsAnnouncement form,
                         RedirectAttributes redirect) {
        if (isBlank(form.getTitle()) || isBlank(form.getMessage())
                || form.getAnnounceDate() == null) {
            redirect.addFlashAttribute("error", "Title, message and announce date are required.");
            return "redirect:/superadmin/announcements/" + id + "/edit";
        }
        SstsAnnouncement updated = announcementAdminService.update(id, form);
        if (updated == null) {
            return "redirect:/superadmin/announcements";
        }
        redirect.addFlashAttribute("message", "Announcement updated.");
        return "redirect:/superadmin/announcements";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        boolean deleted = announcementAdminService.delete(id);
        redirect.addFlashAttribute(
                deleted ? "message" : "error",
                deleted ? "Announcement deleted." : "Announcement not found.");
        return "redirect:/superadmin/announcements";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
