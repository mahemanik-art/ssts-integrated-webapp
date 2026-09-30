package org.sstamilschool.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.sstamilschool.model.SstsRole;
import org.sstamilschool.service.RoleAdminService;

/**
 * Super-admin CRUD for the ssts_roles table, under the /superadmin/** URL
 * space. Protected by SecurityConfig's
 * .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN") and @PreAuthorize
 * here as defense in depth -- the same pattern the calendar, team, gallery and
 * announcement modules use.
 *
 * <p>The role name is load-bearing for authorization: SstsUser.getAuthorities()
 * returns "ROLE_" + role.getName().toUpperCase(), so a name directly determines
 * the Spring Security authority string. That is why the name is immutable
 * after creation -- renaming a role would silently detach every user who held
 * it -- and why the create handler reads it from a separate @RequestParam
 * rather than from the bound entity.
 *
 * <p>The {@code super_admin} role is immutable: RoleAdminService.update refuses
 * to change its isActive or any flag, and delete refuses it entirely. Those
 * refusals surface here as flash errors rather than 500s.
 */
@Controller
@RequestMapping("/superadmin/roles")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    /**
     * Mass-assignment deny-list. The form object is the bound SstsRole entity,
     * so without this a client can post any of these and have it bound even
     * though the form never renders them. RoleAdminService.create also builds a
     * fresh entity and copies only allowed fields, so both layers are
     * intentional.
     *
     * <p>{@code name} is denied because a role name is immutable after
     * creation, which is why the create handler reads it from a separate
     * @RequestParam instead of the bound entity. id/createdAt/updatedAt are
     * denied because they are server-managed.
     */
    @InitBinder("role")
    void denyImmutableFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "name", "createdAt", "updatedAt");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("roles", roleAdminService.findAll());
        return "superadmin/roles/list";
    }

    @GetMapping("/new")
    public String newRole(Model model) {
        model.addAttribute("role", roleAdminService.newRole());
        return "superadmin/roles/form";
    }

    @PostMapping
    public String create(@ModelAttribute("role") SstsRole role,
                         @RequestParam(name = "name", required = false) String name,
                         RedirectAttributes redirect) {
        if (isBlank(name)) {
            redirect.addFlashAttribute("error", "Role name is required.");
            return "redirect:/superadmin/roles/new";
        }
        try {
            roleAdminService.create(role, name);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/roles/new";
        } catch (DataIntegrityViolationException e) {
            // The service's existsByName/countByNameIgnoreCase checks are
            // check-then-act, so a concurrent create can still win the race and
            // trip the unique constraint at flush. Translate it to the same
            // friendly message instead of a 500.
            redirect.addFlashAttribute("error",
                    "That role name was just taken. Please try again.");
            return "redirect:/superadmin/roles/new";
        }
        redirect.addFlashAttribute("message", "Role created.");
        return "redirect:/superadmin/roles";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        SstsRole role = roleAdminService.findById(id);
        if (role == null) {
            return "redirect:/superadmin/roles";
        }
        model.addAttribute("role", role);
        return "superadmin/roles/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("role") SstsRole form,
                         RedirectAttributes redirect) {
        try {
            SstsRole updated = roleAdminService.update(id, form);
            if (updated == null) {
                return "redirect:/superadmin/roles";
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/roles/" + id + "/edit";
        }
        redirect.addFlashAttribute("message", "Role updated.");
        return "redirect:/superadmin/roles";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            boolean deleted = roleAdminService.delete(id);
            redirect.addFlashAttribute(
                    deleted ? "message" : "error",
                    deleted ? "Role deleted." : "Role not found.");
        } catch (IllegalArgumentException e) {
            // A refusal on the protected super_admin role surfaces as a flash
            // error rather than a 500.
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // ssts_users.role_id has NO ON DELETE CASCADE, so this is
            // specifically the foreign key from a user still assigned to the
            // role. It is deliberately left to the database constraint instead
            // of a pre-count, because the constraint is race-free: a
            // check-then-act count cannot see a concurrent reassignment.
            redirect.addFlashAttribute("error",
                    "That role is still assigned to one or more users. Reassign them first.");
        }
        return "redirect:/superadmin/roles";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
