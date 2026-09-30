package org.sstamilschool.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsDonation;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.repository.SstsDonationRepository;
import org.sstamilschool.repository.SstsDonorRepository;
import org.sstamilschool.util.SchoolTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ledger tests for DonationService. The money cases are the point: an amount
 * that silently rounds, or a total that does not reconcile, is a real problem
 * in a way that a mis-sized button is not.
 */
@SpringJUnitConfig(classes = { CacheConfig.class, DonationServiceTest.TestConfig.class })
class DonationServiceTest {

    @Autowired DonationService donationService;
    @Autowired DonorService donorService;
    @Autowired SstsDonationRepository donationRepo;
    @Autowired SstsDonorRepository donorRepo;

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

        @Bean
        DonorService donorService(SstsDonorRepository d, SstsDonationRepository g) {
            return new DonorService(d, g);
        }

        @Bean
        DonationService donationService(SstsDonationRepository g, DonorService donorService) {
            return new DonationService(g, donorService);
        }
    }

    private static SstsDonor donor(long id) {
        SstsDonor d = new SstsDonor();
        d.setId(id);
        d.setName("Donor " + id);
        return d;
    }

    private static SstsDonation form(String amount) {
        SstsDonation g = new SstsDonation();
        g.setAmount(new BigDecimal(amount));
        g.setDonor(donor(1L));
        return g;
    }

    @BeforeEach
    void reset() {
        clearInvocations(donationRepo, donorRepo);
        when(donorRepo.findById(1L)).thenReturn(Optional.of(donor(1L)));
    }

    // ------------------------------------------------------------ precision

    @Test
    void amountsAreStoredAtScaleTwo() {
        SstsDonation g = form("100.1");

        assertThat(g.getAmount()).isEqualByComparingTo(new BigDecimal("100.10"));
        assertThat(g.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void aThirdDecimalPlaceIsRejectedRatherThanRounded() {
        SstsDonor d = donor(1L);
        SstsDonation g = new SstsDonation();
        g.setDonor(d);

        // UNNECESSARY must throw. Silently turning 100.005 into 100.00 or
        // 100.01 would alter a real financial record.
        assertThatThrownBy(() -> g.setAmount(new BigDecimal("100.005")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void aNullAmountIsAllowedOnTheEntityButRefusedByTheService() {
        SstsDonation g = new SstsDonation();
        g.setAmount((BigDecimal) null);
        assertThat(g.getAmount()).isNull();

        assertThatThrownBy(() -> donationService.record(new SstsDonation()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount is required");
    }

    @Test
    void zeroAndNegativeAmountsAreRefused() {
        assertThatThrownBy(() -> donationService.record(form("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> donationService.record(form("-5.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void anAmountBeyondTheColumnWidthIsRefusedWithAReadableMessage() {
        // numeric(12,2) allows 10 integer digits; beyond that Postgres would
        // raise an opaque numeric overflow instead.
        assertThatThrownBy(() -> donationService.record(form("99999999999.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    // ------------------------------------------------------------- recording

    @Test
    void recordDefaultsTheDateToTheSchoolToday() {
        when(donationRepo.save(org.mockito.ArgumentMatchers.any(SstsDonation.class)))
                .thenAnswer(i -> i.getArgument(0));

        SstsDonation saved = donationService.record(form("25.00"));

        // Not LocalDate.now(): the server runs UTC and would file an evening
        // gift under tomorrow.
        assertThat(saved.getDonatedOn()).isEqualTo(SchoolTime.today());
    }

    @Test
    void recordResolvesTheDonorSoTheReferenceCannotDangle() {
        when(donorRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.record(form("25.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer exists");
        verify(donationRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blankMethodAndReferenceAreStoredAsNullNotEmptyString() {
        when(donationRepo.save(org.mockito.ArgumentMatchers.any(SstsDonation.class)))
                .thenAnswer(i -> i.getArgument(0));

        SstsDonation g = form("25.00");
        g.setMethod("  ");
        g.setReferenceNo("");

        SstsDonation saved = donationService.record(g);
        assertThat(saved.getMethod()).isNull();
        assertThat(saved.getReferenceNo()).isNull();
    }

    // --------------------------------------------------------------- update

    @Test
    void updateCannotMoveAGiftToADifferentDonor() {
        SstsDonation existing = form("25.00");
        existing.setId(4L);
        when(donationRepo.findById(4L)).thenReturn(Optional.of(existing));
        when(donationRepo.save(org.mockito.ArgumentMatchers.any(SstsDonation.class)))
                .thenAnswer(i -> i.getArgument(0));

        SstsDonation other = form("99.00");
        other.setDonor(donor(77L));
        donationService.update(4L, other);

        // The bound donor is ignored on purpose: moving a gift between donors
        // silently rewrites whose history it belongs to.
        assertThat(existing.getDonor().getId()).isEqualTo(1L);
        assertThat(existing.getAmount()).isEqualByComparingTo(new BigDecimal("99.00"));
    }

    @Test
    void updateRejectsAMissingRow() {
        when(donationRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.update(404L, form("10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer exists");
    }

    // --------------------------------------------------------------- delete

    @Test
    void deleteRemovesAnExistingGift() {
        when(donationRepo.existsById(4L)).thenReturn(true);

        donationService.delete(4L);

        verify(donationRepo).deleteById(4L);
    }

    @Test
    void deleteOfAMissingRowIsARefusalNotASilentSuccess() {
        when(donationRepo.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> donationService.delete(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer exists");
    }

    // ------------------------------------------------------------------ read

    @Test
    void ledgerIsReadThroughTheFetchingQuery() {
        when(donationRepo.findAllForLedger()).thenReturn(List.of());

        donationService.findLedger();

        // Not findAll(): the ledger must LEFT JOIN FETCH the donor so the list
        // page does not hit an N+1 or a lazy-init failure.
        verify(donationRepo).findAllForLedger();
        verify(donationRepo, never()).findAll();
    }
}
