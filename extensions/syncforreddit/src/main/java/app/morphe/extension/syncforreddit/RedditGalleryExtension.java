package app.morphe.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Handles newer variants of Reddit's native gallery metadata without changing
 * networking, authentication, or the normal gallery parser's behavior.
 */
public final class RedditGalleryExtension {
    private static final Pattern POST_ID = Pattern.compile("^[a-z0-9]{5,10}$",
            Pattern.CASE_INSENSITIVE);
    private static final String PREFIX = "https://oauth.reddit.com/api/info.json?id=t3_";

    private RedditGalleryExtension() {}

    /**
     * LinkHandler.getCommentsId has trouble with query parameters on
     * reddit.com/gallery/<id>?... addresses; strip them using URI parsing.
     */
    public static String galleryApiUrl(String address) {
        if (address == null || address.isEmpty()) return null;
        try {
            if (POST_ID.matcher(address).matches()) {
                return PREFIX + address.toLowerCase(Locale.ROOT);
            }
            URI url = new URI(address);
            String path = url.getPath();
            if (path == null) return null;
            String[] parts = path.split("/");
            String id = null;
            for (int i = 0; i < parts.length - 1; i++) {
                if (("gallery".equalsIgnoreCase(parts[i]) ||
                        "comments".equalsIgnoreCase(parts[i])) && i + 1 < parts.length) {
                    id = parts[i + 1];
                    break;
                }
            }
            if (id != null && POST_ID.matcher(id).matches()) {
                return PREFIX + id.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // Leave URLs we do not understand to Sync's original builder.
        }
        return null;
    }

    /**
     * The old parser requires data.children[0].data.gallery_data.items and
     * media_metadata even though newer posts may store this on the crosspost
     * parent or carry only media_metadata / preview.images.
     */
    public static byte[] normalizeGallery(byte[] source) {
        if (source == null || source.length == 0) return source;
        try {
            JSONObject response = new JSONObject(new String(source, StandardCharsets.UTF_8));
            JSONObject data = response.optJSONObject("data");
            JSONArray children = data == null ? null : data.optJSONArray("children");
            JSONObject child = children == null ? null : children.optJSONObject(0);
            JSONObject post = child == null ? null : child.optJSONObject("data");
            if (post == null) return source;

            JSONObject meta = post.optJSONObject("media_metadata");
            JSONObject gallery = post.optJSONObject("gallery_data");

            JSONArray parentList = post.optJSONArray("crosspost_parent_list");
            if ((meta == null || gallery == null) && parentList != null) {
                JSONObject parent = parentList.optJSONObject(0);
                if (parent != null) {
                    if (meta == null) meta = parent.optJSONObject("media_metadata");
                    if (gallery == null) gallery = parent.optJSONObject("gallery_data");
                }
            }

            if (meta == null) meta = new JSONObject();

            if (gallery == null || gallery.optJSONArray("items") == null
                    || gallery.optJSONArray("items").length() == 0) {
                JSONArray items = new JSONArray();
                JSONArray keys = meta.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String id = keys.optString(i, "");
                        if (id.isEmpty()) continue;
                        JSONObject entry = new JSONObject();
                        entry.put("media_id", id);
                        items.put(entry);
                    }
                }
                gallery = new JSONObject();
                gallery.put("items", items);
            }

            JSONArray items = gallery.optJSONArray("items");
            if (items == null) return source;

            // Some posts expose preview images but don't populate media_metadata.
            JSONObject preview = post.optJSONObject("preview");
            JSONArray previews = preview == null ? null : preview.optJSONArray("images");
            if (meta.length() == 0 && previews != null && previews.length() > 0) {
                JSONArray previewItems = new JSONArray();
                for (int i = 0; i < previews.length(); i++) {
                    JSONObject image = previews.optJSONObject(i);
                    JSONObject original = image == null ? null : image.optJSONObject("source");
                    if (original == null) continue;
                    String link = original.optString("url", "").replace("&amp;", "&");
                    if (!link.startsWith("https://")) continue;
                    String id = "preview_" + i;
                    JSONObject sourceImage = new JSONObject();
                    sourceImage.put("u", link);
                    JSONObject media = new JSONObject();
                    media.put("s", sourceImage);
                    media.put("p", new JSONArray().put(sourceImage));
                    meta.put(id, media);
                    previewItems.put(new JSONObject().put("media_id", id));
                }
                if (previewItems.length() > 0) gallery.put("items", previewItems);
            }

            if (gallery.optJSONArray("items").length() == 0) return source;

            // Preserve the original metadata when it exists and only fill missing fields.
            post.put("media_metadata", meta);
            post.put("gallery_data", gallery);
            return response.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return source;
        }
    }
}
