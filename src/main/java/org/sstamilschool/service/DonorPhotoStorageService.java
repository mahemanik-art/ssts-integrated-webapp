package org.sstamilschool.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores a donor logo as a single object in the same bucket as the gallery,
 * under its own {@code donors/} prefix.
 *
 * <p>This is deliberately a thin adapter over {@link GalleryImageStorageService}
 * rather than a second S3 client. Two services each building their own client
 * would mean two copies of the credential/endpoint/region logic, and the two
 * would inevitably drift -- most visibly the day someone adds a new auth method
 * to one of them. All bucket mechanics live in the gallery service; this class
 * only decides donor-specific naming.
 *
 * <p><b>One logo per donor, not a sequence.</b> A donor's logo is replaced, not
 * accumulated, so the key is a fixed {@code logo.<ext>} inside
 * {@code donors/<slug>-<id>/}. Re-uploading overwrites rather than accumulating
 * {@code 1.png}, {@code 2.png} the way a gallery event does.
 *
 * <p><b>Committed images still work.</b> A donor row may hold a
 * {@code /images/donors/...} path for a file that lives in the repository. That
 * is not an object in the bucket, so {@link #delete} resolves it to no folder
 * and does nothing -- the same guard that stops the gallery from deleting files
 * it does not own.
 */
@Service
public class DonorPhotoStorageService {

    /** Key prefix for every donor object inside the bucket. */
    static final String KEY_PREFIX = "donors/";

    /** Fixed object name: a donor has one current logo, replaced on re-upload. */
    static final String OBJECT_NAME = "logo";

    private final GalleryImageStorageService storage;

    public DonorPhotoStorageService(GalleryImageStorageService storage) {
        this.storage = storage;
    }

    /** Mirrors the gallery service so the admin form can explain a disabled bucket. */
    public boolean isEnabled() {
        return storage.isEnabled();
    }

    /**
     * Folder name for a donor's objects: slug of the name plus the id once it
     * exists, so two donors whose names slug to the same string ("Sai Ram
     * Foundation" and "Sairam Foundation") never share a prefix.
     */
    public String folderFor(String name, Long id) {
        String slug = slugify(name);
        return id != null ? slug + "-" + id : slug;
    }

    /**
     * Uploads one logo and returns its public URL, or null for an empty file.
     *
     * @throws GalleryImageStorageService.StorageDisabledException when unconfigured
     */
    public String store(String name, Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String folder = folderFor(name, id);
        return storage.storeObject(KEY_PREFIX + folder + "/",
                OBJECT_NAME + extensionOf(file.getOriginalFilename()), file);
    }

    /**
     * Best-effort delete of a donor's stored logo. A no-op when the stored value
     * is a committed {@code /images/...} path rather than one of our URLs.
     */
    public void delete(String storedUrl) {
        String folder = storage.folderOfUrl(storedUrl, KEY_PREFIX);
        if (folder != null) {
            storage.deletePrefix(KEY_PREFIX, folder);
        }
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return ".jpg";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return ".jpg";
        }
        String ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.(jpg|jpeg|png|gif|webp)") ? ext : ".jpg";
    }

    private static String slugify(String name) {
        String s = java.text.Normalizer
                .normalize(name == null ? "donor" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "donor" : s;
    }
}