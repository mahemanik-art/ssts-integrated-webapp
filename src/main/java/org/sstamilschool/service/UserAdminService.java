package org.sstamilschool.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.util.SchoolTime;

/**
 * CRUD for general user administration under the super-admin module.
 *
 * <p>This is the ONLY screen in the app where a role may be chosen. It manages
 * ALL ssts_users rows (parent, staff, volunteer, admin), not just the staff/volunteer
 * subset that /team displays. Every field that affects login/visibility is editable
 * here: username, email, fullName, userType, role, isActive, emailVerified,
 * joiningDate, lastDate, receiveNewsletter, receiveVolunteerUpdates, and an optional
 * password reset on edit. It is also the only screen that edits the per-person
 * columns (bio, avatarUrl, occupation, employer, department, contact details,
 * prior experience, certifications, interests) for anyone who is not on /team.
 *
 * <p>Business rules enforced (matching DB constraints):
 * <ul>
 *   <li>user_type whitelist: only 'parent','staff','volunteer','admin'.</li>
 *   <li>role_id must reference an existing ssts_roles row (validated via roleRepository).</li>
 *   <li>username UNIQUE and email UNIQUE: pre-checked and catch-then-act race translated.</li>
 *   <li>Length limits: username<=50, email<=100, fullName<=100, designation<=100.</li>
 *   <li>Self-protection: refuses to delete, deactivate, or change role of the acting user.</li>
 *   <li>Last-super-admin protection: refuses to delete, deactivate, or demote the final
 *       holder of the super_admin role.</li>
 *   <li>Password minimum 12 chars (enforced in controller; service expects pre-validated).</li>
 * </ul>
 *
 * <p>No caching -- this module is not cached.
 */
@Service
public class UserAdminService {

    /** Default role applied when creating a new user if not otherwise specified. */
    public static final String DEFAULT_ROLE = "volunteer_coordinator";

    private static final String SUPER_ADMIN_ROLE_NAME = "super_admin";
    private static final String[] VALID_USER_TYPES = {"parent", "staff", "volunteer", "admin"};

    private final SstsUserRepository userRepository;
    private final SstsRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(SstsUserRepository userRepository,
                            SstsRoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** All users with role joined, ordered by userType then fullName. */
    @Transactional(readOnly = true)
    public List<SstsUser> findAll() {
        return userRepository.findAllForAdmin();
    }

    /** Loads a user with role joined for the edit form. */
    @Transactional(readOnly = true)
    public SstsUser findById(Long id) {
        return userRepository.findByIdWithRole(id).orElse(null);
    }

    /**
     * New user with sensible defaults for the admin form.
     * userType='parent', isActive=true, emailVerified=false,
     * joiningDate=today (school timezone), role=DEFAULT_ROLE.
     */
    public SstsUser newUser() {
        SstsUser user = new SstsUser();
        user.setUserType("parent");
        user.setActive(true);
        user.setEmailVerified(false);
        user.setJoiningDate(SchoolTime.today());
        user.setRole(roleRepository.findByName(DEFAULT_ROLE).orElse(null));
        return user;
    }

    /**
     * Creates a new user from the form data.
     * The bound SstsUser entity is the form object (mass-assignment surface), so
     * ONLY allowed fields are copied. Role is NEVER taken from the form; it is
     * resolved from the validated roleId parameter. Password is encoded.
     *
     * @param form the bound form entity (only allowed fields populated)
     * @param rawPassword the plaintext password for create (required, >= 12 chars validated in controller)
     * @param roleId the selected role ID (validated against roleRepository)
     * @return the saved user, never null on success
     * @throws IllegalArgumentException if validation fails (duplicate username/email, unknown role, invalid userType, etc.)
     */
    @Transactional
    public SstsUser create(SstsUser form, String rawPassword, Long roleId) {
        validateCreate(form, roleId);

        SstsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Selected role does not exist."));

        SstsUser user = new SstsUser();
        user.setUsername(form.getUsername());
        user.setEmail(form.getEmail());
        user.setFullName(form.getFullName());
        user.setPasswordHash(passwordEncoder.encode(rawPassword == null ? "" : rawPassword));
        user.setUserType(form.getUserType());
        user.setDesignation(form.getDesignation());
        user.setJoiningDate(form.getJoiningDate());
        user.setLastDate(form.getLastDate());
        user.setReceiveNewsletter(form.isReceiveNewsletter());
        user.setReceiveVolunteerUpdates(form.isReceiveVolunteerUpdates());
        user.setActive(form.isActive());
        user.setEmailVerified(form.isEmailVerified());
        user.setRole(role);

        copyPerPersonFields(user, form);
        return userRepository.save(user);
    }

    /**
     * Updates an existing user. Only allowed fields are copied; passwordHash is
     * left untouched unless newPassword is non-blank. Role is resolved from
     * roleId (not taken from the form). Self-protection and last-super-admin
     * protection are enforced for deactivation and role changes.
     *
     * @param id the user ID to update
     * @param form the bound form entity (only allowed fields populated)
     * @param roleId the selected role ID (validated against roleRepository)
     * @param newPassword optional new password (blank = keep current)
     * @param currentUserId the acting user's ID (for self-protection)
     * @return the updated user, or null if not found
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public SstsUser update(Long id, SstsUser form, Long roleId, String newPassword, Long currentUserId) {
        SstsUser existing = userRepository.findByIdWithRole(id).orElse(null);
        if (existing == null) return null;

        // Self-protection: cannot deactivate self, cannot change own role
        if (id.equals(currentUserId)) {
            if (!form.isActive()) {
                throw new IllegalArgumentException("You cannot deactivate your own account.");
            }
            SstsRole currentRole = existing.getRole();
            if (currentRole != null && SUPER_ADMIN_ROLE_NAME.equals(currentRole.getName())) {
                // Check if the new role is different from super_admin
                SstsRole newRole = roleRepository.findById(roleId).orElse(null);
                if (newRole == null || !SUPER_ADMIN_ROLE_NAME.equals(newRole.getName())) {
                    throw new IllegalArgumentException("You cannot change your own role.");
                }
            }
        }

        // Validate userType whitelist
        if (!isValidUserType(form.getUserType())) {
            throw new IllegalArgumentException("Invalid user type: " + form.getUserType());
        }
        validatePerPersonFields(form);

        // Validate role exists
        SstsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Selected role does not exist."));

        // Last-super-admin protection: if demoting the last super_admin
        SstsRole existingRole = existing.getRole();
        if (existingRole != null && SUPER_ADMIN_ROLE_NAME.equals(existingRole.getName())
                && !SUPER_ADMIN_ROLE_NAME.equals(role.getName())) {
            long superAdminCount = userRepository.countByRoleName(SUPER_ADMIN_ROLE_NAME);
            if (superAdminCount <= 1) {
                throw new IllegalArgumentException(
                        "This is the only super admin; promote another account first.");
            }
        }

        // Last-super-admin protection: if deactivating the last super_admin
        if (existingRole != null && SUPER_ADMIN_ROLE_NAME.equals(existingRole.getName())
                && !form.isActive()) {
            long superAdminCount = userRepository.countByRoleName(SUPER_ADMIN_ROLE_NAME);
            if (superAdminCount <= 1) {
                throw new IllegalArgumentException(
                        "This is the only super admin; promote another account first.");
            }
        }

        // Copy allowed fields
        existing.setUsername(form.getUsername());
        existing.setEmail(form.getEmail());
        existing.setFullName(form.getFullName());
        existing.setUserType(form.getUserType());
        existing.setDesignation(form.getDesignation());
        existing.setJoiningDate(form.getJoiningDate());
        existing.setLastDate(form.getLastDate());
        existing.setReceiveNewsletter(form.isReceiveNewsletter());
        existing.setReceiveVolunteerUpdates(form.isReceiveVolunteerUpdates());
        existing.setActive(form.isActive());
        existing.setEmailVerified(form.isEmailVerified());
        existing.setRole(role);

        if (newPassword != null && !newPassword.isBlank()) {
            existing.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        copyPerPersonFields(existing, form);

        return userRepository.save(existing);
    }

    /**
     * Hard-deletes a user. A user row is self-contained -- the per-person
     * columns live on it, so there is no child row to clear first.
     * Refuses to delete the acting user. Refuses to delete the last super_admin.
     *
     * @param id the user ID to delete
     * @param currentUserId the acting user's ID (for self-protection)
     * @return true if deleted, false if not found or self-protection triggered
     * @throws IllegalArgumentException if last-super-admin protection triggered
     */
    @Transactional
    public boolean delete(Long id, Long currentUserId) {
        // Self-protection
        if (id.equals(currentUserId)) {
            return false;
        }

        Optional<SstsUser> found = userRepository.findByIdWithRole(id);
        if (found.isEmpty()) {
            return false;
        }

        SstsUser user = found.get();

        // Last-super-admin protection
        SstsRole role = user.getRole();
        if (role != null && SUPER_ADMIN_ROLE_NAME.equals(role.getName())) {
            long superAdminCount = userRepository.countByRoleName(SUPER_ADMIN_ROLE_NAME);
            if (superAdminCount <= 1) {
                throw new IllegalArgumentException(
                        "This is the only super admin; promote another account first.");
            }
        }

        // No child row to remove first: bio/avatarUrl are columns on ssts_users.
        userRepository.deleteById(id);
        return true;
    }

    /** Returns all roles for the role dropdown in the form. */
    @Transactional(readOnly = true)
    public List<SstsRole> findAllRoles() {
        return roleRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    /**
     * Copies the per-person columns that live directly on ssts_users.
     *
     * <p>Shared by create and update so the two can never drift apart. These are
     * a FUNCTION of the person, so they belong on the user row rather than in a
     * separate profile table; the form object is the bound entity, so anything
     * not listed here is deliberately not written.
     *
     * <p>Phone and postal address are NOT written for a parent account:
     * ssts_families is the authoritative source for a parent's contact details,
     * because it holds both parents. Existing values are left untouched rather
     * than nulled so that demoting a staff account to 'parent' does not destroy
     * data that may still be needed.
     */
    private void copyPerPersonFields(SstsUser target, SstsUser form) {
        target.setDateOfBirth(form.getDateOfBirth());
        target.setBio(form.getBio());
        target.setOccupation(form.getOccupation());
        target.setEmployer(form.getEmployer());
        target.setDepartment(form.getDepartment());
        target.setAlternateEmail(form.getAlternateEmail());
        target.setAvatarUrl(form.getAvatarUrl());
        target.setYearsInCommunity(form.getYearsInCommunity());
        target.setPriorEducation(form.getPriorEducation());
        target.setPriorTamilExperience(form.getPriorTamilExperience());
        target.setPriorTeachingExperience(form.getPriorTeachingExperience());
        target.setPriorVolunteerExperience(form.getPriorVolunteerExperience());
        target.setCertifications(form.getCertifications());
        target.setInterests(form.getInterests());

        if ("parent".equals(form.getUserType())) {
            return;
        }

        target.setPhone(form.getPhone());
        target.setAddressLine1(form.getAddressLine1());
        target.setAddressLine2(form.getAddressLine2());
        target.setCity(form.getCity());
        target.setState(form.getState());
        target.setZipCode(form.getZipCode());
        target.setCountry(form.getCountry());
    }

    private void validateCreate(SstsUser form, Long roleId) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("That email is already registered.");
        }
        if (!isValidUserType(form.getUserType())) {
            throw new IllegalArgumentException("Invalid user type: " + form.getUserType());
        }
        // Use findById instead of existsById to avoid extra query; we need the role anyway
        if (roleId == null || !roleRepository.findById(roleId).isPresent()) {
            throw new IllegalArgumentException("Selected role does not exist.");
        }
        if (form.getUsername() != null && form.getUsername().length() > 50) {
            throw new IllegalArgumentException("Username must not exceed 50 characters.");
        }
        if (form.getEmail() != null && form.getEmail().length() > 100) {
            throw new IllegalArgumentException("Email must not exceed 100 characters.");
        }
        if (form.getFullName() != null && form.getFullName().length() > 100) {
            throw new IllegalArgumentException("Full name must not exceed 100 characters.");
        }
        if (form.getDesignation() != null && form.getDesignation().length() > 100) {
            throw new IllegalArgumentException("Designation must not exceed 100 characters.");
        }
        validatePerPersonFields(form);
    }

    /**
     * Re-checks the per-person columns against their schema constraints. The
     * form's maxlength/min are client-side only, so a crafted POST reaches the
     * database without them and a violation would otherwise surface as a raw
     * DataIntegrityViolationException instead of a message an admin can act on.
     */
    private void validatePerPersonFields(SstsUser form) {
        // Not a length: an integer with a CHECK, so it needs a range instead.
        if (form.getYearsInCommunity() != null && form.getYearsInCommunity() < 0) {
            throw new IllegalArgumentException("Years in community must not be negative.");
        }

        requireMax(form.getOccupation(), 100, "Occupation");
        requireMax(form.getEmployer(), 100, "Employer");
        requireMax(form.getDepartment(), 100, "Department");
        requireMax(form.getPhone(), 20, "Phone");
        requireMax(form.getAlternateEmail(), 100, "Alternate email");
        requireMax(form.getAddressLine1(), 255, "Address line 1");
        requireMax(form.getAddressLine2(), 255, "Address line 2");
        requireMax(form.getCity(), 100, "City");
        requireMax(form.getState(), 50, "State");
        requireMax(form.getZipCode(), 20, "Zip code");
        requireMax(form.getCountry(), 50, "Country");
        requireMax(form.getAvatarUrl(), 500, "Avatar path");
    }

    private void requireMax(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(label + " must not exceed " + max + " characters.");
        }
    }

    private boolean isValidUserType(String userType) {
        if (userType == null) return false;
        for (String valid : VALID_USER_TYPES) {
            if (valid.equals(userType)) return true;
        }
        return false;
    }
}
