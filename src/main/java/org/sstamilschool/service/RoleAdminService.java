package org.sstamilschool.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.model.SstsRole;
import org.sstamilschool.repository.SstsRoleRepository;

/**
 * CRUD for the ssts_roles table under the super-admin module (/superadmin/roles).
 *
 * <p>Each role row defines a set of 21 boolean privilege flags (canCreateUsers,
 * canEditUsers, ..., canViewDashboard) and an isActive flag. The role name is
 * load-bearing for authorization: SstsUser.getAuthorities() returns
 * "ROLE_" + role.getName().toUpperCase(), so the name directly determines the
 * Spring Security authority string.
 *
 * <p>Business rules enforced by this service:
 * <ul>
 *   <li>Role name must match {@code ^[a-z][a-z0-9_]*$}, max 50 chars (lowercase,
 *       digits, underscore only; no spaces, dots, dashes, uppercase, or leading
 *       digits). Rejected names throw {@link IllegalArgumentException}.</li>
 *   <li>Name must not already exist (case-sensitive check via
 *       {@link SstsRoleRepository#existsByName(String)}).</li>
 *   <li>Name must not collide case-insensitively with an existing role (via
 *       {@link SstsRoleRepository#countByNameIgnoreCase(String)}), because
 *       {@code admin} and {@code ADMIN} both produce the authority
 *       {@code ROLE_ADMIN}.</li>
 *   <li>The {@code super_admin} role is immutable: {@link #update(Long, SstsRole)}
 *       refuses to change its isActive or any flag; {@link #delete(Long)} refuses
 *       it entirely.</li>
 *   <li>Description max 255 chars.</li>
 *   <li>{@link #create(SstsRole, String)} builds a NEW SstsRole and copies only
 *       allowed fields. {@link #update(Long, SstsRole)} copies only description,
 *       the 21 flags and isActive — never the name.</li>
 * </ul>
 *
 * <p>No caching annotations: role data is small and changes rarely, and there is
 * no out-of-band SQL edit path that would require a manual eviction endpoint.
 */
@Service
public class RoleAdminService {

    /** The role name that backs the ROLE_SUPER_ADMIN authority. Immutable. */
    public static final String PROTECTED_ROLE = "super_admin";

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final SstsRoleRepository roleRepository;

    public RoleAdminService(SstsRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<SstsRole> findAll() {
        return roleRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public SstsRole findById(Long id) {
        return roleRepository.findById(id).orElse(null);
    }

    /**
     * Returns a new role with sensible defaults for the admin form:
     * isActive = true, all 21 privilege flags = false, name = null.
     */
    public SstsRole newRole() {
        SstsRole role = new SstsRole();
        role.setActive(true);
        // All 21 can* flags default to false in the entity constructor.
        return role;
    }

    @Transactional
    public SstsRole create(SstsRole form, String name) {
        validateName(name, true);
        validateDescription(form.getDescription());

        SstsRole role = new SstsRole();
        role.setName(name);
        copyAllowedFields(role, form);
        role.setActive(true); // New roles are active by default
        return roleRepository.save(role);
    }

    @Transactional
    public SstsRole update(Long id, SstsRole form) {
        Optional<SstsRole> existingOpt = roleRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return null;
        }
        SstsRole existing = existingOpt.get();

        if (PROTECTED_ROLE.equals(existing.getName())) {
            throw new IllegalArgumentException("The super_admin role is immutable and cannot be modified.");
        }

        validateDescription(form.getDescription());

        // Name is never updated; copy description, isActive, and all 21 flags
        existing.setDescription(form.getDescription());
        existing.setActive(form.isActive());
        copyAllFlags(existing, form);

        return roleRepository.save(existing);
    }

    @Transactional
    public boolean delete(Long id) {
        Optional<SstsRole> existingOpt = roleRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return false;
        }
        SstsRole existing = existingOpt.get();

        if (PROTECTED_ROLE.equals(existing.getName())) {
            throw new IllegalArgumentException("The super_admin role cannot be deleted.");
        }

        roleRepository.delete(existing);
        return true;
    }

    /**
     * Counts how many of the 21 can* privilege flags are true on the given role.
     */
    public int enabledPrivilegeCount(SstsRole role) {
        int count = 0;
        if (role.isCanCreateUsers()) count++;
        if (role.isCanEditUsers()) count++;
        if (role.isCanDeleteUsers()) count++;
        if (role.isCanViewUsers()) count++;
        if (role.isCanManageContent()) count++;
        if (role.isCanEditPages()) count++;
        if (role.isCanPublishContent()) count++;
        if (role.isCanManageLevels()) count++;
        if (role.isCanManageClasses()) count++;
        if (role.isCanManageDonors()) count++;
        if (role.isCanViewDonors()) count++;
        if (role.isCanManageEvents()) count++;
        if (role.isCanViewCalendar()) count++;
        if (role.isCanManageVolunteers()) count++;
        if (role.isCanViewFinancials()) count++;
        if (role.isCanViewReports()) count++;
        if (role.isCanManageSettings()) count++;
        if (role.isCanViewAuditLogs()) count++;
        if (role.isCanSendAnnouncements()) count++;
        if (role.isCanSendNewsletters()) count++;
        if (role.isCanViewDashboard()) count++;
        return count;
    }

    // ----- Validation helpers -----

    private void validateName(String name, boolean checkUniqueness) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name is required.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Role name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Role name must contain only lowercase letters, digits, and underscores; must start with a letter; no spaces, dots, dashes, or uppercase."
            );
        }
        if (checkUniqueness) {
            if (roleRepository.existsByName(name)) {
                throw new IllegalArgumentException("A role with that name already exists.");
            }
            if (roleRepository.countByNameIgnoreCase(name) > 0) {
                throw new IllegalArgumentException("A role with that name already exists (case-insensitive collision).");
            }
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
    }

    // Copies description and all 21 flags from form to target (used by create)
    private void copyAllowedFields(SstsRole target, SstsRole form) {
        target.setDescription(form.getDescription());
        copyAllFlags(target, form);
    }

    // Copies all 21 boolean flags with explicit setter calls.
    // This list must track the entity: if a new can* field is added to SstsRole,
    // add a corresponding setter call here.
    private void copyAllFlags(SstsRole target, SstsRole form) {
        target.setCanCreateUsers(form.isCanCreateUsers());
        target.setCanEditUsers(form.isCanEditUsers());
        target.setCanDeleteUsers(form.isCanDeleteUsers());
        target.setCanViewUsers(form.isCanViewUsers());
        target.setCanManageContent(form.isCanManageContent());
        target.setCanEditPages(form.isCanEditPages());
        target.setCanPublishContent(form.isCanPublishContent());
        target.setCanManageLevels(form.isCanManageLevels());
        target.setCanManageClasses(form.isCanManageClasses());
        target.setCanManageDonors(form.isCanManageDonors());
        target.setCanViewDonors(form.isCanViewDonors());
        target.setCanManageEvents(form.isCanManageEvents());
        target.setCanViewCalendar(form.isCanViewCalendar());
        target.setCanManageVolunteers(form.isCanManageVolunteers());
        target.setCanViewFinancials(form.isCanViewFinancials());
        target.setCanViewReports(form.isCanViewReports());
        target.setCanManageSettings(form.isCanManageSettings());
        target.setCanViewAuditLogs(form.isCanViewAuditLogs());
        target.setCanSendAnnouncements(form.isCanSendAnnouncements());
        target.setCanSendNewsletters(form.isCanSendNewsletters());
        target.setCanViewDashboard(form.isCanViewDashboard());
    }
}
