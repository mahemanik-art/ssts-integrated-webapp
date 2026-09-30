package org.sstamilschool.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.sstamilschool.model.SstsDonation;
import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.service.DonationService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.util.SchoolTime;

/**
 * The donation ledger screen for super admins: record a gift, correct it, or
 * remove it.
 *
 * <p>Deliberately UNCached, like every other screen that shows administrative
 * state: this is money, and a 24h TTL would show a total that disagrees with
 * the ledger. There is therefore no {@code @CacheEvict} here and no PATCH
 * endpoint -- nothing to evict.
 */
@Controller
@RequestMapping("/superadmin/donations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DonationAdminController {

    private final DonationService donationService;
    private final DonorService donorService;

    public DonationAdminController(DonationService donationService, DonorService donorService) {
        this.donationService = donationService;
        this.donorService = donorService;
    }

    @GetMapping
    public String list(Model model) {
        List<SstsDonation> ledger = donationService.findLedger();
        model.addAttribute("donations", ledger);
        // Summed here in Java, not in SQL and not stored: see DonorService.
        BigDecimal total = BigDecimal.ZERO;
        for (SstsDonation d : ledger) {
            if (d.getAmount() != null) {
                total = total.add(d.getAmount());
            }
        }
        model.addAttribute("total", total);
        model.addAttribute("today", SchoolTime.today());
        model.addAttribute("currentYear", SchoolTime.today().getYear());
        model.addAttribute("yearTotal", donorService.totalForYear(SchoolTime.today().getYear()));
        return "superadmin/donations/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        SstsDonation donation = new SstsDonation();
        // Default the date to the SCHOOL's today, not LocalDate.now(): the server
        // runs UTC and would pre-date an evening gift by a day.
        donation.setDonatedOn(SchoolTime.today());
        model.addAttribute("donation", donation);
        model.addAttribute("donors", donorService.findActiveForPicker());
        model.addAttribute("today", SchoolTime.today());
        return "superadmin/donations/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        SstsDonation donation = donationService.findById(id);
        if (donation == null) {
            ra.addFlashAttribute("error", "That donation no longer exists.");
            return "redirect:/superadmin/donations";
        }
        model.addAttribute("donation", donation);
        model.addAttribute("donors", donorService.findActiveForPicker());
        model.addAttribute("today", SchoolTime.today());
        return "superadmin/donations/form";
    }

    @PostMapping
    public String create(@ModelAttribute("donation") SstsDonation donation,
                         @RequestParam("donorId") Long donorId,
                         RedirectAttributes ra) {
        try {
            donation.setDonor(referenceTo(donorId));
            donationService.record(donation);
            ra.addFlashAttribute("success", "Donation of " + donation.getAmount() + " recorded.");
            return "redirect:/superadmin/donations";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/donations/new";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("donation") SstsDonation donation,
                         RedirectAttributes ra) {
        try {
            // The donor is intentionally NOT re-assignable on edit; see the note
            // in the form template. The bound donor, if any, is simply ignored.
            donationService.update(id, donation);
            ra.addFlashAttribute("success", "Donation updated.");
            return "redirect:/superadmin/donations";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/donations/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            donationService.delete(id);
            ra.addFlashAttribute("success", "Donation deleted.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin/donations";
    }

    /**
     * Turns the posted donorId into a detached donor reference. The row is NOT
     * re-read here: {@code DonationService.record} resolves it through
     * {@code DonorService.require}, which both validates the id and gives the
     * saved donation a real, managed donor.
     */
    private static SstsDonor referenceTo(Long donorId) {
        SstsDonor donor = new SstsDonor();
        donor.setId(donorId);
        return donor;
    }
}
