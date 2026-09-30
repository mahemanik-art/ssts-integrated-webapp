package org.sstamilschool.controller;

import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.sstamilschool.model.SstsDonor;
import org.sstamilschool.service.DonorPhotoStorageService;
import org.sstamilschool.service.DonorService;
import org.sstamilschool.service.GalleryImageStorageService.StorageDisabledException;

/**
 * Donor administration for super admins.
 *
 * <p>Follows the app's established CRUD shape: the ENTITY is the form object
 * ({@code @ModelAttribute("donor")}), the controller validates and redirects
 * with a flash message, and the service maps form -> entity and evicts the
 * cache.
 *
 * <p><b>No centralized exception handler</b> in this project, so the expected
 * failures are caught per method and turned into a readable flash. Each of the
 * three catches below covers a DIFFERENT real constraint:
 * <ul>
 *   <li>{@link IllegalArgumentException} -- the service's own validation
 *       (blank name, duplicate name).</li>
 *   <li>{@link IllegalStateException} -- deleting a donor who has donations.</li>
 *   <li>{@link DataIntegrityViolationException} -- the race where two admins
 *       save the same donor name concurrently, plus the website/photo CHECKs.
 *       The pre-check in the service is not atomic, so the database is what
 *       actually decides; without this catch a duplicate name surfaces as a
 *       bare HTTP 500.</li>
 * </ul>
 */
@Controller
@RequestMapping("/superadmin/donors")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DonorAdminController {

    /** Accepts a /images/... path or an https:// bucket URL, same rule as gallery. */
    static final String WEBSITE_HINT =
            "Website must be a full https:// address (http:// is rejected).";

    private final DonorService donorService;
    private final DonorPhotoStorageService photoStorage;

    public DonorAdminController(DonorService donorService, DonorPhotoStorageService photoStorage) {
        this.donorService = donorService;
        this.photoStorage = photoStorage;
    }

    /**
     * Denies the fields the form does not own.
     *
     * <p>{@code seedKey} is the load-bearing one. It marks rows created by
     * sql/data.sql, and because create/update bind the whole entity, a form
     * without that field would bind it as null and the subsequent merge would
     * silently ERASE the marker on every seeded donor. That is invisible until
     * someone later relies on it.
     *
     * <p>{@code id} is denied too and set explicitly from the path variable, so
     * a crafted POST cannot save one donor's data onto another's row.
     */
    @InitBinder("donor")
    public void denyInternalFields(WebDataBinder binder) {
        binder.setDisallowedFields("seedKey", "createdAt", "updatedAt", "id");
    }

    @GetMapping
    public String list(Model model) {
        List<SstsDonor> donors = donorService.findAllForAdmin();
        model.addAttribute("donors", donors);
        model.addAttribute("totals", totalsFor(donors));
        model.addAttribute("storageEnabled", photoStorage.isEnabled());
        return "superadmin/donors/list";
    }

    /**
     * Giving total per donor, computed at read time. Passed as a map because the
     * list template must not call the service (and therefore must not open a
     * second transaction or a second query per row).
     */
    private java.util.Map<Long, java.math.BigDecimal> totalsFor(List<SstsDonor> donors) {
        java.util.Map<Long, java.math.BigDecimal> totals = new java.util.LinkedHashMap<>();
        for (SstsDonor d : donors) {
            totals.put(d.getId(), donorService.totalFor(d.getId()));
        }
        return totals;
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("donor", new SstsDonor());
        model.addAttribute("storageEnabled", photoStorage.isEnabled());
        return "superadmin/donors/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        SstsDonor donor = donorService.findById(id);
        if (donor == null) {
            // Matches the app's convention: a missing row is "not found", not a 404.
            ra.addFlashAttribute("error", "That donor no longer exists.");
            return "redirect:/superadmin/donors";
        }
        model.addAttribute("donor", donor);
        model.addAttribute("storageEnabled", photoStorage.isEnabled());
        return "superadmin/donors/form";
    }

    @PostMapping
    public String create(@ModelAttribute("donor") SstsDonor donor,
                         @RequestParam(value = "photo", required = false) MultipartFile photo,
                         RedirectAttributes ra) {
        try {
            applyPhoto(donor, photo, null);
            donorService.create(donor);
            ra.addFlashAttribute("success", "Donor \"" + donor.getName() + "\" added.");
            return "redirect:/superadmin/donors";
        } catch (IllegalArgumentException e) {
            return backWithError(ra, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            return backWithError(ra, "That donor could not be saved: the name is already taken, "
                    + "or the website/photo value is not a valid /images/... path or https:// URL.");
        } catch (StorageDisabledException e) {
            return backWithError(ra, e.getMessage());
        } catch (UncheckedIOException e) {
            return backWithError(ra, "The photo could not be uploaded. " + e.getMessage());
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("donor") SstsDonor donor,
                         @RequestParam(value = "photo", required = false) MultipartFile photo,
                         @RequestParam(value = "removePhoto", required = false) String removePhoto,
                         RedirectAttributes ra) {
        SstsDonor existing = donorService.findById(id);
        if (existing == null) {
            ra.addFlashAttribute("error", "That donor no longer exists.");
            return "redirect:/superadmin/donors";
        }
        try {
            boolean replaced = applyPhoto(donor, photo, existing);
            donor.setId(id);
            donorService.update(id, donor);
            if (replaced || (removePhoto != null && !removePhoto.isBlank())) {
                photoStorage.delete(existing.getPhotoPath());
            }
            ra.addFlashAttribute("success", "Donor \"" + donor.getName() + "\" updated.");
            return "redirect:/superadmin/donors";
        } catch (IllegalArgumentException e) {
            return backWithError(ra, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            return backWithError(ra, "That donor could not be saved: the name is already taken, "
                    + "or the website/photo value is not a valid /images/... path or https:// URL.");
        } catch (StorageDisabledException e) {
            return backWithError(ra, e.getMessage());
        } catch (UncheckedIOException e) {
            return backWithError(ra, "The photo could not be uploaded. " + e.getMessage());
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            SstsDonor existing = donorService.findById(id);
            if (existing == null) {
                ra.addFlashAttribute("error", "That donor no longer exists.");
                return "redirect:/superadmin/donors";
            }
            donorService.delete(id);
            photoStorage.delete(existing.getPhotoPath());
            ra.addFlashAttribute("success", "Donor \"" + existing.getName() + "\" deleted.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin/donors";
    }

    /**
     * Resolves the photo field into a storable path.
     *
     * @return true when a NEW object was uploaded, so the caller knows the old
     *         one should be deleted
     */
    private boolean applyPhoto(SstsDonor donor, MultipartFile photo, SstsDonor existing) {
        String typed = donor.getPhotoPath() == null ? null : donor.getPhotoPath().trim();

        if (photo != null && !photo.isEmpty()) {
            // An upload wins over a typed path, and needs the id, which a create
            // does not have yet -- so on create the typed value is kept and the
            // admin re-uploads after the first save. Stated in the flash rather
            // than silently ignored.
            if (existing == null) {
                throw new IllegalArgumentException(
                        "Save the donor first, then upload its photo in a second pass.");
            }
            donor.setPhotoPath(photoStorage.store(existing.getName(), existing.getId(), photo));
            return true;
        }
        if (typed == null || typed.isEmpty()) {
            donor.setPhotoPath(null);
        } else {
            donor.setPhotoPath(typed);
        }
        return false;
    }

    /** Errors go back to the list as a flash, matching every other admin module. */
    private String backWithError(RedirectAttributes ra, String message) {
        ra.addFlashAttribute("error", message);
        return "redirect:/superadmin/donors";
    }
}