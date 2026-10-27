package app.morphe.extension.syncforreddit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

/**
 * Opens Reddit's newer /r/{subreddit}/s/{share-id} post links through Sync's
 * normal post route. Inspired by the ReVanced/Patcheddit Fix /s/ links patch.
 *
 * Only shortened Reddit links are intercepted. Ordinary post URLs and other
 * websites remain handled by Sync. Network work never runs on the UI thread.
 */
public final class RedditPostLinksExtension {
    private static volatile Context applicationContext;
    private static volatile String accessToken;
    private static final int TIMEOUT_MS = 2500;

    private RedditPostLinksExtension() {}

    public static void initialize(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
    }

    public static void setAccessToken(String token) {
        if (token == null || token.isEmpty()) return;
        String cleaned = token.trim();
        if (cleaned.regionMatches(true, 0, "bearer ", 0, 7)) {
            cleaned = cleaned.substring(7).trim();
        }
        if (!cleaned.isEmpty()) accessToken = cleaned;
    }

    /** Return true only when we are taking over a Reddit /s/ share link. */
    public static boolean resolveShareLink(String link) {
        if (link == null) return false;
        final URI original;
        try {
            original = new URI(link);
        } catch (Exception ignored) {
            return false;
        }

        if (!isShareLink(original)) return false;
        final Context context = applicationContext;
        if (context == null) return false;

        Thread worker = new Thread(() -> {
            String target = resolveRedirect(original);
            if (target != null) {
                openInsideSync(context, target, link);
            } else {
                openInAppBrowser(context, link);
            }
        }, "sync-reddit-share-link");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    private static boolean isRedditHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("reddit.com") || lower.endsWith(".reddit.com");
    }

    private static boolean isShareLink(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null
                && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                && isRedditHost(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().matches("(?i)^/r/[^/]+/s/[^/]+/?$");
    }

    private static boolean isPostLink(URI uri) {
        String path = uri.getPath();
        return isRedditHost(uri.getHost()) && path != null
                && (path.matches("(?i)^/r/[^/]+/comments/[^/]+(?:/.*)?$")
                    || path.matches("(?i)^/comments/[^/]+(?:/.*)?$"));
    }

    /** Follow only Reddit-controlled redirects, never an arbitrary external destination. */
    private static String resolveRedirect(URI original) {
        URI current = original;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (!isRedditHost(current.getHost())) return null;

            HttpURLConnection connection = null;
            try {
                URL url = current.toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                String token = accessToken;
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }

                int status = connection.getResponseCode();
                String location = connection.getHeaderField("Location");
                if (status < 300 || status >= 400 || location == null) {
                    return isPostLink(current) ? current.toString() : null;
                }
                current = current.resolve(location);
                if (isPostLink(current)) return current.toString();
            } catch (Exception ignored) {
                return null;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return null;
    }

    private static void openInsideSync(Context context, String url, String fallback) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            openInAppBrowser(context, fallback);
        }
    }

    /** If Reddit won't resolve the short link, show it in a browser instead of a blank post. */
    private static void openInAppBrowser(Context context, String url) {
        try {
            Intent intent = new Intent();
            intent.setClassName(
                    context.getPackageName(),
                    "com.laurencedawson.reddit_sync.ui.activities.WebViewActivity");
            intent.putExtra("url", url);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignoredAgain) {
                // Do not crash Sync when the device has no suitable browser.
            }
        }
    }
}
