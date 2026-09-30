package org.sstamilschool.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import org.sstamilschool.config.CacheConfig;
import org.sstamilschool.model.SstsGalleryEvent;
import org.sstamilschool.repository.SstsGalleryEventRepository;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache + CRUD test for GalleryAdminService, wired with CacheConfig and a mocked
 * repository (no live DB) -- mirrors CalendarAdminServiceTest.
 */
@SpringJUnitConfig(classes = { CacheConfig.class, GalleryAdminServiceTest.TestConfig.class })
class GalleryAdminServiceTest {

    @Autowired GalleryAdminService adminService;
    @Autowired GalleryService galleryService;
    @Autowired SstsGalleryEventRepository repository;

    @Configuration
    static class TestConfig {
        @Bean
        SstsGalleryEventRepository galleryEventRepository() {
            return mock(SstsGalleryEventRepository.class);
        }

        @Bean
        GalleryService galleryService(SstsGalleryEventRepository repository) {
            return new GalleryService(repository);
        }

        /**
         * Gallery photos are objects in S3/R2, so the test injects a mock S3
         * client rather than a temp directory. Public URLs are built from the
         * configured public-url prefix plus the object key.
         */
        @Bean
        S3Client s3Client() {
            return mock(S3Client.class);
        }

        @Bean
        GalleryImageStorageService storage(S3Client s3Client) {
            return new GalleryImageStorageService(
                    s3Client, "test-bucket", "https://cdn.example.com");
        }

        @Bean
        GalleryAdminService galleryAdminService(SstsGalleryEventRepository repository,
                                                GalleryImageStorageService storage) {
            return new GalleryAdminService(repository, storage);
        }
    }

    @BeforeEach
    void clearCache() throws Exception {
        galleryService.evictGalleryEvents();
        clearInvocations(repository);
        SstsGalleryEvent seeded = event(1L, "Seeded Event", LocalDate.of(2026, 1, 15), 10);
        when(repository.findByActiveTrueOrderByDisplayOrderAscEventDateDesc())
                .thenReturn(List.of(seeded));
    }

    @Test void repeatedReadsHitDatabaseOnlyOnce() {
        galleryService.getPublicEvents();
        galleryService.getPublicEvents();
        verify(repository, times(1)).findByActiveTrueOrderByDisplayOrderAscEventDateDesc();
    }

    @Test void createEvictsCacheSoPublicGalleryReloads() {
        SstsGalleryEvent event = event(null, "Pongal", LocalDate.of(2027, 1, 14), 5);
        event.setImageUrl("/images/gallery/pongal.jpg");
        when(repository.save(ArgumentMatchers.<SstsGalleryEvent>any())).thenReturn(event);

        galleryService.getPublicEvents();   // warm cache
        adminService.create(event, List.of());   // write -> evicts

        galleryService.getPublicEvents();
        verify(repository, times(2)).findByActiveTrueOrderByDisplayOrderAscEventDateDesc();
    }

    @Test void updateEvictsCacheAndCoversEveryPublicField() {
        SstsGalleryEvent existing = event(1L, "Old Title", LocalDate.of(2026, 1, 15), 10);
        SstsGalleryEvent form = event(1L, "New Title", LocalDate.of(2026, 3, 2), 99);
        form.setImageUrl("/images/gallery/new.jpg");
        form.setDescription("New description");
        form.setActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(ArgumentMatchers.<SstsGalleryEvent>any())).thenReturn(existing);

        galleryService.getPublicEvents();
        adminService.update(1L, form, List.of());

        // Every field the /gallery card renders, plus the two visibility/order columns.
        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(existing.getEventDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(existing.getImageUrl()).isEqualTo("/images/gallery/new.jpg");
        assertThat(existing.getDescription()).isEqualTo("New description");
        assertThat(existing.getDisplayOrder()).isEqualTo(99);
        assertThat(existing.isActive()).isFalse();

        galleryService.getPublicEvents();
        verify(repository, times(2)).findByActiveTrueOrderByDisplayOrderAscEventDateDesc();
    }

    @Test void updateMissingEntryIsNoOp() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThat(adminService.update(99L, event(99L, "Ghost", LocalDate.of(2026, 1, 1), 0), List.of())).isNull();
    }

    @Test void deleteEvictsCache() {
        SstsGalleryEvent existing = event(5L, "Old", LocalDate.of(2026, 1, 15), 10);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        galleryService.getPublicEvents();
        assertThat(adminService.delete(5L)).isTrue();

        verify(repository).deleteById(5L);
        galleryService.getPublicEvents();
        verify(repository, times(2)).findByActiveTrueOrderByDisplayOrderAscEventDateDesc();
    }

    @Test void deleteMissingEntryIsNoOp() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThat(adminService.delete(99L)).isFalse();
        verify(repository, times(0)).deleteById(99L);
    }

    @Test void updateAppliesUploadAndSetsCoverToFirstUploadedFile() {
        SstsGalleryEvent existing = event(1L, "Pongal", LocalDate.of(2026, 1, 15), 10);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(ArgumentMatchers.<SstsGalleryEvent>any())).thenReturn(existing);

        MockMultipartFile file = new MockMultipartFile(
                "images", "one.jpg", "image/jpeg", new byte[] {1, 2, 3});
        adminService.update(1L, existing, List.of(file));

        assertThat(existing.getSlides()).hasSize(1);
        // Uploaded photos are bucket objects, so the stored value is an https URL
        // built from the public-url prefix plus the object key.
        assertThat(existing.getSlides().get(0))
                .isEqualTo("https://cdn.example.com/gallery/pongal-1/1.jpg");
        // No prior cover: the first upload becomes the card's default frame.
        assertThat(existing.getImageUrl())
                .isEqualTo("https://cdn.example.com/gallery/pongal-1/1.jpg");
    }

    @Test void newEventHasSensibleDefaults() {
        SstsGalleryEvent event = adminService.newEvent();
        assertThat(event.isActive()).isTrue();
        assertThat(event.getDisplayOrder()).isZero();
        // Blank imageUrl is valid: the public card falls back to the first letter.
        assertThat(event.getImageUrl()).isNull();
    }

    @Test void findAllUsesAdminQueryNotThePublicOne() {
        SstsGalleryEvent inactive = event(2L, "Hidden", LocalDate.of(2025, 1, 1), 50);
        inactive.setActive(false);
        when(repository.findAll(ArgumentMatchers.<org.springframework.data.domain.Sort>any()))
                .thenReturn(List.of(inactive));

        // The admin list must show deactivated entries too, unlike the public feed.
        assertThat(adminService.findAll()).containsExactly(inactive);
        verify(repository, never()).findByActiveTrueOrderByDisplayOrderAscEventDateDesc();
    }

    private static SstsGalleryEvent event(Long id, String title, LocalDate date, int order) {
        SstsGalleryEvent event = new SstsGalleryEvent();
        event.setId(id);
        event.setTitle(title);
        event.setEventDate(date);
        event.setDisplayOrder(order);
        event.setActive(true);
        return event;
    }
}
