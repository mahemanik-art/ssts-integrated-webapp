package org.sstamilschool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the regexes behind the DB CHECKs chk_ssts_gallery_events_image_url and
 * chk_ssts_gallery_events_image_urls, which sql/schema.sql declares and which
 * therefore have no Java equivalent guarding them.
 *
 * <p>This is a Java transcription of Postgres ARE patterns, NOT a test of the
 * database.
 *
 * <p>Two things are pinned here.
 *
 * <p><b>1. The dollar-anchor placement.</b> The slider pattern originally put
 * the end-anchor INSIDE the repeating group (dollar-anchor, then a repetition
 * suffix of "backslash-s asterisk, dollar-anchor"). That can only ever match a
 * SINGLE trailing line: the group's trailing whitespace run has to match at
 * end-of-string, and Postgres' dollar-anchor does not match at an interior
 * newline, so the repetition could never advance past line 1. Every entry with
 * two or more photos -- which is what {@code SstsGalleryEvent.setSlides}
 * produces and what a multi-file upload always writes -- was rejected by the
 * constraint and surfaced as a raw HTTP 500. The dollar-anchor must stay
 * OUTSIDE the repeating group.
 *
 * <p><b>2. That both a relative path and an https:// bucket URL are accepted.</b>
 * Uploaded photos are objects in S3/R2, so the CHECK has to take a URL as well
 * as a committed relative path, while still refusing http, data: and
 * javascript: schemes and absolute filesystem paths.
 *
 * <p>Keep the patterns below byte-identical to the ones in sql/schema.sql, and
 * keep the CHECK's NULL/blank branches (which the patterns alone do not cover)
 * in mind when adding cases.
 */
class GalleryImagePathConstraintTest {

    /** Single-value CHECK: image_url. */
    private static final Pattern IMAGE_URL =
            Pattern.compile("^/images/[A-Za-z0-9._/-]+$|^https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+$");

    /** Multi-line CHECK: image_urls, one entry per line. */
    private static final Pattern IMAGE_URLS =
            Pattern.compile("^(\\s*(/images/[A-Za-z0-9._/-]+|https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+)\\s*)+$");

    /** The whole multi-line CHECK, including branches the pattern alone misses. */
    private static boolean satisfiesSliderCheck(String value) {
        return value == null
                || value.isBlank()
                || IMAGE_URLS.matcher(value).matches();
    }

    /** The whole single-value CHECK. */
    private static boolean satisfiesCoverCheck(String value) {
        return value == null
                || value.isBlank()
                || IMAGE_URL.matcher(value).matches();
    }

    @Test
    void acceptsASinglePath() {
        assertEquals(true, satisfiesSliderCheck("/images/gallery/pongal.jpg"));
    }

    /** The regression: two or more photos is the ordinary upload case. */
    @Test
    void acceptsTwoPathsOnSeparateLines() {
        assertEquals(true,
                satisfiesSliderCheck("/images/gallery/upload-1/1.jpg\n/images/gallery/upload-1/2.jpg"));
    }

    @Test
    void acceptsManyPathsOnSeparateLines() {
        assertEquals(true, satisfiesSliderCheck(
                "/images/gallery/u/1.jpg\n/images/gallery/u/2.jpg\n"
                        + "/images/gallery/u/3.jpg\n/images/gallery/u/4.jpg\n"
                        + "/images/gallery/u/5.jpg"));
    }

    @Test
    void acceptsCarriageReturnLineEndings() {
        assertEquals(true,
                satisfiesSliderCheck("/images/gallery/u/1.jpg\r\n/images/gallery/u/2.jpg"));
    }

    @Test
    void acceptsATrailingNewline() {
        assertEquals(true, satisfiesSliderCheck("/images/gallery/u/1.jpg\n"));
    }

    @Test
    void acceptsNullAndBlankBecauseTheCheckShortCircuitsFirst() {
        assertEquals(true, satisfiesSliderCheck(null));
        assertEquals(true, satisfiesSliderCheck(""));
    }

    @Test
    void acceptsAnHttpsBucketUrl() {
        assertEquals(true, satisfiesSliderCheck("https://pub-x.r2.dev/gallery/a-1/1.jpg"));
    }

    @Test
    void acceptsAnHttpsS3Url() {
        assertEquals(true,
                satisfiesSliderCheck("https://bucket.s3.us-east-1.amazonaws.com/gallery/a-1/1.jpg"));
    }

    @Test
    void acceptsTwoHttpsBucketUrlsOnSeparateLines() {
        assertEquals(true, satisfiesSliderCheck(
                "https://cdn.example.com/gallery/a-1/1.jpg\nhttps://cdn.example.com/gallery/a-1/2.jpg"));
    }

    /** A cover path goes through the single-value CHECK, which has no newline support. */
    @Test
    void coverCheckAcceptsEitherFormButNotAMultiLineValue() {
        assertEquals(true, satisfiesCoverCheck("/images/gallery/a/1.jpg"));
        assertEquals(true, satisfiesCoverCheck("https://cdn.example.com/gallery/a-1/1.jpg"));
        assertEquals(false, satisfiesCoverCheck(
                "https://cdn.example.com/gallery/a-1/1.jpg\nhttps://cdn.example.com/gallery/a-1/2.jpg"));
    }

    @Test
    void rejectsAnHttpUrlBecauseHttpsOnly() {
        assertEquals(false, satisfiesSliderCheck("http://cdn.example.com/gallery/a-1/1.jpg"));
    }

    @Test
    void rejectsADataUri() {
        assertEquals(false, satisfiesSliderCheck("data:image/png;base64,iVBORw0KGgo="));
    }

    @Test
    void rejectsAJavascriptUri() {
        assertEquals(false, satisfiesSliderCheck("javascript:alert(1)//gallery/a.jpg"));
    }

    @Test
    void rejectsAnHttpsHostWithNoObjectPath() {
        assertEquals(false, satisfiesSliderCheck("https://cdn.example.com"));
    }

    @Test
    void rejectsAnAbsoluteUrl() {
        assertEquals(false, satisfiesSliderCheck("https://evil.example/x.jpg?a=1"));
    }

    @Test
    void rejectsAnAbsoluteFilesystemPath() {
        assertEquals(false, satisfiesSliderCheck("/var/www/html/x.jpg"));
    }

    @Test
    void rejectsAPathContainingASpace() {
        assertEquals(false, satisfiesSliderCheck("/images/gallery/my photo.jpg"));
    }

    @Test
    void rejectsOneBadLineAmongGoodOnes() {
        assertEquals(false,
                satisfiesSliderCheck("/images/gallery/u/1.jpg\nnot-a-path.jpg"));
    }

    @Test
    void rejectsTrailingGarbageAfterAPath() {
        assertEquals(false, satisfiesSliderCheck("/images/gallery/u/1.jpg\nrm -rf /"));
    }

    /**
     * The pattern is the join of exactly what the entity stores, so a round
     * trip through setSlides/getSlides must keep every line valid. This is the
     * link between the DB constraint and the code that writes the column.
     */
    @Test
    void everyPathSetSlidesProducesSatisfiesTheCheck() {
        var event = new org.sstamilschool.model.SstsGalleryEvent();
        List<String> slides = List.of(
                "/images/gallery/pongal-3/1.jpg",
                "/images/gallery/pongal-3/2.jpg",
                "/images/gallery/pongal-3/3.png");
        event.setSlides(slides);
        assertEquals(true, satisfiesSliderCheck(event.getImageUrls()));
        assertEquals(slides, event.getSlides());
    }
}
