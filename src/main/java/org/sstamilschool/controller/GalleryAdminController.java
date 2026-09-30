package org.sstamilschool.controller;

import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.service.GalleryAdminService;
import org.sstamilschool.service.GalleryImageStorageService;

/**
 * Super-admin CRUD for gallery entries, under the /superadmin/** URL space.
 * Protected by SecurityConfig's .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
 * and @PreAuthorize here as defense in depth -- the same Stage 1 pattern
 * CalendarAdminController and TeamAdminController use.
 *
 * <p>Photos are files, uploaded through this form. Each event's photos live in
 * their own folder under src/main/resources/static/images/gallery/<folder>/
 * (folder = slug of the title + id), served at /images/gallery/...; the row
 * stores only relative paths, exactly like team avatarUrl.
 */
@Controller
@RequestMapping("/superadmin/gallery")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class GalleryAdminController {

    private final GalleryAdminService galleryAdminService;

    public GalleryAdminController(GalleryAdminService galleryAdminService) {
        this.galleryAdminService = galleryAdminService;
    }

    /**
     * Every stored image reference must be either a relative /images/... path
     * (an image committed to static/images/) or an https:// URL into the
     * configured gallery bucket -- the DB CHECKs
     * chk_ssts_gallery_events_image_url and chk_ssts_gallery_events_image_urls
     * enforce exactly that. The cover-path field is free text, so an admin
     * typing anything else trips the constraint. Catch it and explain, rather
     * than letting a constraint violation become a bare 500 -- the same
     * treatment UserAdminController gives its unique-constraint race.
     */
    private static final String BAD_IMAGE_PATH_MESSAGE =
            "Image references must be either a path under /images/ (for example "
            + "/images/gallery/my-photo.jpg) or an https:// URL. An http:// URL, "
            + "a local filesystem path, or a path containing spaces is not "
            + "accepted. The simplest option is to upload the photo below.";

    /**
     * Shown when no bucket is configured, so an admin gets an actionable
     * message instead of a failed upload with no explanation.
     */
    private static final String STORAGE_DISABLED_MESSAGE =
            "Photo uploads are not configured on this server (app.storage.enabled "
            + "is off or no bucket is set), so nothing can be stored. Ask an "
            + "administrator to configure the gallery bucket, or enter an "
            + "existing image path under /images/ instead.";

    @GetMapping
    public String list(Model model) {
        model.addAttribute("events", galleryAdminService.findAll());
        return "superadmin/gallery/list";
    }

    @GetMapping("/new")
    public String newEvent(Model model) {
        model.addAttribute("event", galleryAdminService.newEvent());
        return "superadmin/gallery/form";
    }

    @PostMapping
    public String create(@ModelAttribute("event") SstsGalleryEvent event,
                         @RequestParam(value = "images", required = false) List<MultipartFile> images,
                         RedirectAttributes redirect) {
        if (event.getTitle() == null || event.getTitle().isBlank()
                || event.getEventDate() == null) {
            redirect.addFlashAttribute("error", "Title and event date are required.");
            return "redirect:/superadmin/gallery/new";
        }
        try {
            galleryAdminService.create(event, images);
        } catch (UncheckedIOException e) {
            redirect.addFlashAttribute("error", "Could not save the uploaded photos: " + e.getMessage());
            return "redirect:/superadmin/gallery/new";
        } catch (GalleryImageStorageService.StorageDisabledException e) {
            redirect.addFlashAttribute("error", STORAGE_DISABLED_MESSAGE);
            return "redirect:/superadmin/gallery/new";
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", BAD_IMAGE_PATH_MESSAGE);
            return "redirect:/superadmin/gallery/new";
        }
        redirect.addFlashAttribute("message", "Gallery entry created.");
        return "redirect:/superadmin/gallery";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        SstsGalleryEvent event = galleryAdminService.findById(id);
        if (event == null) {
            return "redirect:/superadmin/gallery";
        }
        model.addAttribute("event", event);
        model.addAttribute("folderName", galleryAdminService.folderNameFor(id));
        return "superadmin/gallery/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("event") SstsGalleryEvent form,
                         @RequestParam(value = "images", required = false) List<MultipartFile> images,
                         RedirectAttributes redirect) {
        if (form.getTitle() == null || form.getTitle().isBlank()
                || form.getEventDate() == null) {
            redirect.addFlashAttribute("error", "Title and event date are required.");
            return "redirect:/superadmin/gallery/" + id + "/edit";
        }
        SstsGalleryEvent updated;
        try {
            updated = galleryAdminService.update(id, form, images);
        } catch (UncheckedIOException e) {
            redirect.addFlashAttribute("error", "Could not save the uploaded photos: " + e.getMessage());
            return "redirect:/superadmin/gallery/" + id + "/edit";
        } catch (GalleryImageStorageService.StorageDisabledException e) {
            redirect.addFlashAttribute("error", STORAGE_DISABLED_MESSAGE);
            return "redirect:/superadmin/gallery/" + id + "/edit";
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", BAD_IMAGE_PATH_MESSAGE);
            return "redirect:/superadmin/gallery/" + id + "/edit";
        }
        if (updated == null) {
            return "redirect:/superadmin/gallery";
        }
        redirect.addFlashAttribute("message", "Gallery entry updated.");
        return "redirect:/superadmin/gallery";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        boolean deleted = galleryAdminService.delete(id);
        redirect.addFlashAttribute(
                deleted ? "message" : "error",
                deleted ? "Gallery entry deleted." : "Gallery entry not found.");
        return "redirect:/superadmin/gallery";
    }
}
