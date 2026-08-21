package com.douyin.tv;

import android.webkit.JavascriptInterface;

/**
 * JavaScript bridge interface.
 * Methods here are called from injected JS running inside the WebView.
 */
public class JavaScriptInterface {

    private final MainActivity activity;

    public JavaScriptInterface(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void onPageReady() {
        activity.runOnUiThread(() -> activity.onPageReady());
    }

    @JavascriptInterface
    public void onVideoPlaying() {
        activity.runOnUiThread(() -> activity.onVideoPlaying());
    }

    @JavascriptInterface
    public void log(String msg) {
        android.util.Log.d("DouyinTV-JS", msg);
    }
}
