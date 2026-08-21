package com.douyin.tv;

import android.app.Activity;
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

    private static final String DOUYIN_HOME = "https://www.douyin.com/?recommend=1";
    private static final String USER_AGENT_PC = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on + fullscreen flags BEFORE setContentView
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        statusBar = findViewById(R.id.status_bar);
        loadingOverlay = findViewById(R.id.loading_overlay);
        mainHandler = new Handler(Looper.getMainLooper());

        setupWebView();
        setupCursorController();

        webView.loadUrl(DOUYIN_HOME);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Performance: cache + DOM storage
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        settings.setBlockNetworkImage(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Force PC user agent for Douyin desktop version
        settings.setUserAgentString(USER_AGENT_PC);

        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new TvWebViewClient(this));

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 50 && loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                if (newProgress >= 80) {
                    view.evaluateJavascript(
                        "document.querySelectorAll('video').forEach(function(v){v.muted=false;v.volume=1.0;});",
                        null
                    );
                }
            }
        });

        webView.addJavascriptInterface(new JavaScriptInterface(this), "Android");

        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void setupCursorController() {
        cursorController = new CursorController(this, webView);
        cursorController.setOnCursorModeChangedListener(enabled -> {
            if (statusBar != null) {
                statusBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
                if (enabled) {
                    statusBar.setText("光标模式 | 菜单键退出");
                }
            }
        });
    }

    // ==================== KEY EVENTS ====================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (cursorController != null && cursorController.onKeyDown(keyCode, event)) {
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return true;
                }
                return handleBackPress();

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return false;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (cursorController != null && cursorController.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ==================== BACK PRESS ====================

    private long lastBackPress = 0;

    private boolean handleBackPress() {
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 2000) {
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
        if (mainHandler == null) return;
        mainHandler.post(() -> {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            injectCss();
        });
    }

    public void onVideoPlaying() {}

    private void injectCss() {
        try {
            String css = loadAssetMinified("inject.css");
            String js = "var s=document.createElement('style');s.textContent='" +
                    css.replace("'", "\\'") + "';document.head.appendChild(s);";
            if (webView != null) webView.evaluateJavascript(js, null);
        } catch (Exception e) {}
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ==================== IMMERSIVE MODE ====================

    private void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setSystemUiVisibility(
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
            content = content.replaceAll("/\\*.*?\\*/", "");
            content = content.replaceAll("//[^\n]*", "");
            content = content.replaceAll("\\s+", " ");
            return content.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
