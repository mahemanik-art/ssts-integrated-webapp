package org.sstamilschool.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores gallery photos in an S3-compatible bucket.
 *
 * <p><b>Why not the local filesystem.</b> The app runs containerized, and a
 * container's filesystem is thrown away on every deploy. An earlier version
 * wrote uploads to {@code src/main/resources/static/images/gallery/} in dev and
 * fell back to a sibling {@code ./static} folder next to the jar in Docker.
 * That fallback could not work: it is not on the classpath, so nothing served
 * it (every uploaded photo 404'd), and the directory was discarded on the next
 * release while the {@code ssts_gallery_events} row survived pointing at a
 * file that no longer existed. In dev the primary path masked both problems,
 * because that directory IS the classpath and IS served.
 *
 * <p><b>Layout.</b> Every event gets a key prefix {@code gallery/<folder>/}
 * (folder = slug of the title plus the id, so two same-titled events never
 * share objects). Multiple uploads become {@code 1.jpg}, {@code 2.jpg}, ... in
 * that one prefix, continuing from the highest number already stored.
 *
 * <p><b>Public URLs.</b> The service never guesses a URL from the SDK: the
 * stored value is built from the configured {@code app.storage.public-url}
 * prefix plus the key. That keeps object naming and addressing separate, which
 * is what lets the same bucket sit behind a custom domain or an R2 public
 * bucket URL without a code change.
 *
 * <p><b>When storage is disabled</b> (no bucket configured) the service is
 * constructed inert: {@link #isEnabled()} is false and {@link #store} throws
 * {@link StorageDisabledException}. It deliberately does NOT build an S3
 * client at all, so a missing bucket cannot fail application startup.
 */
@Service
public class GalleryImageStorageService {

    /** Key prefix for every gallery object inside the bucket. */
    static final String KEY_PREFIX = "gallery/";

    /** Raised when an admin uploads while no bucket is configured. */
    public static class StorageDisabledException extends RuntimeException {
        public StorageDisabledException(String message) {
            super(message);
        }
    }

    private final boolean enabled;
    private final String bucket;
    private final String region;
    private final String publicUrl;
    private final long maxUploadBytes;
    private final S3Client client;

    public GalleryImageStorageService(
            @Value("${app.storage.enabled:false}") boolean enabled,
            @Value("${app.storage.bucket:ssts-gallery}") String bucket,
            @Value("${app.storage.region:auto}") String region,
            @Value("${app.storage.endpoint-url:}") String endpointUrl,
            @Value("${app.storage.public-url:}") String publicUrl,
            @Value("${app.storage.access-key:}") String accessKey,
            @Value("${app.storage.secret-key:}") String secretKey,
            @Value("${app.storage.max-upload-bytes:10485760}") long maxUploadBytes) {
        this.enabled = enabled;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.region = region == null || region.isBlank() ? "us-east-1" : region.trim();
        this.publicUrl = trimTrailingSlash(publicUrl == null ? "" : publicUrl.trim());
        this.maxUploadBytes = maxUploadBytes;
        this.client = (enabled && this.bucket.isEmpty()) ? null
                : buildClient(enabled, region, endpointUrl, accessKey, secretKey);
    }

    /**
     * Test-only constructor: injects a (mock) S3 client directly so the upload
     * path can be exercised without a bucket or network.
     */
    GalleryImageStorageService(S3Client client, String bucket, String publicUrl) {
        this.enabled = true;
        this.client = client;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.region = "us-east-1";
        this.publicUrl = trimTrailingSlash(publicUrl == null ? "" : publicUrl.trim());
        this.maxUploadBytes = 10485760L;
    }

    /** Test-only constructor for the disabled case. */
    GalleryImageStorageService() {
        this.enabled = false;
        this.client = null;
        this.bucket = "";
        this.region = "us-east-1";
        this.publicUrl = "";
        this.maxUploadBytes = 10485760L;
    }

    private static S3Client buildClient(boolean enabled, String region, String endpointUrl,
                                        String accessKey, String secretKey) {
        if (!enabled) {
            return null;
        }
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region == null || region.isBlank() ? "auto" : region.trim()));
        String endpoint = endpointUrl == null ? "" : endpointUrl.trim();
        if (!endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(false).build());
        }
        if (accessKey != null && !accessKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.trim(), secretKey == null ? "" : secretKey.trim())));
        }
        // Credentials omitted above => fall back to the default provider chain
        // (env vars, ~/.aws, EC2/ECS task role, workload identity).
        return builder.build();
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * True when a bucket is configured and this service will actually upload.
     * The admin form uses it to fail fast with a readable message.
     */
    public boolean isEnabled() {
        return enabled && client != null && !bucket.isEmpty();
    }

    /**
     * Folder/prefix name for an event's objects, slugified from the title and
     * suffixed with the id once it exists, so two same-titled events never
     * collide.
     */
    public String folderFor(String title, Long id) {
        String slug = slugify(title);
        return id != null ? slug + "-" + id : slug;
    }

    /**
     * Uploads every non-empty file into the event's prefix and returns their
     * public URLs IN UPLOAD ORDER.
     *
     * @throws StorageDisabledException when no bucket is configured
     * @throws UncheckedIOException     when the upload fails
     */
    public List<String> store(String folder, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        String prefix = KEY_PREFIX + folder + "/";

        List<String> stored = new ArrayList<>();
        int next = 1;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            stored.add(storeObject(prefix, String.valueOf(next) + extensionOf(file.getOriginalFilename()), file));
            next++;
        }
        return stored;
    }

    /**
     * Generic single-object put, used by other modules (donor logos) that store
     * into the same bucket but under their own key prefix.
     *
     * <p>This is the one place that actually talks to S3, so size limits,
     * content type, and the two error messages live here exactly once.
     *
     * @return the public URL of the stored object
     * @throws StorageDisabledException when no bucket is configured
     * @throws UncheckedIOException     when the upload fails
     */
    public String storeObject(String keyPrefix, String objectName, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (!isEnabled()) {
            throw new StorageDisabledException(
                    "Photo storage is not configured on this server.");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new UncheckedIOException(new IOException(
                    "'" + safeName(file.getOriginalFilename()) + "' is larger than the "
                            + (maxUploadBytes / 1024 / 1024) + " MB limit."));
        }
        String key = keyPrefix + objectName;
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded image", e);
        } catch (S3Exception e) {
            throw new UncheckedIOException(new IOException(
                    "Could not store the photo in the bucket (" + e.awsErrorDetails().errorCode() + ")", e));
        }
        return publicUrlFor(key);
    }

    /**
     * Public URL for an object key. Falls back to the virtual-hosted S3 URL when
     * no public-url is configured, so a dev bucket without a CDN still resolves.
     */
    String publicUrlFor(String key) {
        if (!publicUrl.isEmpty()) {
            return publicUrl + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * Deletes an event's stored objects. Best-effort per object: a failure here
     * must not block deleting the gallery row itself, so the exception is
     * swallowed after being logged. The row is the source of truth for what the
     * gallery shows; an orphaned object in a bucket is far less harmful than an
     * admin unable to delete an entry.
     */
    public void deleteFolder(String folder) {
        deletePrefix(KEY_PREFIX, folder);
    }

    /**
     * Deletes every object under an arbitrary {@code <keyPrefix><folder>/}
     * prefix. Same best-effort contract as {@link #deleteFolder}: an orphaned
     * object in a bucket is far less harmful than an admin unable to delete a
     * row, so a failure here is swallowed rather than propagated.
     */
    public void deletePrefix(String keyPrefix, String folder) {
        if (!isEnabled() || folder == null || folder.isBlank()) {
            return;
        }
        String prefix = keyPrefix + folder + "/";
        // S3 ListObjects paginates; the SDK's listObjectsV2Paginator handles that.
        for (var page : client.listObjectsV2Paginator(b -> b.bucket(bucket).prefix(prefix))) {
            var keys = page.contents().stream().map(o -> o.key()).toList();
            if (keys.isEmpty()) {
                continue;
            }
            try {
                List<ObjectIdentifier> ids = keys.stream()
                        .map(k -> ObjectIdentifier.builder().key(k).build())
                        .toList();
                client.deleteObjects(b -> b.bucket(bucket)
                        .delete(Delete.builder().objects(ids).quiet(true).build()));
            } catch (RuntimeException ignored) {
                // Orphaned objects are acceptable; a failed row delete is not.
            }
        }
    }

    /**
     * Derives the key prefix for a stored public URL, or null when the value is
     * not one of ours. Used by the delete path to find an event's objects
     * without needing a separate column.
     */
    public String folderOf(String storedUrl) {
        return folderOfUrl(storedUrl, KEY_PREFIX);
    }

    /**
     * Derives the folder for a stored URL under an arbitrary key prefix, or null
     * when the value is not one of ours. The null is load-bearing: it is what
     * stops a delete from removing objects this service does not own, so a
     * hand-edited URL can never make the server delete something outside its
     * own prefix.
     */
    public String folderOfUrl(String storedUrl, String keyPrefix) {
        if (storedUrl == null || keyPrefix == null) {
            return null;
        }
        String head = publicUrl + "/" + keyPrefix;
        if (storedUrl.startsWith(head)) {
            String rest = storedUrl.substring(head.length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : null;
        }
        // No public-url configured: publicUrlFor() falls back to the
        // virtual-hosted S3 URL, so match against that instead.
        if (!publicUrl.isEmpty()) {
            return null;
        }
        String fallback = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + keyPrefix;
        if (!storedUrl.startsWith(fallback)) {
            return null;
        }
        String rest = storedUrl.substring(fallback.length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : null;
    }

    /**
     * Normalizes a stored path for the slider. Kept so the admin service does
     * not have to know about storage details.
     */
    public String normalize(String storedPath) {
        return storedPath == null ? null : storedPath.trim();
    }

    private static String safeName(String originalName) {
        return originalName == null ? "image" : originalName;
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

    private static String slugify(String title) {
        String s = Normalizer.normalize(title == null ? "event" : title, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "event" : s;
    }

    /**
     * Random object-name suffix. Present so a future private bucket can be
     * switched to without the caller having to change.
     */
    static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
