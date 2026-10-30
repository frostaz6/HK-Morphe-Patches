# HK Morphe Patches

Some Morphe patches for apps I use.

## Patches list

<!-- PATCHES_START -->
> **[v1.9.9](https://github.com/frostaz6/HK-Morphe-Patches/releases/tag/v1.9.9)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;41 patches total
<details>
<summary>📦 Niagara Launcher&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.16.34 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Apple WeatherKit](#apple-weatherkit) | Fetches weather from Apple WeatherKit in place of Niagara's weather service. Requires a WeatherKit key. | • Team ID<br>• Key ID<br>• Services ID<br>• Private key |

</details>

<details>
<summary>📦 Sync for Reddit&nbsp;&nbsp;•&nbsp;&nbsp;15 patches</summary>
<br>

**🎯 Supported versions:**

| v23.06.30-13:39 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Removes the startup warning that Sync for Reddit is shutting down and the Sync for Lemmy invitation. |  |
| [Fix "Other Discussions"](#fix-other-discussions) | Fixes the "Other Discussions" feature. |  |
| [Fix "Restore Comment"](#fix-restore-comment) | Fixes the "Restore Comment" feature (requires Sync Ultra) by fetching from an alternative API. Also includes UI changes to make this feature more accessible. |  |
| [Fix Image Post Selftext](#fix-image-post-selftext) | Fixes selftext in image posts to properly render Markdown. |  |
| [Fix Reddit Video Links](#fix-reddit-video-links) | Fixes reddit video links (reddit.com/link/.../video) so they open in the native video player. |  |
| [Fix Reddit galleries](#fix-reddit-galleries) | Repairs Reddit gallery metadata with a direct DEX hook; no fragile inline-smali injection. |  |
| [Fix Reddit shared post links](#fix-reddit-shared-post-links) | Resolves Reddit /r/.../s/... share links to real post URLs so links to other posts open inside Sync. |  |
| [Fix imgur links](#fix-imgur-links) | Shows public Imgur albums from embed and archived pages without an API key; retains direct image support. |  |
| [Fix inline images](#fix-inline-images) | Fixes images in text posts showing as plain links instead of rendering inline. Also adds captions below the images where applicable. |  |
| [Fix r/random](#fix-r-random) | Restores random subreddit functionality by fetching from an alternative API. |  |
| [Hacker News Feed](#hacker-news-feed) | Integrates Hacker News (news.ycombinator.com) into Reddit Sync as a custom feed. |  |
| [Predictive back gesture (Reddit Sync)](#predictive-back-gesture-reddit-sync) | Enables the predictive back gesture with fixes for Reddit Sync. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID (default included). | • OAuth client ID<br>• Redirect URI<br>• User agent<br>• Imgur client ID |
| [Choose image upload host](#upload-comment-images-to-catbox) | Choose Reddit-hosted or Catbox uploads for Sync comment images. Catbox remains the default. |  |
| [Unlock Sync Ultra](#unlock-sync-ultra) | Unlocks Sync Ultra Lifetime. |  |

</details>

<details>
<summary>📦 Sync for Reddit Pro&nbsp;&nbsp;•&nbsp;&nbsp;13 patches</summary>
<br>

**🎯 Supported versions:**

| v23.06.30-13:39 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Removes the startup warning that Sync for Reddit is shutting down and the Sync for Lemmy invitation. |  |
| [Fix "Other Discussions"](#fix-other-discussions) | Fixes the "Other Discussions" feature. |  |
| [Fix "Restore Comment"](#fix-restore-comment) | Fixes the "Restore Comment" feature (requires Sync Ultra) by fetching from an alternative API. Also includes UI changes to make this feature more accessible. |  |
| [Fix Image Post Selftext](#fix-image-post-selftext) | Fixes selftext in image posts to properly render Markdown. |  |
| [Fix Reddit Video Links](#fix-reddit-video-links) | Fixes reddit video links (reddit.com/link/.../video) so they open in the native video player. |  |
| [Fix Reddit shared post links](#fix-reddit-shared-post-links) | Resolves Reddit /r/.../s/... share links to real post URLs so links to other posts open inside Sync. |  |
| [Fix imgur links](#fix-imgur-links) | Fixes Imgur album/image links and uses the personal Imgur client ID configured in Spoof client for reads. |  |
| [Fix inline images](#fix-inline-images) | Fixes images in text posts showing as plain links instead of rendering inline. Also adds captions below the images where applicable. |  |
| [Fix r/random](#fix-r-random) | Restores random subreddit functionality by fetching from an alternative API. |  |
| [Hacker News Feed](#hacker-news-feed) | Integrates Hacker News (news.ycombinator.com) into Reddit Sync as a custom feed. |  |
| [Predictive back gesture (Reddit Sync)](#predictive-back-gesture-reddit-sync) | Enables the predictive back gesture with fixes for Reddit Sync. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID (default included). | • OAuth client ID<br>• Redirect URI<br>• User agent<br>• Imgur client ID |
| [Unlock Sync Ultra](#unlock-sync-ultra) | Unlocks Sync Ultra Lifetime. |  |

</details>

<details>
<summary>📦 Sync for Reddit Dev&nbsp;&nbsp;•&nbsp;&nbsp;13 patches</summary>
<br>

**🎯 Supported versions:**

| v23.06.30-13:39 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Removes the startup warning that Sync for Reddit is shutting down and the Sync for Lemmy invitation. |  |
| [Fix "Other Discussions"](#fix-other-discussions) | Fixes the "Other Discussions" feature. |  |
| [Fix "Restore Comment"](#fix-restore-comment) | Fixes the "Restore Comment" feature (requires Sync Ultra) by fetching from an alternative API. Also includes UI changes to make this feature more accessible. |  |
| [Fix Image Post Selftext](#fix-image-post-selftext) | Fixes selftext in image posts to properly render Markdown. |  |
| [Fix Reddit Video Links](#fix-reddit-video-links) | Fixes reddit video links (reddit.com/link/.../video) so they open in the native video player. |  |
| [Fix Reddit shared post links](#fix-reddit-shared-post-links) | Resolves Reddit /r/.../s/... share links to real post URLs so links to other posts open inside Sync. |  |
| [Fix imgur links](#fix-imgur-links) | Fixes Imgur album/image links and uses the personal Imgur client ID configured in Spoof client for reads. |  |
| [Fix inline images](#fix-inline-images) | Fixes images in text posts showing as plain links instead of rendering inline. Also adds captions below the images where applicable. |  |
| [Fix r/random](#fix-r-random) | Restores random subreddit functionality by fetching from an alternative API. |  |
| [Hacker News Feed](#hacker-news-feed) | Integrates Hacker News (news.ycombinator.com) into Reddit Sync as a custom feed. |  |
| [Predictive back gesture (Reddit Sync)](#predictive-back-gesture-reddit-sync) | Enables the predictive back gesture with fixes for Reddit Sync. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID (default included). | • OAuth client ID<br>• Redirect URI<br>• User agent<br>• Imgur client ID |
| [Unlock Sync Ultra](#unlock-sync-ultra) | Unlocks Sync Ultra Lifetime. |  |

</details>

<details>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Predictive back gesture](#predictive-back-gesture) | Enables the predictive back gesture introduced in Android 13. |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=frostaz6/HK-Morphe-Patches

Or manually add this repository url as a patch source in Morphe: https://github.com/frostaz6/HK-Morphe-Patches

### Fixing images that fail to upload in Sync comments

Sync uploads pictures attached to comments and posts via Imgur. Its built-in shared
Imgur client ID can run out of upload quota, resulting in **Failed to upload image**.

The **Spoof client** patch now has a separate **Imgur client ID** setting:

1. Sign in to Imgur and open https://api.imgur.com/oauth2/addclient.
2. Register an application using **OAuth 2 authorization without a callback URL**.
3. Copy the **Client ID** (not the Client Secret).
4. In Morphe, edit the **Spoof client** patch and paste it into **Imgur client ID**.
5. Repatch a clean Sync APK. Keep your existing Reddit OAuth client ID, redirect URI,
   and user agent unchanged.

The old shared ID remains the default for backwards compatibility but usually
cannot upload images. You must supply a personal Imgur client ID to fix uploads.
This patch is limited to Imgur uploads; it does not enable Reddit's native image
attachments in communities where those are unsupported.

### Contributing

Thank you for considering contributing to HK Morphe Patches.
You can find the contribution guidelines [here](CONTRIBUTING.md).

### Building

To build HK Morphe Patches,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## License

HK Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE)

### Imgur albums in Sync

**Fix imgur links** now uses the **Imgur client ID** from the **Spoof client**
patch for Imgur album and single-image API requests, as well as uploads.
Select both default-enabled patches and enter your own registered
Imgur client ID under **Spoof client** while patching the original Sync APK.
This is not your Reddit OAuth client ID. The old album credential is
preserved only as a backwards-compatible fallback when no personal
Imgur ID has been entered. Deleted albums or unsupported Reddit galleries
may still require separate fixes.

### Sync image uploads and Imgur albums (v1.9.4)

Enable **Upload comment images to Catbox** (default on), **Fix imgur links**,
and **Spoof client** when patching the original Sync APK.

- Uploads: Sync sends image data to [Catbox](https://catbox.moe/tools.php)
  anonymously, and receives an image URL that its existing comment editor
  inserts. This is **not** a Reddit-native image attachment. The link is
  public; anonymous Catbox uploads cannot be deleted from your account.
- Album viewing: Sync reads the public Imgur embed page and converts the
  images to the gallery JSON its viewer expects. When necessary it tries the
  public album page and then available Wayback Machine snapshots.
- **No Imgur API client ID is needed** for either of these functions. The
  legacy Imgur client ID option under Spoof client is now only for the separate
  single-image API lookup path.
- Deleted albums, age-restricted albums, blocks by either service, and Reddit
  native galleries may still fail. Repatch a clean Sync APK rather than
  reusing the already-patched APK.

### Reddit-native galleries (v1.9.5)

**Fix Reddit galleries** handles Reddit-hosted image galleries such as
`reddit.com/gallery/<post-id>` separately from Imgur. It normalizes
gallery post IDs when query strings or trailing slashes are present and
uses alternate `crosspost_parent_list`, `media_metadata`, and
`preview.images` structures if the older Sync parser cannot find
`gallery_data`. It does **not** alter Reddit OAuth credentials or bypass
API rate limits or deleted/private posts.

This patch is compiled but needs **on-device testing** with a known
failing Reddit gallery. If Reddit denies access to the gallery API, no
local metadata parser can restore missing response data.

### Reddit-hosted image uploads (experimental, v1.9.9)

When patching a clean Sync APK, open the **Choose image upload host**
patch's options and set **Image upload host** to `reddit`. The default
`catbox` continues to work without Reddit upload support. The host is
chosen **when patching**, not from Sync's settings afterward. To change
it, patch and install the APK again.

The Reddit option requests an image upload lease at
`oauth.reddit.com/api/media/asset.json` using the OAuth bearer from
Sync's normal Reddit API requests. It then uploads a **single image**
to the signed Reddit storage endpoint, returning the URL to Sync's
existing comment-image link insertion. The OAuth token is kept only in
memory and never sent to the storage endpoint or Catbox.

**Limitations:** This is experimental and not yet tested on a live
Reddit account. Reddit may return HTTP 401/403 under a third-party
OAuth client. Reddit-hosted files can initially appear as Amazon S3
URLs before Reddit processes them; the resulting Markdown link is not
the same as Reddit's native embedded-comment image feature. Images
over 20 MB, multiple images in a single upload, and video uploads are
not implemented. An upload failure does not silently switch to Catbox.

The upload-lease strategy was informed by the GPLv3-licensed
[Apollo-Reborn native Reddit media uploader](https://github.com/Apollo-Reborn/Apollo-Reborn).
All previous patches, including **Fix Reddit galleries**, are retained.

### v1.9.9 — Fix Reddit gallery patcher failure

Fixes a Morphe fingerprint failure when patching Sync v23.06.30-13:39:
`Failed to match the fingerprint: ... name=buildUrl ...`. The builder is
located by its Reddit OAuth API endpoint string, and its patch is optional
when that method cannot be located. Gallery metadata normalization remains
active. Does not change the Reddit/Catbox upload host option.

Note: compilation alone does not prove an APK can be patched successfully.
Please test the complete patch set against a clean Sync APK.

### v1.9.9 — Reddit gallery response compatibility

Fixes another Morphe patch error on the original Sync APK: `Could not find Reddit gallery response data`. The patched gallery method may read a `byte[]` field with an obfuscated name, so the patch now matches the field's type. If the response bytes are accessed indirectly, an optional reflective runtime fallback is inserted instead of failing the entire patch. This should permit the patch operation to complete on more APK variants; live gallery behavior still requires testing.

### v1.9.9 — Fix Reddit galleries crash

Prevents Morphe's `Collection is empty` exception from its inline-smali compiler on Sync `v23.06.30-13:39`. The gallery parser is now hooked using a directly constructed DEX `invoke-static/range` instruction, with no inline-smali assembly and no hardcoded response-field names. The previously unverified optional gallery-URL rewrite has also been dropped to reduce fragility. When no suitable gallery parser is found, the patch leaves that method untouched rather than aborting all patches.

**Not a confirmed gallery-loading fix yet.** GitHub compilation does not execute the patcher on the real Sync APK. Install and gallery tests still need verification.

### Experimental comparison: Imgur JSON API rollback

A test-only branch restores the Imgur album handling present in **v1.9.3** and used by
[ReSync](https://github.com/YCDumpling/sync):
`https://api.imgur.com/3/album/<album-id>` with an Imgur `Client-ID` header and
Sync's original `data.images[]` JSON parser. The v1.9.4+ web HTML embed scraper
was removed from this branch. The existing *Spoof client* Imgur client-ID option
is retained, and the *Choose image upload host* patch is unchanged.

**Important:** This is a separate diagnostic experiment from the native Reddit
gallery rollback in PR #11. Imgur albums (`imgur.com/a/...`) and Reddit-hosted
posts (`reddit.com/gallery/...`) use different request classes. ReSync's
published source has a placeholder Imgur client ID; it does not prove the
Imgur API works without valid credentials. Compare a known Imgur album with
v1.9.3 and this branch before any release is prepared.
