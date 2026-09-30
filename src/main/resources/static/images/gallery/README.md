# Gallery images

Committed photo files live here and are served by Spring at `/images/...`, the
same convention as team photos (`ssts_users.avatar_url` →
`static/images/teachers/`).

Each event's photos go in a folder named `<title-slug>-<id>` — the layout
`GalleryImageStorageService` uses for the bucket, so a committed file and an
uploaded one live side by side. `pongal-celebration-1/` holds the four photos
for gallery event 1, "Pongal Celebration".

## Two ways to attach a photo

**1. Commit a file (what the seed uses).** Drop it in
`pongal-celebration-1/`, then set the entry's "Photo path" to the relative path
at `/superadmin/gallery`:

```
/images/gallery/pongal-celebration-1/1.png
```

**2. Upload from the admin form.** The form writes to the S3-compatible bucket
configured under `app.storage.*` and stores the public `https://` URL. This is
the only option that works on a container, because a container's disk is
discarded on every deploy — a file written to local disk is neither served nor
kept. Uploads are disabled (`enabled: false`) until a bucket is configured;
the form then explains that rather than failing.

A stored value may be **either** form. The DB CHECKs
`chk_ssts_gallery_events_image_url` / `_image_urls` accept a relative
`/images/...` path or an `https://` URL and reject `http:`, `data:`,
`javascript:`, query strings and absolute filesystem paths.

Leaving the path blank is valid: the public card falls back to the first letter
of the title on a gradient tile.
