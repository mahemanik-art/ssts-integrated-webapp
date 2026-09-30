package org.sstamilschool.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.dto.RegisterRequest;
import org.sstamilschool.model.SstsFamily;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsFamilyRepository;

/**
 * The PARENT MODULE's service: everything that is a fact about a HOUSEHOLD
 * rather than about a login.
 *
 * <p>The single rule this service exists to enforce: a parent's phone and postal
 * address live on {@code ssts_families}, never on {@code ssts_users}. The admin
 * screen already enforces this in {@code UserAdminService.copyPerPersonFields},
 * which skips the contact block for {@code user_type = 'parent'}. This service is
 * the registration-side half of the same rule -- before it existed,
 * {@code LoginService.register} wrote {@code phone}, {@code addressLine1..country}
 * straight onto the user row, so the two write paths disagreed and a registered
 * parent's contact details sat in a column nothing reads for parents.
 *
 * <p>Keeping the rule in ONE place matters: it is duplicated policy, so the
 * second copy is where the drift happens. If you add another path that creates a
 * parent, route it through {@link #createFamilyForNewParent}.
 *
 * <p>Not cached, deliberately: like announcements, this is administrative state
 * read once per page view. A 24h TTL would show a parent stale household data.
 */
@Service
public class ParentService {

    private final SstsFamilyRepository familyRepository;

    public ParentService(SstsFamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    /**
     * Creates the household for a newly registered parent and links it to the
     * account.
     *
     * <p>Must be called in the same transaction as the user save, so a failure
     * cannot leave an account with no family -- which is the state every
     * previously-registered parent is in.
     *
     * @param user the just-saved parent account
     * @param request the registration form
     * @return the created family
     * @throws IllegalStateException if the account is already linked to a family,
     *         because silently creating a second household for one person is
     *         exactly the duplication ssts_families exists to prevent
     */
    @Transactional
    public SstsFamily createFamilyForNewParent(SstsUser user, RegisterRequest request) {
        if (user.getId() == null) {
            throw new IllegalStateException("Cannot link a family to an unsaved user.");
        }
        if (familyRepository.existsByUserId(user.getId())) {
            throw new IllegalStateException(
                    "This account is already linked to a family; refusing to create a second one.");
        }

        SstsFamily family = new SstsFamily();
        family.setUser(user);
        // The registering parent is parent1 by definition: they are the account
        // holder, and parent1 is the column pair the admin screens key on.
        family.setParent1FullName(user.getFullName());
        family.setParent1Email(user.getEmail());
        family.setParent2FullName(trimToNull(request.getParent2FullName()));
        family.setParent2Email(trimToNull(request.getParent2Email()));

        family.setPhone1(trimToNull(request.getPhone()));
        family.setPhone2(trimToNull(request.getPhone2()));
        family.setStreet(composeStreet(request.getAddressLine1(), request.getAddressLine2()));
        family.setCity(trimToNull(request.getCity()));
        family.setState(trimToNull(request.getState()));
        family.setZip(trimToNull(request.getZipCode()));
        family.setCountry(defaultCountry(request.getCountry()));
        family.setEmergencyContact(trimToNull(request.getEmergencyContact()));

        return familyRepository.save(family);
    }

    /**
     * The family for a logged-in parent, if they have one.
     *
     * <p>Empty is a normal, non-exceptional answer: a family can exist with no
     * login and an account can exist with no family, so callers must render an
     * empty state rather than assume a match.
     */
    @Transactional(readOnly = true)
    public Optional<SstsFamily> findFamilyForUser(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return familyRepository.findByUserId(userId);
    }

    /**
     * ssts_families has ONE street column, but the form collects two address
     * lines. Rather than add a column for the rare second line (which would mean
     * a schema change plus a migration for every existing environment), the lines
     * are joined. This is deliberately lossy: it is a display/contact field, not
     * something the school queries by address.
     */
    private String composeStreet(String line1, String line2) {
        String first = trimToNull(line1);
        String second = trimToNull(line2);
        if (first == null) {
            return null;
        }
        return second == null ? first : first + ", " + second;
    }

    private String defaultCountry(String country) {
        String value = trimToNull(country);
        return value != null ? value : "USA";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
