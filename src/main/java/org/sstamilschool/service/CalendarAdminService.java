package org.sstamilschool.service;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsCalendarEvent;
import org.sstamilschool.repository.SstsCalendarEventRepository;
import org.sstamilschool.util.AcademicYear;

/**
 * CRUD for calendar events under the super-admin module.
 * Every write evicts the CALENDAR_EVENTS cache (all entries) so the
 * public /calendar page reflects changes immediately -- same pattern
 * TeamService.evictTeamMembers() uses for the team cache.
 */
@Service
public class CalendarAdminService {

    private final SstsCalendarEventRepository repository;

    public CalendarAdminService(SstsCalendarEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SstsCalendarEvent> findAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.asc("academicYear"),
                org.springframework.data.domain.Sort.Order.asc("eventDate")));
    }

    @Transactional(readOnly = true)
    public SstsCalendarEvent findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /** New event with sensible defaults for the admin form. */
    public SstsCalendarEvent newEvent() {
        SstsCalendarEvent event = new SstsCalendarEvent();
        event.setEventType("working");
        event.setAcademicYear(AcademicYear.current());
        event.setActive(true);
        return event;
    }

    @CacheEvict(value = CacheConfig.CALENDAR_EVENTS, allEntries = true)
    @Transactional
    public SstsCalendarEvent create(SstsCalendarEvent event) {
        return repository.save(event);
    }

    @CacheEvict(value = CacheConfig.CALENDAR_EVENTS, allEntries = true)
    @Transactional
    public SstsCalendarEvent update(Long id, SstsCalendarEvent form) {
        SstsCalendarEvent existing = repository.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setTitle(form.getTitle());
        existing.setEventDate(form.getEventDate());
        existing.setEndDate(form.getEndDate());
        existing.setEventType(form.getEventType());
        existing.setAcademicYear(form.getAcademicYear());
        existing.setDescription(form.getDescription());
        existing.setActive(form.isActive());
        return repository.save(existing);
    }

    @CacheEvict(value = CacheConfig.CALENDAR_EVENTS, allEntries = true)
    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
