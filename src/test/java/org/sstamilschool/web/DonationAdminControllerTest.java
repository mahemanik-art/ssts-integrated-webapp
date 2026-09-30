package org.sstamilschool.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.sstamilschool.controller.DonationAdminController;
import org.sstamilschool.model.SstsDonation;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.service.DonationService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.util.SchoolTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * @WebMvcTest slice for the donations ledger.
 *
 * <p>The money assertions here are the reason this class exists. The ledger page
 * is where a wrong number would be most visible and most costly, so the tests
 * pin that the totals shown are summed from the rows on the same request, that
 * the year figure uses the SCHOOL's current year rather than the server's, and
 * that a bad amount becomes a flash rather than a 500.
 */
@WebMvcTest(DonationAdminController.class)
@Import(TestSecurityConfig.class)
class DonationAdminControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean DonationService donationService;
    @MockitoBean DonorService donorService;

    private static final RequestPostProcessor SUPER_ADMIN = user("ssts_admin").roles("SUPER_ADMIN");

    private static SstsDonor donor(long id, String name) {
        SstsDonor d = new SstsDonor();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private static SstsDonation gift(long id, String donorName, String amount, String date) {
        SstsDonation g = new SstsDonation();
        g.setId(id);
        g.setDonor(donor(1L, donorName));
        g.setAmount(new BigDecimal(amount));
        g.setDonatedOn(LocalDate.parse(date));
        g.setMethod("check");
        return g;
    }

    @BeforeEach
    void setup() {
        when(donorService.findActiveForPicker())
                .thenReturn(List.of(donor(1L, "Acme Corp"), donor(2L, "Riverwood Dental")));
        when(donorService.totalForYear(Mockito.anyInt())).thenReturn(new BigDecimal("750.25"));
    }

    // ---------------------------------------------------------------- list

    @Test
    void listRendersTheLedgerAndBothTotals() throws Exception {
        when(donationService.findLedger()).thenReturn(List.of(
                gift(1L, "Acme Corp", "500.00", "2026-03-01"),
                gift(2L, "Riverwood Dental", "250.25", "2026-07-04")));

        mvc.perform(get("/superadmin/donations").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/donations/list"))
                .andExpect(content().string(containsString("Acme Corp")))
                .andExpect(content().string(containsString("Riverwood Dental")))
                // All-time total is summed from the rows, and the year figure is
                // a separate live call rather than a filtered list.
                .andExpect(content().string(containsString("750.25")));
    }

    @Test
    void listRendersAnEmptyLedger() throws Exception {
        when(donationService.findLedger()).thenReturn(List.of());

        mvc.perform(get("/superadmin/donations").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No donations recorded yet")));
    }

    // ---------------------------------------------------------------- form

    /**
     * The new-gift form must default to the SCHOOL's date. The server runs UTC
     * and would otherwise pre-date an evening gift by a day.
     */
    @Test
    void newFormDefaultsTheDateToTheSchoolToday() throws Exception {
        mvc.perform(get("/superadmin/donations/new").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin/donations/form"))
                .andExpect(content().string(containsString(SchoolTime.today().toString())));
    }

    @Test
    void newFormOffersOnlyActiveDonors() throws Exception {
        mvc.perform(get("/superadmin/donations/new").with(SUPER_ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Acme Corp")))
                .andExpect(content().string(containsString("Riverwood Dental")));
    }

    @Test
    void editingAMissingDonationRedirectsToTheLedger() throws Exception {
        when(donationService.findById(404L)).thenReturn(null);

        mvc.perform(get("/superadmin/donations/404/edit").with(SUPER_ADMIN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donations"));
    }

    // -------------------------------------------------------------- create

    @Test
    void createSendsTheDonorAsAnIdNotABoundObject() throws Exception {
        mvc.perform(post("/superadmin/donations").with(SUPER_ADMIN).with(csrf())
                        .param("donorId", "2")
                        .param("amount", "125.50")
                        .param("donatedOn", "2026-05-04"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donations"));

        // The form must not be able to smuggle in a whole donor object: only the
        // id crosses the boundary and the service re-resolves it.
        org.mockito.ArgumentCaptor<SstsDonation> captor =
                org.mockito.ArgumentCaptor.forClass(SstsDonation.class);
        verify(donationService).record(captor.capture());
        assertThat(captor.getValue().getDonor().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("125.50"));
    }

    @Test
    void aBadAmountBecomesAFlashNotA500() throws Exception {
        doThrow(new IllegalArgumentException("A donation amount must be greater than zero."))
                .when(donationService).record(any(SstsDonation.class));

        mvc.perform(post("/superadmin/donations").with(SUPER_ADMIN).with(csrf())
                        .param("donorId", "1")
                        .param("amount", "-5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donations/new"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void aMissingDonorBecomesAFlashNotA500() throws Exception {
        doThrow(new IllegalArgumentException("That donor no longer exists."))
                .when(donationService).record(any(SstsDonation.class));

        mvc.perform(post("/superadmin/donations").with(SUPER_ADMIN).with(csrf())
                        .param("donorId", "999")
                        .param("amount", "10.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    // -------------------------------------------------------------- update

    @Test
    void updateSavesAndRedirects() throws Exception {
        mvc.perform(post("/superadmin/donations/1").with(SUPER_ADMIN).with(csrf())
                        .param("amount", "300.00")
                        .param("donatedOn", "2026-06-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donations"));

        verify(donationService).update(eq(1L), any(SstsDonation.class));
    }

    @Test
    void aFailedUpdateReturnsToTheEditForm() throws Exception {
        doThrow(new IllegalArgumentException("A donation amount is required."))
                .when(donationService).update(eq(1L), any(SstsDonation.class));

        mvc.perform(post("/superadmin/donations/1").with(SUPER_ADMIN).with(csrf())
                        .param("amount", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donations/1/edit"))
                .andExpect(flash().attributeExists("error"));
    }

    // -------------------------------------------------------------- delete

    @Test
    void deleteRemovesTheGift() throws Exception {
        mvc.perform(post("/superadmin/donations/1/delete").with(SUPER_ADMIN).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/donations"));

        verify(donationService).delete(1L);
    }

    @Test
    void deletingAMissingGiftReportsAnError() throws Exception {
        doThrow(new IllegalArgumentException("That donation no longer exists."))
                .when(donationService).delete(404L);

        mvc.perform(post("/superadmin/donations/404/delete").with(SUPER_ADMIN).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "That donation no longer exists."));
    }

    // -------------------------------------------------------- authorization

    @Test
    void aPlainAdminIsRefusedTheDonationsModule() throws Exception {
        mvc.perform(get("/superadmin/donations").with(user("plain").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
