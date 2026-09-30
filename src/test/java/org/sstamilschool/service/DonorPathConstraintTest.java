package org.sstamilschool.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Pins the regexes behind the DB CHECKs {@code chk_ssts_donors_photo} and
 * {@code chk_ssts_donors_website} in
 * sql/schema.sql. A CHECK constraint is invisible to the rest of the suite --
 * every repository here is a Mockito mock, so no SQL is ever built -- which
 * makes this Java transcription the only guard on it.
 *
 * <p><b>This is a transcription of the Postgres ARE pattern, not a test of the
 * database.</b> Keep it byte-identical to schema.sql.
 *
 * <p>Donor photos have the same two possible shapes as gallery photos: a
 * committed {@code /images/...} path for a file in the repo, or an
 * {@code https://} URL for an object in the S3/R2 bucket. The CHECK must accept
 * both and still refuse {@code http:}, {@code data:}, {@code javascript:} and
 * absolute filesystem paths, because the public home page renders this value
 * straight into an image {@code src}.
 */
class DonorPathConstraintTest {

    /**
     * chk_ssts_donors_photo. Single value, so unlike the gallery slider pattern
     * there is no repeating group and therefore no dollar-anchor placement trap.
     */
    private static final Pattern PHOTO_PATH =
            Pattern.compile("^/images/[A-Za-z0-9._/-]+$|^https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+$");

    /**
     * chk_ssts_donors_website. Deliberately DIFFERENT from the photo pattern:
     * the path segment is a {@code (...)*} group, so a bare host with no path
     * is accepted ("https://example.com"), whereas the photo pattern requires at
     * least one path segment because an image needs one to be fetchable.
     */
    private static final Pattern WEBSITE =
            Pattern.compile("^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._/-]+)*$");

    /** The whole CHECK, including the NULL/blank branch the pattern alone misses. */
    private static boolean satisfiesPhotoCheck(String value) {
        return value == null
                || value.isBlank()
                || PHOTO_PATH.matcher(value).matches();
    }

    /** The whole website CHECK, NULL/blank branch included. */
    private static boolean satisfiesWebsiteCheck(String value) {
        return value == null
                || value.isBlank()
                || WEBSITE.matcher(value).matches();
    }

    // --------------------------------------------------------------- accept

    @Test
    void acceptsACommittedRelativePath() {
        assertTrue(satisfiesPhotoCheck("/images/MadrasPhotStudios.jpg"));
        assertTrue(satisfiesPhotoCheck("/images/donors/nested/photo.png"));
    }

    @Test
    void acceptsAnHttpsBucketUrl() {
        assertTrue(satisfiesPhotoCheck("https://cdn.example.com/donors/acme-corp-7/logo.png"));
    }

    @Test
    void acceptsNullAndBlankBecauseTheColumnIsOptional() {
        assertTrue(satisfiesPhotoCheck(null));
        assertTrue(satisfiesPhotoCheck(""));
        assertTrue(satisfiesPhotoCheck("   "));
    }

    // --------------------------------------------------------------- reject

    @Test
    void rejectsInsecureSchemes() {
        // http:// is refused for the website URL for the same reason: these
        // values are rendered into a public page.
        assertFalse(satisfiesPhotoCheck("http://cdn.example.com/donors/a/logo.png"));
        assertFalse(satisfiesPhotoCheck("data:image/png;base64,AAAA"));
        assertFalse(satisfiesPhotoCheck("javascript:alert(1)"));
    }

    @Test
    void rejectsAbsoluteFilesystemPaths() {
        assertFalse(satisfiesPhotoCheck("/var/www/static/images/donors/a.png"));
        assertFalse(satisfiesPhotoCheck("file:///etc/passwd"));
    }

    @Test
    void rejectsValuesWithSpacesOrLeadingNoise() {
        assertFalse(satisfiesPhotoCheck("/images/donors/my photo.png"));
        assertFalse(satisfiesPhotoCheck("  /images/donors/a.png"));
        assertFalse(satisfiesPhotoCheck("prefix/images/donors/a.png"));
    }

    /**
     * The stored photo path is a single value, so a newline-separated list must
     * be refused. This is the structural difference from the gallery slider
     * column, which joins many values with a newline and needs the repeating
     * group; copying that pattern here would wrongly accept a two-line value.
     */
    @Test
    void rejectsAMultiLineValueBecauseOnlyOneLogoPerDonor() {
        assertFalse(satisfiesPhotoCheck("/images/donors/a.png\n/images/donors/b.png"));
    }

    @Test
    void rejectsAnHttpsUrlWithNoPathAfterTheHost() {
        assertFalse(satisfiesPhotoCheck("https://cdn.example.com"));
    }

    // -------------------------------------------------------------- website

    @Test
    void websiteAcceptsAHttpsUrlWithOrWithoutAPath() {
        assertTrue(satisfiesWebsiteCheck("https://example.com"));
        assertTrue(satisfiesWebsiteCheck("https://www.example.com/donate"));
        assertTrue(satisfiesWebsiteCheck("https://example.com/a/b/c"));
    }

    /**
     * The scheme-relative form is the reason this CHECK exists rather than a
     * naive "starts with http" test. "//evil.com" contains no scheme at all, so
     * it sails past a prefix check and becomes a live off-site link on the
     * public home page.
     */
    @Test
    void websiteRejectsSchemeRelativeAndInsecureValues() {
        assertFalse(satisfiesWebsiteCheck("//evil.com"));
        assertFalse(satisfiesWebsiteCheck("http://example.com"));
        assertFalse(satisfiesWebsiteCheck("javascript:alert(1)"));
        assertFalse(satisfiesWebsiteCheck("example.com"));
    }

    @Test
    void websiteRejectsAQueryStringBecauseThePatternHasNoQuestionMarkClass() {
        // Honest about current behaviour rather than aspirational: the pattern
        // admits only [A-Za-z0-9._/-] in the path, so ? and & are refused. If
        // that ever needs to change, schema.sql AND this case change together.
        assertFalse(satisfiesWebsiteCheck("https://example.com/donate?ref=ssts"));
    }
}
