package org.sstamilschool.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.repository.SstsGalleryEventRepository;

/**
 * Read side of the public Event Gallery (/gallery).
 *
 * <p>Cached in-memory for ~24h (app.cache.gallery-ttl-hours) because the
 * gallery changes rarely, same as TeamService and CalendarService. Writes go
 * through GalleryAdminService, which evicts this cache.
 */
@Service
public class GalleryService {

    private final SstsGalleryEventRepository repository;

    public GalleryService(SstsGalleryEventRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = CacheConfig.GALLERY_EVENTS, key = "'public'")
    @Transactional(readOnly = true)
    public List<SstsGalleryEvent> getPublicEvents() {
        return repository.findByActiveTrueOrderByDisplayOrderAscEventDateDesc();
    }

    @CacheEvict(value = CacheConfig.GALLERY_EVENTS, allEntries = true)
    public void evictGalleryEvents() {
    }
}
