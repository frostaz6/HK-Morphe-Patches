#!/usr/bin/env python3
"""ReSync 1.0.3: make token minting wait for Reddit origin + diagnose failures.
This is a test-only overlay; no Reddit credentials/cookies/tokens are logged.
"""
from pathlib import Path
import sys

source = Path(sys.argv[1])
code = source.read_text(encoding="utf-8")

def replace_once(old, new):
    global code
    assert code.count(old) == 1, f"Expected exactly one match: {old[:80]!r}"
    code = code.replace(old, new)

def replace_between(start, end, replacement):
    global code
    a = code.index(start)
    b = code.index(end, a + len(start))
    code = code[:a] + replacement + code[b:]

# Match the embedded Chromium version instead of claiming Chrome 120 on Android 13.
# A stale declared browser UA with a different underlying TLS/Chromium version
# may be challenged by Reddit. Keep the Android WebView version consistent.
replace_once(
    's.setUserAgentString("Mozilla/5.0 (Linux; Android 13; SM-G950F) AppleWebKit/537.36 "\n'
    '                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");',
    'String browserUa = s.getUserAgentString();\n'
    '        if (browserUa != null && !browserUa.isEmpty()) {\n'
    '            s.setUserAgentString(browserUa.replace("; wv", "").replace(" Version/4.0", ""));\n'
    '        }'
)

# Fetch only after a real Reddit HTML document is loaded and CSRF cookie exists.
# robots.txt is not an HTML document and is frequently still loading when the
# current code fires evaluateJavascript().
replace_once(
    'webView.loadUrl(anonymous ? "https://www.reddit.com/" : "https://www.reddit.com/robots.txt");',
    'webView.loadUrl("https://www.reddit.com/");'
)
replace_once(
    'waitForCsrfThenFetch(System.currentTimeMillis(), anonymous ? 15000L : 4000L);',
    'waitForCsrfThenFetch(System.currentTimeMillis(), 20000L);'
)

replace_between(
    '    /** Polls the cookie jar for ',
    '    /** Same-origin POST to the token endpoint,',
    '''    /** Wait for a completed Reddit document *and* the CSRF cookie before invoking JS. */
    private void waitForCsrfThenFetch(final long startMs, final long timeoutMs) {
        main.postDelayed(new Runnable() {
            @Override public void run() {
                String cookies = CookieManager.getInstance().getCookie("https://www.reddit.com");
                boolean haveCsrf = cookies != null && cookies.contains("csrf_token=");
                String loadedUrl = webView == null ? null : webView.getUrl();
                boolean redditPage = loadedUrl != null
                        && loadedUrl.startsWith("https://www.reddit.com/")
                        && webView.getProgress() == 100;
                if (haveCsrf && redditPage) {
                    runTokenFetch();
                } else if (System.currentTimeMillis() - startMs >= timeoutMs) {
                    String reason = !haveCsrf ? "Reddit CSRF cookie missing"
                            : !redditPage ? "Reddit token WebView page not ready"
                            : "Reddit token request timed out";
                    android.util.Log.i(TAG, "Token bootstrap: " + reason);
                    deliver(null, reason);
                } else {
                    main.postDelayed(this, 400);
                }
            }
        }, 600);
    }

'''
)

replace_between(
    '    /** Same-origin POST to the token endpoint,',
    '    /**\n     * Fetches the logged-in account',
    '''    /** Same-origin request; report only HTTP status and sanitized errors. */
    private void runTokenFetch() {
        String js =
            "(function(){try{"
          + "if(location.origin!=='https://www.reddit.com'){"
          + "SyncTokenBridge.onError('Unexpected Reddit page origin');return;}"
          + "var m=document.cookie.split(';').map(function(c){return c.trim();})"
          + ".find(function(c){return c.indexOf('csrf_token=')===0;});"
          + "var csrf=m?decodeURIComponent(m.substring('csrf_token='.length)):'';"
          + "if(!csrf){SyncTokenBridge.onError('CSRF cookie inaccessible in WebView');return;}"
          + "fetch('" + TOKEN_URL + "',{method:'POST',credentials:'include',"
          + "headers:{'Content-Type':'application/json','Accept':'application/json'},"
          + "body:JSON.stringify({csrf_token:csrf})})"
          + ".then(function(r){return r.text().then(function(t){"
          + "SyncTokenBridge.onResult(r.status,t);});})"
          + ".catch(function(e){SyncTokenBridge.onError('Network error');});"
          + "}catch(e){SyncTokenBridge.onError('WebView JavaScript error');}})();";
        webView.evaluateJavascript(js, null);
    }

    private void deliver(String jwt, String reason) {
        List<TokenCallback> toCall;
        synchronized (pending) {
            toCall = new ArrayList<>(pending);
            pending.clear();
            fetchInFlight = false;
        }
        String message = reason == null ? "token fetch failed" : reason;
        for (TokenCallback cb : toCall) {
            if (jwt != null) cb.onToken(jwt); else cb.onError(message);
        }
    }

    /** JS -> Java bridge; does not log/store response bodies or JWTs in diagnostics. */
    private final class Bridge {
        @JavascriptInterface
        public void onResult(int status, String responseBody) {
            String jwt = null;
            String error = null;
            if (status == 200 && responseBody != null) {
                try {
                    JSONObject o = new JSONObject(responseBody);
                    jwt = o.optString("token", null);
                    long expires = o.optLong("expires", 0L);
                    if (jwt != null && !jwt.isEmpty()) {
                        cachedToken = jwt;
                        cachedExpiryMs = expires > 0
                            ? expires : System.currentTimeMillis() + 23 * 60 * 60 * 1000L;
                        android.util.Log.i(TAG, "Web-session token obtained");
                    } else {
                        error = "Reddit HTTP 200 without token";
                    }
                } catch (Exception e) {
                    error = "Reddit token returned invalid JSON";
                }
            } else {
                error = "Reddit token endpoint HTTP " + status;
            }
            final String result = jwt;
            final String resultError = error;
            android.util.Log.i(TAG, "Token result: " + (result != null ? "success" : resultError));
            main.post(new Runnable() {
                @Override public void run() { deliver(result, resultError); }
            });
        }

        @JavascriptInterface
        public void onError(String reason) {
            final String safeReason = reason == null ? "WebView request failed"
                    : reason.length() > 90 ? reason.substring(0, 90) : reason;
            android.util.Log.i(TAG, "Token failure: " + safeReason);
            main.post(new Runnable() {
                @Override public void run() { deliver(null, safeReason); }
            });
        }
    }

'''
)

# Ensure the previous implementation is absent, and the safe diagnostics are present.
assert 'deliver(null);' not in code
assert 'SyncTokenBridge.onToken(' not in code
assert 'SyncTokenBridge.onResult(r.status,t)' in code
assert 'Reddit token endpoint HTTP ' in code
source.write_text(code, encoding="utf-8")
print("Applied ReSync token-loading and safe HTTP diagnostics overlay")
