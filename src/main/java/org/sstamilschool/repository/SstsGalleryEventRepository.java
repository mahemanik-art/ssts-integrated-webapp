package org.sstamilschool.repository;
import org.sstamilschool.model.SstsGalleryEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SstsGalleryEventRepository extends JpaRepository<SstsGalleryEvent, Long> {

    /**
     * Public /gallery feed: active entries only, curated order first (ascending
     * displayOrder) then newest-first by date. Note the boolean property is
     * 'active' with getter isActive(), so the derived name must be
     * ...AndActiveTrue... -- NOT ...AndIsActiveTrue... (see AGENTS.md gotchas).
     */
    List<SstsGalleryEvent> findByActiveTrueOrderByDisplayOrderAscEventDateDesc();

    /** Active gallery events for Reports. */
    long countByActiveTrue();

    /** Inactive gallery events for Reports. */
    long countByActiveFalse();
}
