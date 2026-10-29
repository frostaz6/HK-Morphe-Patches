package app.morphe.extension.syncforreddit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Catbox uploads for Sync's original UploadImgurRequest.
 *
 * The request itself still invokes Sync's usual listener with an image URL,
 * so comment Markdown and the rest of Sync's editor remain unmodified.
 * Other Volley requests are explicitly left untouched.
 */
public final class CatboxUploadExtension {
    private static final String ENDPOINT = "https://catbox.moe/user/api.php";
    private static final String BOUNDARY = "----SyncForRedditCatbox";
    private static final Pattern RESULT = Pattern.compile(
            "https://files\\.catbox\\.moe/[A-Za-z0-9._-]+");
    private static final int MAX_SIZE = 200 * 1024 * 1024;

    private CatboxUploadExtension() {}

    private static boolean isCatbox(Object request) {
        if (request == null) return false;
        try {
            Method urlMethod = request.getClass().getMethod("getUrl");
            return ENDPOINT.equals(urlMethod.invoke(request));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String requestContentType(Object request) {
        return isCatbox(request) ? "multipart/form-data; boundary=" + BOUNDARY : null;
    }

    public static byte[] requestBody(Object request) {
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

    /** Return Imgur-shaped JSON only when Catbox returned a valid HTTPS file URL. */
    public static byte[] wrapResponse(byte[] data) {
        if (data == null) return null;
        String result = new String(data, StandardCharsets.UTF_8).trim();
        if (!RESULT.matcher(result).matches()) return data;
        return ("{\"data\":{\"link\":\"" + result + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
