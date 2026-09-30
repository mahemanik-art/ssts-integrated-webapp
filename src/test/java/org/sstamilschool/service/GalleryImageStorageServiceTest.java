package org.sstamilschool.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Unit tests for the bucket-backed gallery storage.
 *
 * <p>These matter because the previous filesystem implementation could not be
 * tested honestly at all: it wrote to a directory that, in a container, is not
 * on the classpath and is discarded on every deploy, so every uploaded photo
 * 404'd and then vanished. The S3 client is mocked, so what is actually pinned
 * here is the part the app is responsible for -- the object KEY it chooses and
 * the PUBLIC URL it returns -- not the network.
 */
class GalleryImageStorageServiceTest {

    private S3Client client;
    private GalleryImageStorageService storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        storage = new GalleryImageStorageService(client, "test-bucket", "https://cdn.example.com");
    }

    @Test
    void folderIsSlugifiedAndSuffixedWithTheId() {
        assertThat(storage.folderFor("Pongal Celebration!", 12L))
                .isEqualTo("pongal-celebration-12");
    }

    @Test
    void folderHasNoIdSuffixBeforeTheRowExists() {
        assertThat(storage.folderFor("Pongal Celebration", null))
                .isEqualTo("pongal-celebration");
    }

    @Test
    void folderFallsBackWhenTheTitleSlugifiesToNothing() {
        // e.g. a title that is entirely non-ASCII; must not produce an empty key.
        assertThat(storage.folderFor("ந்தமிழ்", 3L)).isEqualTo("event-3");
    }

    @Test
    void uploadReturnsAPublicUrlBuiltFromTheConfiguredPrefix() {
        MultipartFile file = new MockMultipartFile(
                "images", "holiday.jpg", "image/jpeg", new byte[] {1, 2, 3});

        List<String> urls = storage.store("pongal-12", List.of(file));

        assertThat(urls).containsExactly("https://cdn.example.com/gallery/pongal-12/1.jpg");
    }

    @Test
    void uploadsAreNumberedInOrderUnderOneKeyPrefix() {
        List<String> urls = storage.store("pongal-12", List.of(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1}),
                new MockMultipartFile("images", "b.png", "image/png", new byte[] {2})));

        assertThat(urls).containsExactly(
                "https://cdn.example.com/gallery/pongal-12/1.jpg",
                "https://cdn.example.com/gallery/pongal-12/2.png");
    }

    @Test
    void everyUploadGoesIntoTheSameBucket() {
        storage.store("pongal-12", List.of(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1})));

        verify(client).putObject(
                org.mockito.ArgumentMatchers.argThat(
                        (PutObjectRequest r) -> "test-bucket".equals(r.bucket())
                                && "gallery/pongal-12/1.jpg".equals(r.key())),
                any(RequestBody.class));
    }

    @Test
    void aDisallowedExtensionFallsBackToJpg() {
        List<String> urls = storage.store("e-1", List.of(
                new MockMultipartFile("images", "evil.svg", "image/svg+xml", new byte[] {1})));

        assertThat(urls).containsExactly("https://cdn.example.com/gallery/e-1/1.jpg");
    }

    @Test
    void emptyAndNullFilesAreSkipped() {
        List<String> urls = storage.store("e-1", java.util.Arrays.asList(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[0]),
                null,
                new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[] {1})));

        assertThat(urls).containsExactly("https://cdn.example.com/gallery/e-1/1.jpg");
    }

    @Test
    void noFilesMeansNoUploads() {
        assertThat(storage.store("e-1", List.of())).isEmpty();
        assertThat(storage.store("e-1", null)).isEmpty();
    }

    @Test
    void folderOfExtractsThePrefixFromAStoredUrl() {
        assertThat(storage.folderOf("https://cdn.example.com/gallery/pongal-12/1.jpg"))
                .isEqualTo("pongal-12");
    }

    @Test
    void folderOfReturnsNullForAUrlThatIsNotOurs() {
        assertThat(storage.folderOf("https://somewhere-else.example/x.jpg")).isNull();
        assertThat(storage.folderOf("/images/gallery/pongal-12/1.jpg")).isNull();
        assertThat(storage.folderOf(null)).isNull();
    }

    /**
     * The disabled case is the one that must never take the site down: with no
     * bucket configured the service is inert and explains itself, rather than
     * throwing something opaque at an admin mid-upload.
     */
    @Test
    void disabledStorageReportsItselfAndRefusesToStore() {
        GalleryImageStorageService off = new GalleryImageStorageService();

        assertThat(off.isEnabled()).isFalse();
        assertThatThrownBy(() -> off.store("e-1", List.of(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1}))))
                .isInstanceOf(GalleryImageStorageService.StorageDisabledException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void disabledStorageMakesNoNetworkCalls() {
        GalleryImageStorageService off = new GalleryImageStorageService();

        // deleteFolder must be a silent no-op, not an NPE on a null client.
        off.deleteFolder("pongal-12");
        off.deleteFolder(null);
        assertThat(off.isEnabled()).isFalse();
    }
}
