package app.morphe.extension.syncforreddit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts public Imgur album/embed pages to the legacy album JSON shape
 * expected by Sync's ImgurGalleryRequest. No Imgur API key required.
 *
 * Adapted from the ImgurAlbum recovery strategy in Sync-Up (GPLv3).
 * Archived copies are a best-effort fallback for public albums.
 */
public final class ImgurAlbumWebExtension {
    private static final String IMAGES_MARKER = "var images";
    private static final String ARCHIVED_MARKER = "\"album_images\"";
    private static final Pattern POST_JSON = Pattern.compile(
            "postDataJSON\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern IMAGE_LINK = Pattern.compile(
            "https?://i\\.imgur\\.com/([A-Za-z0-9]{5,10})\\.(jpe?g|png|gifv?|webp|mp4)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALBUM_ID = Pattern.compile(
            "/a/([A-Za-z0-9]{4,16})", Pattern.CASE_INSENSITIVE);
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final int TIMEOUT_MS = 3000;
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36";

    private ImgurAlbumWebExtension() {}

    public static byte[] rewriteAlbum(byte[] response, Object request) {
        if (response == null) return null;
        try {
            String content = new String(response, StandardCharsets.UTF_8);
            if (content.trim().startsWith("{")) {
                JSONObject json = new JSONObject(content);
                if (json.optJSONObject("data") != null
                        && json.optJSONObject("data").optJSONArray("images") != null) {
                    return response;
                }
            }
            JSONArray images = parse(content);
            if (images.length() == 0) {
                String id = albumId(request);
                if (id != null) images = fromFallbacks(id);
            }
            if (images.length() == 0) return response;

            JSONObject body = new JSONObject();
            JSONObject data = new JSONObject();
            data.put("images", images);
            body.put("data", data);
            return body.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Sync will show its normal gallery error instead of crashing.
            return response;
        }
    }

    private static JSONArray parse(String html) {
        JSONArray images = new JSONArray();
        try {
            images = embedded(html, IMAGES_MARKER);
            if (images.length() != 0) return images;
        } catch (Exception ignored) {}
        try {
            images = embedded(html, ARCHIVED_MARKER);
            if (images.length() != 0) return images;
        } catch (Exception ignored) {}
        try {
            Matcher match = POST_JSON.matcher(html);
            if (match.find()) {
                String serialized = new JSONArray("[\"" + match.group(1) + "\"]").getString(0);
                JSONArray media = new JSONObject(serialized).optJSONArray("media");
                if (media != null) {
                    for (int i = 0; i < media.length(); i++) {
                        JSONObject entry = media.optJSONObject(i);
                        if (entry == null) continue;
                        String link = entry.optString("url", "");
                        if (link.isEmpty()) {
                            String id = entry.optString("id", "");
                            if (id.isEmpty()) continue;
                            String ext = entry.optString("ext", "jpg");
                            link = "https://i.imgur.com/" + id + (ext.startsWith(".") ? ext : "." + ext);
                        }
                        if (link.startsWith("//")) link = "https:" + link;
                        if (!link.startsWith("https://i.imgur.com/")) continue;
                        images.put(image(link, entry.optInt("width"), entry.optInt("height"),
                                entry.optString("name"), entry.optString("description")));
                    }
                    if (images.length() != 0) return images;
                }
            }
        } catch (Exception ignored) {}

        // Older archived pages can expose direct image URLs but not the JSON list.
        Set<String> seen = new HashSet<>();
        Matcher linked = IMAGE_LINK.matcher(html);
        while (linked.find() && images.length() < 100) {
            String url = "https://i.imgur.com/" + linked.group(1) + "." + linked.group(2);
            if (!seen.add(url)) continue;
            images.put(image(url, 0, 0, "", ""));
        }
        return images;
    }

    private static JSONArray embedded(String html, String marker) {
        int at = html.indexOf(marker);
        if (at < 0) return new JSONArray();
        int start = html.indexOf('{', at + marker.length());
        if (start < 0) return new JSONArray();
        String json = objectAt(html, start);
        if (json == null) return new JSONArray();

        JSONArray entries = new JSONObject(json).optJSONArray("images");
        JSONArray images = new JSONArray();
        if (entries == null) return images;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            String link = entry.optString("url", "");
            if (link.isEmpty()) link = entry.optString("link", "");
            if (link.isEmpty()) {
                String id = entry.optString("hash", "");
                if (id.isEmpty()) continue;
                String ext = entry.optString("ext", ".jpg");
                link = "https://i.imgur.com/" + id + (ext.startsWith(".") ? ext : "." + ext);
            }
            if (link.startsWith("//")) link = "https:" + link;
            if (!link.startsWith("https://i.imgur.com/")) continue;
            images.put(image(link, entry.optInt("width"), entry.optInt("height"),
                    entry.optString("title"), entry.optString("description")));
        }
        return images;
    }

    private static JSONObject image(String url, int width, int height,
                                    String title, String description) {
        JSONObject object = new JSONObject();
        try {
            object.put("link", url);
            if (url.endsWith(".mp4")) object.put("mp4", url);
            object.put("width", width);
            object.put("height", height);
            object.put("title", title);
            object.put("description", description);
        } catch (Exception ignored) {}
        return object;
    }

    private static String objectAt(String source, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '{') depth++;
                if (c == '}') {
                    depth--;
                    if (depth == 0) return source.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String albumId(Object request) {
        try {
            Method method = request.getClass().getMethod("getUrl");
            String url = (String) method.invoke(request);
            Matcher match = ALBUM_ID.matcher(url);
            return match.find() ? match.group(1) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONArray fromFallbacks(String id) {
        try {
            // If the embed changed its format, the album's public page may still
            // contain a parseable postDataJSON block.
            JSONArray parsed = parse(fetch("https://imgur.com/a/" + id));
            if (parsed.length() != 0) return parsed;
        } catch (Exception ignored) {}

        // CDX gives snapshot timestamps; try up to two older archived public pages.
        try {
            String cdx = "https://web.archive.org/cdx/search/cdx"
                    + "?url=imgur.com%2Fa%2F" + id
                    + "&output=json&filter=statuscode%3A200"
                    + "&fl=timestamp%2Coriginal&collapse=timestamp%3A4&limit=3";
            JSONArray entries = new JSONArray(fetch(cdx));
            for (int i = 1; i < entries.length() && i <= 2; i++) {
                JSONArray entry = entries.optJSONArray(i);
                if (entry == null || entry.length() < 1) continue;
                String timestamp = entry.optString(0, "");
                if (!timestamp.matches("[0-9]{14}")) continue;
                String snapshot = "https://web.archive.org/web/" + timestamp + "id_/https://imgur.com/a/" + id;
                try {
                    JSONArray recovered = parse(fetch(snapshot));
                    if (recovered.length() != 0) return recovered;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return new JSONArray();
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept", "text/html, application/json");
        try {
            if (conn.getResponseCode() != 200) return "";
            try (InputStream input = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (out.size() + count > MAX_BYTES) return "";
                    out.write(buffer, 0, count);
                }
                return out.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }
}
