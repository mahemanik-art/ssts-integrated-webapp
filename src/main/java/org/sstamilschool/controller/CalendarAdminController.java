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

import org.sstamilschool.model.SstsCalendarEvent;
import org.sstamilschool.service.CalendarAdminService;
import org.sstamilschool.util.AcademicYear;

/**
 * Super-admin CRUD for calendar events, under the /superadmin/** URL space.
 * Protected by SecurityConfig's .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
 * and @PreAuthorize here as defense in depth (Stage 1 pattern, reused).
 * Every write goes through CalendarAdminService, which evicts the
 * CALENDAR_EVENTS cache so /calendar shows changes immediately.
 */
@Controller
@RequestMapping("/superadmin/calendar")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CalendarAdminController {

    private final CalendarAdminService calendarAdminService;

    public CalendarAdminController(CalendarAdminService calendarAdminService) {
        this.calendarAdminService = calendarAdminService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("events", calendarAdminService.findAll());
        model.addAttribute("currentAcademicYear", AcademicYear.current());
        return "superadmin/calendar/list";
    }

    @GetMapping("/new")
    public String newEvent(Model model) {
        model.addAttribute("event", calendarAdminService.newEvent());
        model.addAttribute("currentAcademicYear", AcademicYear.current());
        return "superadmin/calendar/form";
    }

    @PostMapping
    public String create(@ModelAttribute("event") SstsCalendarEvent event,
                         RedirectAttributes redirect) {
        // Spring binds an empty endDate input to null for LocalDate.
        if (event.getTitle() == null || event.getTitle().isBlank()
                || event.getEventDate() == null) {
            redirect.addFlashAttribute("error", "Title and event date are required.");
            return "redirect:/superadmin/calendar/new";
        }
        calendarAdminService.create(event);
        redirect.addFlashAttribute("message", "Event created.");
        return "redirect:/superadmin/calendar";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        SstsCalendarEvent event = calendarAdminService.findById(id);
        if (event == null) {
            return "redirect:/superadmin/calendar";
        }
        model.addAttribute("event", event);
        model.addAttribute("currentAcademicYear", AcademicYear.current());
        return "superadmin/calendar/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("event") SstsCalendarEvent form,
                         RedirectAttributes redirect) {
        if (form.getTitle() == null || form.getTitle().isBlank()
                || form.getEventDate() == null) {
            redirect.addFlashAttribute("error", "Title and event date are required.");
            return "redirect:/superadmin/calendar/" + id + "/edit";
        }
        SstsCalendarEvent updated = calendarAdminService.update(id, form);
        if (updated == null) {
            return "redirect:/superadmin/calendar";
        }
        redirect.addFlashAttribute("message", "Event updated.");
        return "redirect:/superadmin/calendar";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        boolean deleted = calendarAdminService.delete(id);
        redirect.addFlashAttribute(
                deleted ? "message" : "error",
                deleted ? "Event deleted." : "Event not found.");
        return "redirect:/superadmin/calendar";
    }
}
