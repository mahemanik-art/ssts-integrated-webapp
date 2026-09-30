package org.sstamilschool.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.repository.SstsGalleryEventRepository;
import org.springframework.web.multipart.MultipartFile;

/**
 * CRUD for the public Event Gallery, under the super-admin module.
 *
 * <p>The form covers every field the /gallery card renders: imageUrl, title,
 * eventDate and description, plus displayOrder and active which control whether
 * and where a card shows up.
 *
 * <p>Photos are files. An upload of one or more images is handed to
 * {@link GalleryImageStorageService}, which writes them into the event's own
 * folder under static/images/gallery/<folder>/ and returns their relative
 * paths; the FIRST upload also becomes imageUrl (the card's default frame),
 * and the full ordered list is stored newline-separated in imageUrls for the
 * card's hover slider. A hand-typed imageUrl still works and simply becomes
 * that one slider slide.
 *
 * <p>Every write evicts the GALLERY_EVENTS cache so the public page reflects
 * changes immediately -- the same pattern CalendarAdminService and
 * TeamAdminService use for their caches.
 */
@Service
public class GalleryAdminService {

    private final SstsGalleryEventRepository repository;
    private final GalleryImageStorageService storage;

    public GalleryAdminService(SstsGalleryEventRepository repository,
                               GalleryImageStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<SstsGalleryEvent> findAll() {
        return repository.findAll(Sort.by(
                Sort.Order.asc("displayOrder"),
                Sort.Order.desc("eventDate"),
                Sort.Order.asc("title")));
    }

    @Transactional(readOnly = true)
    public SstsGalleryEvent findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /** Folder name (slug + id) the event's photos live in, for the form hint. */
    @Transactional(readOnly = true)
    public String folderNameFor(Long id) {
        SstsGalleryEvent event = repository.findById(id).orElse(null);
        return event == null ? null : storage.folderFor(event.getTitle(), event.getId());
    }

    /** New entry with sensible defaults for the admin form. */
    public SstsGalleryEvent newEvent() {
        SstsGalleryEvent event = new SstsGalleryEvent();
        event.setActive(true);
        event.setDisplayOrder(0);
        return event;
    }

    @CacheEvict(value = CacheConfig.GALLERY_EVENTS, allEntries = true)
    @Transactional
    public SstsGalleryEvent create(SstsGalleryEvent event, List<MultipartFile> files) {
        repository.save(event); // need the id to name the folder
        applyUpload(event, files);
        return repository.save(event);
    }

    @CacheEvict(value = CacheConfig.GALLERY_EVENTS, allEntries = true)
    @Transactional
    public SstsGalleryEvent update(Long id, SstsGalleryEvent form, List<MultipartFile> files) {
        SstsGalleryEvent existing = repository.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setTitle(form.getTitle());
        existing.setEventDate(form.getEventDate());
        existing.setDescription(form.getDescription());
        existing.setDisplayOrder(form.getDisplayOrder());
        existing.setActive(form.isActive());
        existing.setImageUrl(form.getImageUrl());
        existing.setImageUrls(form.getImageUrls());
        applyUpload(existing, files);
        return repository.save(existing);
    }

    /**
     * Appends uploaded files to the event's folder and rewrites imageUrl +
     * imageUrls so the first upload is the card's default frame and every
     * upload appears in the slider, in upload order.
     */
    private void applyUpload(SstsGalleryEvent event, List<MultipartFile> files) {
        if (files == null || files.stream().allMatch(f -> f == null || f.isEmpty())) {
            return;
        }
        String folder = storage.folderFor(event.getTitle(), event.getId());
        List<String> uploaded = storage.store(folder, files);
        if (uploaded.isEmpty()) return;

        List<String> slides = new ArrayList<>(event.getSlides());
        // A hand-typed imageUrl that isn't already in the list keeps its spot
        // as the first slide so admins can pin a cover photo manually.
        String cover = event.getImageUrl();
        if (cover != null && !cover.isBlank() && !slides.contains(cover)) {
            slides.add(0, cover);
        }
        slides.addAll(uploaded);
        if (cover == null || cover.isBlank()) cover = slides.get(0);

        event.setImageUrl(cover);
        event.setSlides(slides);
    }

    @CacheEvict(value = CacheConfig.GALLERY_EVENTS, allEntries = true)
    @Transactional
    public boolean delete(Long id) {
        SstsGalleryEvent existing = repository.findById(id).orElse(null);
        if (existing == null) return false;
        String folder = storage.folderOf(existing.getImageUrl());
        repository.deleteById(id);
        storage.deleteFolder(folder);
        return true;
    }
}
