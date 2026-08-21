package com.douyin.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity - fullscreen WebView loading Douyin web version.
 * Designed for Android TV boxes with D-pad remote control.
 */
public class MainActivity extends Activity {

    private WebView webView;
    private CursorController cursorController;
    private TextView statusBar;
    private View loadingOverlay;
    private Handler mainHandler;

    // Douyin web URLs
    private static final String DOUYIN_HOME = "https://www.douyin.com";
    private static final String DOUYIN_RECOMMEND = "https://www.douyin.com/recommend";
    private static final String USER_AGENT_PC = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive fullscreen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        statusBar = findViewById(R.id.status_bar);
        loadingOverlay = findViewById(R.id.loading_overlay);
        mainHandler = new Handler(Looper.getMainLooper());

        setupWebView();
        setupCursorController();

        // Load Douyin
        webView.loadUrl(DOUYIN_HOME);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Performance optimizations for low-end TV boxes
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        // Reduce memory usage
        settings.setBlockNetworkImage(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Force desktop mode (Douyin PC web is better for TV)
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Set PC user agent to get Douyin desktop version
        webView.getSettings().setUserAgentString(USER_AGENT_PC);

        // Enable mixed content (http resources on https page)
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Cookie manager - needed for login persistence
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Custom WebView client with resource blocking
        webView.setWebViewClient(new TvWebViewClient(this));

        // Minimal Chrome client
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 80) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }
        });

        // Add JavaScript interface for callbacks
        webView.addJavascriptInterface(new JavaScriptInterface(this), "Android");

        // Disable long-click to prevent text selection popup
        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);

        // Scroll bar hidden
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        // Disable overscroll glow effect
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void setupCursorController() {
        cursorController = new CursorController(this, webView);
        cursorController.setOnCursorModeChangedListener(enabled -> {
            statusBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
            if (enabled) {
                statusBar.setText("🖱️ 光标模式 [菜单键退出]");
            }
        });
    }

    // ==================== KEY EVENT HANDLING ====================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Let cursor controller handle if in cursor mode
        if (cursorController.onKeyDown(keyCode, event)) {
            return true;
        }

        // Global key handling (non-cursor mode)
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                // Double-tap back to exit
                return handleBackPress();

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_GUIDE:
                // Already handled by CursorController
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                // Let system handle volume
                return false;

            // D-pad in non-cursor mode: let WebView handle (it scrolls/navigates)
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // Pass to WebView for default focus navigation
                return super.onKeyDown(keyCode, event);

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (cursorController.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ==================== BACK PRESS HANDLING ====================

    private long lastBackPress = 0;

    private boolean handleBackPress() {
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
            // Exit app
            finish();
            return true;
        } else {
            lastBackPress = now;
            Toast.makeText(this, "再按一次返回退出", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    // ==================== PAGE CALLBACKS ====================

    public void onPageReady() {
        mainHandler.post(() -> {
            loadingOverlay.setVisibility(View.GONE);
            // Inject CSS after page is ready
            injectCss();
        });
    }

    public void onVideoPlaying() {
        // Video started playing - could adjust quality or show controls
    }

    private void injectCss() {
        try {
            String css = loadAssetMinified("inject.css");
            String js = "var s=document.createElement('style');s.textContent='" +
                    css.replace("'", "\\'") + "';document.head.appendChild(s);";
            webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            // Ignore
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    // ==================== IMMERSIVE MODE ====================

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    // ==================== HELPERS ====================

    private String loadAssetMinified(String name) {
        try {
            java.io.InputStream is = getAssets().open(name);
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String content = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
            // Simple minification
            content = content.replaceAll("/\\*.*?\\*/", "");
            content = content.replaceAll("//[^\n]*", "");
            content = content.replaceAll("\\s+", " ");
            return content.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
