package org.sstamilschool.service;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;

/**
 * CRUD for the public team/staff directory under the super-admin module.
 *
 * <p>Team members are ordinary ssts_users rows with user_type 'staff' or
 * 'volunteer' -- the two values SstsUserRepository.findPublicTeamMembers()
 * selects from for /team. A member's card on that page renders exactly four
 * fields, all editable here:
 * <ul>
 *   <li>fullName (SstsUser)</li>
 *   <li>designation (SstsUser)</li>
 *   <li>bio (SstsUser)</li>
 *   <li>avatarUrl (SstsUser)</li>
 * </ul>
 * userType and isActive are editable too because they gate whether the member
 * appears on /team at all.
 *
 * <p>Every write evicts the TEAM_MEMBERS cache so the public page reflects
 * changes immediately -- same pattern CalendarAdminService uses for
 * CALENDAR_EVENTS.
 */
@Service
public class TeamAdminService {

    /** Role applied when the form does not pick one. */
    public static final String DEFAULT_ROLE = "volunteer_coordinator";

    private final SstsUserRepository userRepository;
    private final SstsRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public TeamAdminService(SstsUserRepository userRepository,
                            SstsRoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<SstsUser> findAll() {
        return userRepository.findByUserTypeInOrderByFullNameAsc(List.of("staff", "volunteer"));
    }

    @Transactional(readOnly = true)
    public SstsUser findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    /** New member with sensible defaults for the admin form. */
    public SstsUser newMember() {
        SstsUser user = new SstsUser();
        user.setUserType("staff");
        user.setActive(true);
        user.setRole(roleRepository.findByName(DEFAULT_ROLE).orElse(null));
        return user;
    }

    /**
     * @param rawPassword only used on create; encoded with BCrypt before storing.
     * @return the saved user, or null when username/email are already taken.
     */
    @CacheEvict(value = CacheConfig.TEAM_MEMBERS, allEntries = true)
    @Transactional
    public SstsUser create(SstsUser form, String rawPassword) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("That email is already registered.");
        }

        SstsUser user = new SstsUser();
        user.setUsername(form.getUsername());
        user.setEmail(form.getEmail());
        user.setFullName(form.getFullName());
        user.setPasswordHash(passwordEncoder.encode(rawPassword == null ? "" : rawPassword));
        user.setUserType(form.getUserType());
        user.setDesignation(form.getDesignation());
        user.setActive(form.isActive());
        user.setEmailVerified(false);
        // Role is NOT taken from the form, ever. The form object is the bound
        // SstsUser entity, so a client can post role.id=<super_admin's role id>
        // and have it mass-assigned; honouring it would let any super-admin mint
        // a second SUPER_ADMIN account through a screen that documents role as
        // not editable. New members always get DEFAULT_ROLE.
        user.setRole(roleRepository.findByName(DEFAULT_ROLE).orElse(null));

        // One save. bio/avatarUrl are columns on ssts_users now, so there is no
        // second row to create and no back-reference to keep in sync -- which is
        // the class of bug this table used to be prone to (a NULL profile_id
        // silently rendered the fallback letter on /team).
        applyProfileFields(user, form);
        return userRepository.save(user);
    }

    /**
     * Updates the team-visible fields plus the two visibility flags. Username,
     * email, role and password are intentionally left alone -- they are auth
     * concerns, not team-page content, and are not rendered on /team.
     *
     * @return the updated user, or null when the id does not exist.
     */
    @CacheEvict(value = CacheConfig.TEAM_MEMBERS, allEntries = true)
    @Transactional
    public SstsUser update(Long id, SstsUser form) {
        SstsUser existing = userRepository.findById(id).orElse(null);
        if (existing == null) return null;

        existing.setFullName(form.getFullName());
        existing.setUserType(form.getUserType());
        existing.setDesignation(form.getDesignation());
        existing.setActive(form.isActive());
        applyProfileFields(existing, form);

        userRepository.save(existing);
        return existing;
    }

    /**
     * Hard-deletes the member. There is no child row to remove first: bio and
     * avatarUrl are columns on ssts_users, so a single delete is the whole
     * operation.
     *
     * <p>Refuses to delete the acting super-admin (id == currentUserId) so an
     * admin cannot lock themselves out of /superadmin/**.
     *
     * @return false when the id is missing or is the acting user.
     */
    @CacheEvict(value = CacheConfig.TEAM_MEMBERS, allEntries = true)
    @Transactional
    public boolean delete(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) return false;
        Optional<SstsUser> found = userRepository.findById(id);
        if (found.isEmpty()) return false;

        userRepository.deleteById(id);
        return true;
    }

    /** Copies the /team-visible bio and avatar onto the entity itself. */
    private void applyProfileFields(SstsUser target, SstsUser form) {
        target.setBio(form.getBio());
        target.setAvatarUrl(form.getAvatarUrl());
    }
}
