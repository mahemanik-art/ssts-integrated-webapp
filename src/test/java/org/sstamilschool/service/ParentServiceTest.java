package org.sstamilschool.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.dto.RegisterRequest;
import org.sstamilschool.model.SstsFamily;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.repository.SstsFamilyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The parent module's core rule: a parent's phone and address live on
 * ssts_families, NEVER on ssts_users.
 *
 * <p>Mocked repo only, so this is fast and DB-free. The database-level half of
 * the same rule is chk_ssts_families_parent1_email_needs_name plus the phone
 * CHECKs; neither can catch a value written to the wrong TABLE, which is why
 * this test exists.
 *
 * <p>No CacheConfig: the parent module is deliberately uncached.
 */
@SpringJUnitConfig(classes = { ParentServiceTest.TestConfig.class })
class ParentServiceTest {

    @Autowired ParentService parentService;
    @Autowired SstsFamilyRepository familyRepository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsFamilyRepository familyRepository() {
            return mock(SstsFamilyRepository.class);
        }

        @Bean
        ParentService parentService(SstsFamilyRepository familyRepository) {
            return new ParentService(familyRepository);
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(familyRepository);
    }

    @Test void createsAFamilyLinkedToTheRegisteredAccount() {
        when(familyRepository.existsByUserId(7L)).thenReturn(false);
        when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SstsUser user = parent(7L, "Lakshmi Raman", "lakshmi@example.test");
        RegisterRequest request = request();
        request.setFullName("Lakshmi Raman");
        request.setEmail("lakshmi@example.test");

        SstsFamily family = parentService.createFamilyForNewParent(user, request);

        // The account holder is parent1 by definition.
        assertThat(family.getParent1FullName()).isEqualTo("Lakshmi Raman");
        assertThat(family.getParent1Email()).isEqualTo("lakshmi@example.test");
        assertThat(family.getUser()).isSameAs(user);
    }

    @Test void routesPhoneAndAddressOntoTheFamilyNotTheUser() {
        when(familyRepository.existsByUserId(7L)).thenReturn(false);
        when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest request = request();
        request.setPhone("404-555-0142");
        request.setAddressLine1("12 Kolam St");
        request.setAddressLine2("Apt 3");
        request.setCity("Atlanta");
        request.setState("GA");
        request.setZipCode("30328");
        request.setCountry("USA");

        SstsFamily family = parentService.createFamilyForNewParent(parent(7L, "L", "l@x.test"), request);

        assertThat(family.getPhone1()).isEqualTo("404-555-0142");
        assertThat(family.getCity()).isEqualTo("Atlanta");
        assertThat(family.getState()).isEqualTo("GA");
        assertThat(family.getZip()).isEqualTo("30328");
        assertThat(family.getCountry()).isEqualTo("USA");
        // Two address lines, one street column: joined, and the lossy-ness is deliberate.
        assertThat(family.getStreet()).isEqualTo("12 Kolam St, Apt 3");
    }

    @Test void treatsBlankOptionalFieldsAsNullNotEmptyString() {
        when(familyRepository.existsByUserId(7L)).thenReturn(false);
        when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest request = request();
        request.setPhone("   ");
        request.setParent2FullName("  ");
        request.setParent2Email("");
        request.setAddressLine1("");
        request.setCountry("  ");

        SstsFamily family = parentService.createFamilyForNewParent(parent(7L, "L", "l@x.test"), request);

        // '' and '   ' would pass the DB's email/phone CHECKs, which explicitly
        // allow blank, and would then render as a blank contact everywhere.
        assertThat(family.getPhone1()).isNull();
        assertThat(family.getParent2FullName()).isNull();
        assertThat(family.getParent2Email()).isNull();
        assertThat(family.getStreet()).isNull();
        // Country is the one default that IS applied.
        assertThat(family.getCountry()).isEqualTo("USA");
    }

    @Test void keepsTheSecondParentAndEmergencyContact() {
        when(familyRepository.existsByUserId(7L)).thenReturn(false);
        when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest request = request();
        request.setParent2FullName("Ravi Raman");
        request.setParent2Email("ravi@example.test");
        request.setPhone2("404-555-0143");
        request.setEmergencyContact("404-555-0199");

        SstsFamily family = parentService.createFamilyForNewParent(parent(7L, "L", "l@x.test"), request);

        assertThat(family.getParent2FullName()).isEqualTo("Ravi Raman");
        assertThat(family.getParent2Email()).isEqualTo("ravi@example.test");
        assertThat(family.getPhone2()).isEqualTo("404-555-0143");
        assertThat(family.getEmergencyContact()).isEqualTo("404-555-0199");
    }

    @Test void refusesToCreateASecondFamilyForOneAccount() {
        when(familyRepository.existsByUserId(7L)).thenReturn(true);

        // One household per person is the entire point of ssts_families: a second
        // row would duplicate both parents' contact details for the same family.
        assertThatThrownBy(() -> parentService.createFamilyForNewParent(parent(7L, "L", "l@x.test"), request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already linked");

        verify(familyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test void refusesToLinkAnUnsavedUser() {
        SstsUser unsaved = parent(null, "L", "l@x.test");

        assertThatThrownBy(() -> parentService.createFamilyForNewParent(unsaved, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsaved");

        verify(familyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test void findFamilyForUserReturnsEmptyRatherThanThrowing() {
        // user_id is nullable, so "no family" is a legal state, not an error.
        when(familyRepository.findByUserId(9L)).thenReturn(Optional.empty());
        assertThat(parentService.findFamilyForUser(9L)).isEmpty();

        // A null id must not reach the repository as a query.
        assertThat(parentService.findFamilyForUser(null)).isEmpty();
        verify(familyRepository, org.mockito.Mockito.never()).findByUserId(null);
    }

    private static SstsUser parent(Long id, String fullName, String email) {
        SstsUser user = new SstsUser();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setUserType("parent");
        return user;
    }

    private static RegisterRequest request() {
        return new RegisterRequest();
    }
}
