package com.douyin.tv;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Custom WebViewClient that blocks heavy/unnecessary network requests
 * to reduce memory and bandwidth usage on low-end TV boxes.
 */
public class TvWebViewClient extends WebViewClient {

    // Domains to completely block (analytics, tracking, monitoring)
    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>();

    // URL path patterns to block
    private static final Set<String> BLOCKED_PATHS = new HashSet<>();

    static {
        // Analytics & tracking domains
        BLOCKED_DOMAINS.add("mcs.snssdk.com");
        BLOCKED_DOMAINS.add("mcs.zijieapi.com");
        BLOCKED_DOMAINS.add("mon.zijieapi.com");
        BLOCKED_DOMAINS.add("mssdk.bytedance.com");
        BLOCKED_DOMAINS.add("security.zijieapi.com");
        BLOCKED_DOMAINS.add("vcs.zijieapi.com");
        BLOCKED_DOMAINS.add("privacy.zijieapi.com");
        BLOCKED_DOMAINS.add("lf-static.applogcdn.com");
        BLOCKED_DOMAINS.add("lf3-short.ibytedapm.com");
        BLOCKED_DOMAINS.add("tnc0-aliec2.zijieapi.com");
        BLOCKED_DOMAINS.add("frontier.zijieapi.com");
        BLOCKED_DOMAINS.add("log.snssdk.com");
        BLOCKED_DOMAINS.add("analytics.snssdk.com");
        BLOCKED_DOMAINS.add("ad.zijieapi.com");
        BLOCKED_DOMAINS.add("ads.snssdk.com");
        BLOCKED_DOMAINS.add("sf-tb-sg.ibytedtos.com");
        BLOCKED_DOMAINS.add("log.byteoversea.com");
        BLOCKED_DOMAINS.add("mon.snssdk.com");
        BLOCKED_DOMAINS.add("applog.ucdn.uc.cn");
        BLOCKED_DOMAINS.add("i.snssdk.com");

        // Path patterns to block (Sentry error reporting, etc.)
        BLOCKED_PATHS.add("/sentry");
        BLOCKED_PATHS.add("/slardar");
        BLOCKED_PATHS.add("/monitor");
        BLOCKED_PATHS.add("/analytics");
        BLOCKED_PATHS.add("/tracking");
    }

    private static final WebResourceResponse BLOCKED_RESPONSE = new WebResourceResponse(
            "text/plain",
            "utf-8",
            new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
    );

    private final android.content.Context context;

    public TvWebViewClient(android.content.Context context) {
        this.context = context;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString().toLowerCase();

        // Block by domain
        try {
            String host = request.getUrl().getHost();
            if (host != null && BLOCKED_DOMAINS.contains(host.toLowerCase())) {
                return BLOCKED_RESPONSE;
            }
        } catch (Exception ignored) {}

        // Block by path pattern
        for (String path : BLOCKED_PATHS) {
            if (url.contains(path)) {
                return BLOCKED_RESPONSE;
            }
        }

        // Block heavy sprite images (>300KB CSS background images from Douyin static CDN)
        // These are decorative sprites that waste memory on TV
        if (url.contains("douyinstatic.com") && url.endsWith(".png")) {
            // Let small images through, block large sprites
            // Note: we can't know size before downloading, but these are typically
            // 200-800KB sprite sheets. We'll use a heuristic:
            // Block if URL contains patterns that look like sprite hashes
            if (url.matches(".*[a-f0-9]{32}\\.png.*")) {
                return BLOCKED_RESPONSE;
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        // Inject scripts at page start for earliest possible resource blocking
        injectCoreScripts(view);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        // Re-inject after page load (in case DOM changed)
        view.evaluateJavascript(loadAsset("inject.js"), null);
        view.evaluateJavascript(
                "var s=document.createElement('style');" +
                "s.textContent='" + loadAssetMinified("inject.css") + "';" +
                "document.head.appendChild(s);",
                null
        );
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // Allow all SSL for TV box convenience (no cert pinning issues)
        handler.proceed();
    }

    private void injectCoreScripts(WebView view) {
        try {
            String js = loadAsset("inject.js");
            view.evaluateJavascript(js, null);
        } catch (Exception e) {
            android.util.Log.e("DouyinTV", "Failed to inject JS", e);
        }
    }

    private String loadAsset(String name) {
        try (java.io.InputStream is = context.getAssets().open(name)) {
            byte[] buf = new byte[is.available()];
            is.read(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String loadAssetMinified(String name) {
        return loadAsset(name)
                .replaceAll("/\\*.*?\\*/", "")  // remove block comments
                .replaceAll("//[^\n]*", "")      // remove line comments
                .replaceAll("\\s+", " ")         // collapse whitespace
                .trim();
    }
}
