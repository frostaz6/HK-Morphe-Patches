package app.morphe.extension.syncforreddit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Configurable Catbox and Reddit-hosted uploads for Sync's original UploadImgurRequest.
 *
 * The request itself still invokes Sync's usual listener with an image URL,
 * so comment Markdown and the rest of Sync's editor remain unmodified.
 * Other Volley requests are explicitly left untouched.
 */
public final class CatboxUploadExtension {
    private static final String ENDPOINT = "https://catbox.moe/user/api.php";
    private static final String REDDIT_ENDPOINT = "https://oauth.reddit.com/api/media/asset.json";
    private static final String REDDIT_LEASE_BOUNDARY = "----SyncRedditMediaLease";
    private static final String REDDIT_STORAGE_BOUNDARY = "----SyncRedditMediaStorage";
    private static final Pattern S3_LOCATION = Pattern.compile("<Location>([^<]+)</Location>");
    private static final int MAX_REDDIT_IMAGE_BYTES = 20 * 1024 * 1024;
    // Captured from Sync's already-authenticated requests. Never persist or log.
    private static volatile String oauthBearer;

    private static final String BOUNDARY = "----SyncForRedditCatbox";
    private static final Pattern RESULT = Pattern.compile(
            "https://files\\.catbox\\.moe/[A-Za-z0-9._-]+");
    private static final int MAX_SIZE = 200 * 1024 * 1024;

    private CatboxUploadExtension() {}

    private static boolean isCatbox(Object request) {
        return ENDPOINT.equals(requestUrl(request));
    }

    private static boolean isReddit(Object request) {
        return REDDIT_ENDPOINT.equals(requestUrl(request));
    }

    private static String requestUrl(Object request) {
        if (request == null) return "";
        try {
            Method urlMethod = request.getClass().getMethod("getUrl");
            Object value = urlMethod.invoke(request);
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Called from Sync AccountSingleton.getAuthHeaders on real Reddit API requests. */
    public static void captureBearer(String token) {
        oauthBearer = token != null && !token.isEmpty() ? token : null;
    }

    /** Never forward Reddit OAuth credentials to Catbox or S3. */
    public static Map<String, String> uploadHeaders(Object request) {
        String token = oauthBearer;
        if (!isReddit(request) || token == null || token.isEmpty()) return Collections.emptyMap();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("User-Agent", "android:com.laurencedawson.reddit_sync:1.9.6");
        return headers;
    }

    public static String requestContentType(Object request) {
        if (isReddit(request)) return "multipart/form-data; boundary=" + REDDIT_LEASE_BOUNDARY;
        return isCatbox(request) ? "multipart/form-data; boundary=" + BOUNDARY : null;
    }

    public static byte[] requestBody(Object request) {
        if (isReddit(request)) return redditLeaseBody(request);
        if (!isCatbox(request)) return null;
        try {
            byte[] bytes = null;
            // Sync holds its selected image in a byte[] field on UploadImgurRequest.
            for (Field field : request.getClass().getDeclaredFields()) {
                if (field.getType() != byte[].class) continue;
                field.setAccessible(true);
                byte[] value = (byte[]) field.get(request);
                if (value != null && value.length > 0) {
                    bytes = value;
                    break;
                }
            }
            if (bytes == null || bytes.length > MAX_SIZE) return null;

            String type = "image/jpeg";
            String suffix = ".jpg";
            if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
                type = "image/png";
                suffix = ".png";
            } else if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I'
                    && bytes[2] == 'F') {
                type = "image/gif";
                suffix = ".gif";
            } else if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P') {
                type = "image/webp";
                suffix = ".webp";
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length + 512);
            write(out, "--" + BOUNDARY + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n");
            write(out, "fileupload\r\n");
            write(out, "--" + BOUNDARY + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"sync" + suffix + "\"\r\n");
            write(out, "Content-Type: " + type + "\r\n\r\n");
            out.write(bytes);
            write(out, "\r\n--" + BOUNDARY + "--\r\n");
            return out.toByteArray();
        } catch (Exception ignored) {
            // Falling through to Volley is preferable to crashing while patching.
            return null;
        }
    }

    private static void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Return an Imgur-shaped JSON URL after an upload (Catbox or Reddit). */
    public static byte[] wrapResponse(byte[] data, Object request) {
        if (isReddit(request)) return finishRedditUpload(data, request);
        if (data == null) return null;
        String result = new String(data, StandardCharsets.UTF_8).trim();
        if (!RESULT.matcher(result).matches()) return data;
        return ("{\"data\":{\"link\":\"" + result + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] imageBytes(Object request) throws Exception {
        if (request == null) return null;
        for (Field field : request.getClass().getDeclaredFields()) {
            if (field.getType() != byte[].class) continue;
            field.setAccessible(true);
            byte[] image = (byte[]) field.get(request);
            if (image != null && image.length > 0) return image;
        }
        return null;
    }

    private static String mimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 6) return "image/jpeg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return "image/jpeg";
    }

    private static String fileSuffix(String mime) {
        if ("image/png".equals(mime)) return ".png";
        if ("image/webp".equals(mime)) return ".webp";
        if ("image/gif".equals(mime)) return ".gif";
        return ".jpg";
    }

    /** Stage 1: POST filepath and mimetype so Reddit issues a signed S3 lease. */
    private static byte[] redditLeaseBody(Object request) {
        try {
            byte[] image = imageBytes(request);
            if (image == null || image.length > MAX_REDDIT_IMAGE_BYTES) return new byte[0];
            String mime = mimeType(image);
            String filename = "sync-upload" + fileSuffix(mime);
            ByteArrayOutputStream out = new ByteArrayOutputStream(512);
            appendField(out, REDDIT_LEASE_BOUNDARY, "filepath", filename);
            appendField(out, REDDIT_LEASE_BOUNDARY, "mimetype", mime);
            write(out, "--" + REDDIT_LEASE_BOUNDARY + "--\r\n");
            return out.toByteArray();
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static void appendField(ByteArrayOutputStream out, String boundary,
                                    String name, String value) throws IOException {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(out, value + "\r\n");
    }

    /**
     * Stage 2: use only Reddit's signed storage lease. Never send OAuth
     * Authorization or cookies to the storage server.
     */
    private static byte[] finishRedditUpload(byte[] leaseBytes, Object request) {
        if (leaseBytes == null) return null;
        try {
            byte[] image = imageBytes(request);
            if (image == null || image.length == 0 || image.length > MAX_REDDIT_IMAGE_BYTES) return leaseBytes;
            JSONObject lease = new JSONObject(new String(leaseBytes, StandardCharsets.UTF_8));
            JSONObject args = lease.optJSONObject("args");
            if (args == null) return leaseBytes;
            String action = args.optString("action", "");
            JSONArray fields = args.optJSONArray("fields");
            if (fields == null || fields.length() == 0 || action.isEmpty()) return leaseBytes;
            if (action.startsWith("//")) action = "https:" + action;

            URL uploadUrl = new URL(action);
            String hostname = uploadUrl.getHost().toLowerCase(Locale.ROOT);
            // A lease returned by Reddit cannot turn into an arbitrary exfiltration URL.
            if (!"https".equalsIgnoreCase(uploadUrl.getProtocol()) ||
                    !(hostname.equals("amazonaws.com") || hostname.endsWith(".amazonaws.com")))
                return leaseBytes;

            String mime = mimeType(image);
            ByteArrayOutputStream body = new ByteArrayOutputStream(image.length + 4096);
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field == null) continue;
                String key = field.optString("name", "");
                String value = field.optString("value", "");
                if (!key.matches("[a-zA-Z0-9_+.-]{1,80}")) return leaseBytes;
                appendField(body, REDDIT_STORAGE_BOUNDARY, key, value);
            }
            write(body, "--" + REDDIT_STORAGE_BOUNDARY + "\r\n");
            write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"sync-upload"
                    + fileSuffix(mime) + "\"\r\n");
            write(body, "Content-Type: " + mime + "\r\n\r\n");
            body.write(image);
            write(body, "\r\n--" + REDDIT_STORAGE_BOUNDARY + "--\r\n");

            HttpURLConnection conn = (HttpURLConnection) uploadUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + REDDIT_STORAGE_BOUNDARY);
            conn.setFixedLengthStreamingMode(body.size());
            try {
                try (OutputStream out = conn.getOutputStream()) {
                    body.writeTo(out);
                }
                int responseCode = conn.getResponseCode();
                if (responseCode != 200 && responseCode != 201) return leaseBytes;
                String xml;
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[16384];
                    ByteArrayOutputStream response = new ByteArrayOutputStream();
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (response.size() + n > 65536) return leaseBytes;
                        response.write(buffer, 0, n);
                    }
                    xml = response.toString("UTF-8");
                }
                java.util.regex.Matcher match = S3_LOCATION.matcher(xml);
                if (!match.find()) return leaseBytes;
                String link = match.group(1).replace("&amp;", "&");
                URL media = new URL(link);
                String mediaHost = media.getHost().toLowerCase(Locale.ROOT);
                if (!"https".equalsIgnoreCase(media.getProtocol()) ||
                        !(mediaHost.endsWith(".amazonaws.com") || mediaHost.endsWith(".redd.it")))
                    return leaseBytes;
                JSONObject result = new JSONObject();
                result.put("data", new JSONObject().put("link", link));
                return result.toString().getBytes(StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        } catch (Exception ignored) {
            // Returning the original lease is deliberately NOT a success response.
            // Sync's JSON parser will reject it so the user sees an upload failure.
            return leaseBytes;
        }
    }

}
