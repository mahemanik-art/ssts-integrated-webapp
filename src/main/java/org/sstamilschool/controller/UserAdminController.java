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
import org.sstamilschool.service.UserAdminService;

/**
 * Super-admin CRUD for general user administration, under the /superadmin/** URL space.
 * Protected by SecurityConfig's .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
 * and @PreAuthorize here as defense in depth -- the same Stage 1 pattern
 * CalendarAdminController and TeamAdminController use.
 *
 * <p>This is the ONLY screen in the app where a role may be chosen. It manages
 * ALL ssts_users rows (parent, staff, volunteer, admin). Every field that affects
 * login/visibility is editable: username, email, fullName, userType, role, isActive,
 * emailVerified, joiningDate, lastDate, receiveNewsletter, receiveVolunteerUpdates,
 * and an optional password reset on edit.
 *
 * <p>The form object is the bound SstsUser entity, so it is a mass-assignment surface.
 * Two independent layers guard this: @InitBinder denies auth fields, AND
 * UserAdminService.create/update resolve the role from a separate validated roleId
 * parameter rather than honouring the bound role.
 */
@Controller
@RequestMapping("/superadmin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserAdminController {

    /** Matches the minlength on the form's password input. */
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * Defense in depth against mass assignment. The form object is the bound
     * SstsUser entity, so without this a client can post any of these and have
     * it bound even though the form never renders them. UserAdminService.create
     * also ignores the bound role on its own; both layers are intentional.
     *
     * <p>emailVerified is DELIBERATELY allowed here (an admin setting it is the
     * point of this screen), unlike TeamAdminController which denies it.
     */
    @InitBinder("user")
    void denyAuthenticationFields(WebDataBinder binder) {
        binder.setDisallowedFields("role", "role.id", "role.name", "passwordHash", "lastLogin", "createdAt", "updatedAt");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userAdminService.findAll());
        return "superadmin/users/list";
    }

    @GetMapping("/new")
    public String newUser(Model model) {
        model.addAttribute("user", userAdminService.newUser());
        model.addAttribute("roles", userAdminService.findAllRoles());
        return "superadmin/users/form";
    }

    @PostMapping
    public String create(@ModelAttribute("user") SstsUser user,
                         @RequestParam(name = "roleId", required = false) Long roleId,
                         @RequestParam(name = "password", required = false) String password,
                         RedirectAttributes redirect) {
        if (isBlank(user.getUsername()) || isBlank(user.getEmail())
                || isBlank(user.getFullName()) || isBlank(password)) {
            redirect.addFlashAttribute("error",
                    "Username, email, full name and an initial password are all required.");
            return "redirect:/superadmin/users/new";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            redirect.addFlashAttribute("error",
                    "The initial password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
            return "redirect:/superadmin/users/new";
        }
        if (roleId == null) {
            redirect.addFlashAttribute("error", "A role must be selected.");
            return "redirect:/superadmin/users/new";
        }
        try {
            userAdminService.create(user, password, roleId);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/users/new";
        } catch (DataIntegrityViolationException e) {
            // The service's existsByUsername/existsEmail check is a
            // check-then-act, so a concurrent create can still win the race and
            // trip the unique constraint at flush. Translate it to the same
            // friendly message instead of a 500.
            redirect.addFlashAttribute("error",
                    "That username or email was just taken. Please try again.");
            return "redirect:/superadmin/users/new";
        }
        redirect.addFlashAttribute("message", "User created.");
        return "redirect:/superadmin/users";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        SstsUser user = userAdminService.findById(id);
        if (user == null) {
            return "redirect:/superadmin/users";
        }
        model.addAttribute("user", user);
        model.addAttribute("roles", userAdminService.findAllRoles());
        return "superadmin/users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("user") SstsUser form,
                         @RequestParam(name = "roleId", required = false) Long roleId,
                         @RequestParam(name = "newPassword", required = false) String newPassword,
                         RedirectAttributes redirect) {
        if (isBlank(form.getFullName())) {
            redirect.addFlashAttribute("error", "Full name is required.");
            return "redirect:/superadmin/users/" + id + "/edit";
        }
        if (roleId == null) {
            redirect.addFlashAttribute("error", "A role must be selected.");
            return "redirect:/superadmin/users/" + id + "/edit";
        }
        try {
            SstsUser updated = userAdminService.update(id, form, roleId, newPassword, currentUserId());
            if (updated == null) {
                return "redirect:/superadmin/users";
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/users/" + id + "/edit";
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error",
                    "That username or email was just taken. Please try again.");
            return "redirect:/superadmin/users/" + id + "/edit";
        }
        redirect.addFlashAttribute("message", "User updated.");
        return "redirect:/superadmin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            boolean deleted = userAdminService.delete(id, currentUserId());
            redirect.addFlashAttribute(
                    deleted ? "message" : "error",
                    deleted ? "User deleted."
                            : "User not found, or you cannot delete your own account.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin/users";
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
