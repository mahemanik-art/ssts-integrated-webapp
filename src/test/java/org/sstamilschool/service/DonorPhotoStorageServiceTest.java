package org.sstamilschool.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import software.amazon.awssdk.services.s3.S3Client;
import java.util.function.Consumer;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DonorPhotoStorageService naming and ownership rules.
 *
 * <p>The load-bearing behaviour is that a donor logo is a FIXED object name
 * (replaced on re-upload, not accumulated like a gallery event) and that a
 * committed {@code /images/...} path is never treated as a bucket object, so
 * delete cannot reach outside the donors/ prefix.
 */
class DonorPhotoStorageServiceTest {

    private S3Client s3;
    private GalleryImageStorageService storage;
    private DonorPhotoStorageService service;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        storage = new GalleryImageStorageService(s3, "test-bucket", "https://cdn.example.com");
        service = new DonorPhotoStorageService(storage);
    }

    private static MockMultipartFile file(String name, String original) {
        return new MockMultipartFile(name, original, "image/jpeg", new byte[] { 1, 2, 3 });
    }

    // -------------------------------------------------------------- naming

    @Test
    void folderIsTheSlugPlusTheId() {
        assertThat(service.folderFor("Madras Photo Studios", 12L)).isEqualTo("madras-photo-studios-12");
    }

    @Test
    void folderFallsBackToTheSlugBeforeAnIdExists() {
        assertThat(service.folderFor("Blue Oak Consulting", null)).isEqualTo("blue-oak-consulting");
    }

    @Test
    void nonAsciiNamesStillProduceAUsableSlug() {
        // Tamil or accented text must not collapse the folder to nothing, or
        // every such donor would share the "donor" prefix.
        assertThat(service.folderFor("கலைஞர் அறக்கட்டளை", 3L)).isEqualTo("donor-3");
        assertThat(service.folderFor("Café Süd", 4L)).isEqualTo("cafe-sud-4");
    }

    @Test
    void twoDonorsWhoseNamesSlugAlikeGetDifferentFolders() {
        // The id suffix is what stops these sharing a key prefix.
        assertThat(service.folderFor("Sai Ram Foundation", 1L))
                .isNotEqualTo(service.folderFor("Sairam Foundation", 2L));
    }

    // -------------------------------------------------------------- storing

    @Test
    void storeWritesAFixedLogoNameAndReturnsTheCdnUrl() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

        String url = service.store("Acme Corp", 7L, file("photo", "Acme.PNG"));

        // Fixed name: a donor's logo is replaced, not accumulated as 1/2/3.
        assertThat(url).isEqualTo("https://cdn.example.com/donors/acme-corp-7/logo.png");
    }

    @Test
    void extensionIsNormalisedAndUnknownOnesFallBackToJpg() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

        assertThat(service.store("A", 1L, file("p", "x.WEBP")))
                .endsWith("/donors/a-1/logo.webp");
        assertThat(service.store("B", 2L, file("p", "noextension")))
                .endsWith("/donors/b-2/logo.jpg");
        assertThat(service.store("C", 3L, file("p", "payload.exe")))
                .endsWith("/donors/c-3/logo.jpg");
    }

    @Test
    void anEmptyUploadStoresNothing() {
        assertThat(service.store("Acme", 1L, new MockMultipartFile("photo", new byte[0]))).isNull();
        assertThat(service.store("Acme", 1L, null)).isNull();
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void storageDisabledPropagatesTheReadableMessage() {
        DonorPhotoStorageService disabled = new DonorPhotoStorageService(new GalleryImageStorageService());

        assertThat(disabled.isEnabled()).isFalse();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> disabled.store("Acme", 1L, file("p", "x.jpg")))
                .isInstanceOf(GalleryImageStorageService.StorageDisabledException.class)
                .hasMessageContaining("not configured");
    }

    // ------------------------------------------------------------ ownership

    @Test
    void aCommittedImagePathIsNotTreatedAsABucketObject() {
        // Nothing of ours owns /images/donors/x.jpg, so delete must be a no-op
        // rather than deriving some folder and removing unrelated objects.
        service.delete("/images/MadrasPhotStudios.jpg");

        verify(s3, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
        verify(s3, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void aUrlFromAnotherHostIsNotOursToDelete() {
        service.delete("https://somewhere-else.example.com/donors/acme-7/logo.png");

        verify(s3, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
    }

    @Test
    void ourOwnUrlResolvesToItsFolder() {
        assertThat(storage.folderOfUrl("https://cdn.example.com/donors/acme-corp-7/logo.png", "donors/"))
                .isEqualTo("acme-corp-7");
    }
}
