package org.sstamilschool.dto;

/**
 * The public donor carousel's view of a donor: exactly the four fields a card
 * renders, and nothing else.
 *
 * <p>This exists because the carousel hands its data to a client-side script as
 * JSON. Serialising {@link org.sstamilschool.model.SstsDonor} directly would
 * publish {@code notes} (internal), {@code seedKey}, and the audit timestamps on
 * a public page, so the payload is built from this record instead.
 *
 * <p>Thymeleaf's JavaScript inlining escapes these values for the script context,
 * so a donor name containing markup or quotes cannot break out of the JSON.
 *
 * @param name        display name, always present
 * @param tagline     short descriptor, or null when the school left it blank
 * @param photoPath   a committed {@code /images/...} path or an {@code https://}
 *                    bucket URL, or null when the donor has no logo
 * @param websiteUrl  an {@code https://} link, or null. Guaranteed https-only by
 *                    chk_ssts_donors_website, which matters because the client
 *                    script writes it into an anchor's href
 */
public record DonorCard(String name, String tagline, String photoPath, String websiteUrl) {

    /** True when this donor has a logo, so the card shows an image not a tile. */
    public boolean hasPhoto() {
        return photoPath != null && !photoPath.isBlank();
    }

    /** True when this donor has a link, so the card shows the "Visit website" row. */
    public boolean hasWebsite() {
        return websiteUrl != null && !websiteUrl.isBlank();
    }
}
