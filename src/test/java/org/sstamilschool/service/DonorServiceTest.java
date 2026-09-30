package org.sstamilschool.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsDonation;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.repository.SstsDonationRepository;
import org.sstamilschool.repository.SstsDonorRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache + CRUD tests for DonorService, wired with CacheConfig and mocked
 * repositories (no live DB) -- mirrors GalleryAdminServiceTest.
 *
 * <p>The tests that matter most here are the ones about totals and about the
 * cached public list, because those are the two places this module could
 * quietly disagree with the ledger.
 */
@SpringJUnitConfig(classes = { CacheConfig.class, DonorServiceTest.TestConfig.class })
class DonorServiceTest {

    @Autowired DonorService donorService;
    @Autowired SstsDonorRepository donorRepo;
    @Autowired SstsDonationRepository donationRepo;

    @Configuration
    static class TestConfig {
        @Bean
        SstsDonorRepository donorRepository() {
            return mock(SstsDonorRepository.class);
        }

        @Bean
        SstsDonationRepository donationRepository() {
            return mock(SstsDonationRepository.class);
        }

        /**
         * Registered explicitly rather than component-scanned, so the test uses
         * the real DonorService with mocked repositories -- the same wiring
         * GalleryAdminServiceTest uses for its service.
         */
        @Bean
        DonorService donorService(SstsDonorRepository donorRepository,
                                  SstsDonationRepository donationRepository) {
            return new DonorService(donorRepository, donationRepository);
        }
    }

    private static SstsDonor donor(long id, String name) {
        SstsDonor d = new SstsDonor();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private static SstsDonation gift(long donorId, String amount, String date) {
        SstsDonation g = new SstsDonation();
        g.setDonor(donor(donorId, "D" + donorId));
        g.setAmount(new BigDecimal(amount));
        g.setDonatedOn(LocalDate.parse(date));
        return g;
    }

    @BeforeEach
    void reset() {
        clearInvocations(donorRepo, donationRepo);
    }

    // ------------------------------------------------------- cache contract

    @Test
    void publicListIsCachedAndAdminListIsNot() {
        when(donorRepo.findPubliclyListed()).thenReturn(List.of(donor(1L, "Acme")));

        donorService.findPubliclyListed();
        donorService.findPubliclyListed();

        // The second call is served from Caffeine; the repository is hit once.
        verify(donorRepo, org.mockito.Mockito.times(1)).findPubliclyListed();

        donorService.findAllForAdmin();
        verify(donorRepo).findAllByOrderByDisplayOrderAscNameAsc();
    }

    @Test
    void savingADonorEvictsTheCachedPublicList() {
        when(donorRepo.findPubliclyListed()).thenReturn(List.of(donor(1L, "Acme")));
        donorService.findPubliclyListed();

        when(donorRepo.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(donorRepo.save(any(SstsDonor.class))).thenAnswer(i -> i.getArgument(0));
        donorService.create(donor(0L, "New Co"));

        // Without the eviction the home page would keep serving the old list
        // for up to the full TTL.
        donorService.findPubliclyListed();
        verify(donorRepo, org.mockito.Mockito.times(2)).findPubliclyListed();
    }

    // ------------------------------------------------------------- totals

    @Test
    void totalForSumsTheLedgerExactly() {
        when(donationRepo.findByDonorIdForLedger(7L))
                .thenReturn(List.of(gift(7L, "100.10", "2026-01-05"), gift(7L, "250.00", "2026-02-11")));

        // 0.1 + 0.25 is exactly representable in binary ONLY as a decimal
        // concern -- this is the assertion that a float/double implementation
        // would fail with 350.09999999999997.
        assertThat(donorService.totalFor(7L)).isEqualByComparingTo(new BigDecimal("350.10"));
    }

    @Test
    void totalForAnUnknownDonorIsZeroNotNull() {
        assertThat(donorService.totalFor(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void overallTotalSumsEveryGift() {
        when(donationRepo.findAllForLedger())
                .thenReturn(List.of(gift(1L, "19.99", "2026-01-01"), gift(2L, "0.01", "2026-01-02")));

        assertThat(donorService.overallTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void yearTotalQueriesTheWholeCalendarYear() {
        when(donationRepo.findByDonatedOnBetweenForLedger(any(), any()))
                .thenReturn(List.of(gift(1L, "500.00", "2026-07-04")));

        assertThat(donorService.totalForYear(2026)).isEqualByComparingTo(new BigDecimal("500.00"));
        verify(donationRepo).findByDonatedOnBetweenForLedger(
                ArgumentMatchers.eq(LocalDate.of(2026, 1, 1)),
                ArgumentMatchers.eq(LocalDate.of(2026, 12, 31)));
    }

    // ------------------------------------------------------------ validation

    @Test
    void createRefusesABlankName() {
        assertThatThrownBy(() -> donorService.create(donor(0L, "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
        verify(donorRepo, never()).save(any());
    }

    @Test
    void createRefusesADuplicateName() {
        when(donorRepo.existsByNameIgnoreCase("Acme")).thenReturn(true);

        assertThatThrownBy(() -> donorService.create(donor(0L, "Acme")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateAllowsSavingTheSameNameUnchanged() {
        when(donorRepo.findById(3L)).thenReturn(Optional.of(donor(3L, "Acme")));
        when(donorRepo.save(any(SstsDonor.class))).thenAnswer(i -> i.getArgument(0));

        // The pre-check must exclude the donor's own row, or every save of an
        // unchanged name would report a collision with itself.
        donorService.update(3L, donor(3L, "Acme"));

        verify(donorRepo, never()).findByNameIgnoreCaseExcludingId(anyString(), anyLong());
    }

    @Test
    void updateRefusesRenamingOntoAnotherDonorsName() {
        when(donorRepo.findById(3L)).thenReturn(Optional.of(donor(3L, "Acme")));
        when(donorRepo.findByNameIgnoreCaseExcludingId("Beta", 3L))
                .thenReturn(Optional.of(donor(4L, "Beta")));

        assertThatThrownBy(() -> donorService.update(3L, donor(3L, "Beta")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different donor");
    }

    // --------------------------------------------------------------- delete

    @Test
    void deleteRefusesADonorWhoHasGiven() {
        when(donorRepo.findById(3L)).thenReturn(Optional.of(donor(3L, "Acme")));
        when(donationRepo.countByDonorId(3L)).thenReturn(2L);

        assertThatThrownBy(() -> donorService.delete(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be deleted")
                .hasMessageContaining("Deactivate");
        verify(donorRepo, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesADonorWithNoGivingHistory() {
        when(donorRepo.findById(3L)).thenReturn(Optional.of(donor(3L, "Acme")));
        when(donationRepo.countByDonorId(3L)).thenReturn(0L);

        donorService.delete(3L);

        verify(donorRepo).deleteById(3L);
    }

    @Test
    void requireThrowsForAMissingDonorSoAGiftCannotDangle() {
        when(donorRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donorService.require(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer exists");
    }
}
