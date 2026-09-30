package org.sstamilschool.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.sstamilschool.model.SstsUser;
import org.sstamilschool.service.TeamAdminService;

/**
 * Super-admin CRUD for team/staff members, under the /superadmin/** URL space.
 * Protected by SecurityConfig's .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
 * and @PreAuthorize here as defense in depth -- the same Stage 1 pattern
 * CalendarAdminController uses.
 *
 * <p>The form covers every field the public /team page renders: fullName,
 * designation, bio and avatarUrl, plus the userType/isActive pair that decides
 * whether a member shows up there at all. Every write goes through
 * TeamAdminService, which evicts the TEAM_MEMBERS cache.
 */
@Controller
@RequestMapping("/superadmin/team")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TeamAdminController {

    /** Matches the minlength on the form's password input. */
    static final int MIN_PASSWORD_LENGTH = 12;

    private final TeamAdminService teamAdminService;

    public TeamAdminController(TeamAdminService teamAdminService) {
        this.teamAdminService = teamAdminService;
    }

    /**
     * Defense in depth against mass assignment. The form object is the bound
     * SstsUser entity, so without this a client can post any of these and have
     * it bound even though the form never renders them. TeamAdminService.create
     * also ignores the bound role on its own; both layers are intentional.
     * Username/email are NOT denied -- the create form genuinely sets them.
     */
    @InitBinder("member")
    void denyAuthenticationFields(WebDataBinder binder) {
        binder.setDisallowedFields("role", "role.id", "role.name", "passwordHash", "emailVerified");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("members", teamAdminService.findAll());
        return "superadmin/team/list";
    }

    @GetMapping("/new")
    public String newMember(Model model) {
        model.addAttribute("member", teamAdminService.newMember());
        return "superadmin/team/form";
    }

    @PostMapping
    public String create(@ModelAttribute("member") SstsUser member,
                         @RequestParam(name = "bio", required = false) String bio,
                         @RequestParam(name = "avatarUrl", required = false) String avatarUrl,
                         @RequestParam(name = "password", required = false) String password,
                         RedirectAttributes redirect) {
        if (isBlank(member.getUsername()) || isBlank(member.getEmail())
                || isBlank(member.getFullName()) || isBlank(password)) {
            redirect.addFlashAttribute("error",
                    "Username, email, full name and an initial password are all required.");
            return "redirect:/superadmin/team/new";
        }
        // The form's minlength is client-side only, so enforce it here too --
        // otherwise a direct POST creates a loginable account with a 1-char
        // password.
        if (password.length() < MIN_PASSWORD_LENGTH) {
            redirect.addFlashAttribute("error",
                    "The initial password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            return "redirect:/superadmin/team/new";
        }
        member.setBio(bio);
        member.setAvatarUrl(avatarUrl);
        try {
            teamAdminService.create(member, password);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/team/new";
        } catch (DataIntegrityViolationException e) {
            // The service's existsByUsername/existsEmail check is a
            // check-then-act, so a concurrent create can still win the race and
            // trip the unique constraint at flush. Translate it to the same
            // friendly message instead of a 500.
            redirect.addFlashAttribute("error",
                    "That username or email was just taken. Please try again.");
            return "redirect:/superadmin/team/new";
        }
        redirect.addFlashAttribute("message", "Team member created.");
        return "redirect:/superadmin/team";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        SstsUser member = teamAdminService.findById(id);
        if (member == null) {
            return "redirect:/superadmin/team";
        }
        model.addAttribute("member", member);
        return "superadmin/team/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("member") SstsUser form,
                         @RequestParam(name = "bio", required = false) String bio,
                         @RequestParam(name = "avatarUrl", required = false) String avatarUrl,
                         RedirectAttributes redirect) {
        if (isBlank(form.getFullName())) {
            redirect.addFlashAttribute("error", "Full name is required.");
            return "redirect:/superadmin/team/" + id + "/edit";
        }
        form.setBio(bio);
        form.setAvatarUrl(avatarUrl);
        SstsUser updated = teamAdminService.update(id, form);
        if (updated == null) {
            return "redirect:/superadmin/team";
        }
        redirect.addFlashAttribute("message", "Team member updated.");
        return "redirect:/superadmin/team";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        boolean deleted = teamAdminService.delete(id, currentUserId());
        redirect.addFlashAttribute(
                deleted ? "message" : "error",
                deleted ? "Team member deleted."
                        : "Team member not found, or you cannot delete your own account.");
        return "redirect:/superadmin/team";
    }

    /** The acting principal is the SstsUser entity (see DashboardController). */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SstsUser user)) {
            return null;
        }
        return user.getId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
