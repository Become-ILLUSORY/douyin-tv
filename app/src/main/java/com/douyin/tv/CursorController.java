package com.douyin.tv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.WebView;

/**
 * D-pad remote control → virtual cursor / video navigation.
 *
 * 光标模式 (菜单键切换):
 *   方向键 → 移动光标
 *   OK     → 左键 / 长按右键
 *
 * 默认模式:
 *   上/下  → 切换视频 (上一个/下一个)
 *   OK     → 暂停/继续播放
 *   左/右  → 进度快退/快进 (由WebView焦点处理)
 */
public class CursorController {

    private boolean cursorMode = false;
    private float cursorX = 960f;
    private float cursorY = 540f;

    private static final int STEP_NORMAL = 60;
    private static final int STEP_SLOW = 15;
    private int currentStep = STEP_NORMAL;

    // Long-press for right-click in cursor mode
    private boolean okPressed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long LONG_PRESS_MS = 500;
    private final Runnable longPressRunnable = () -> {
        if (okPressed) {
            okPressed = false;
            performRightClick();
        }
    };

    private static final int SCROLL_AMOUNT = 400;

    private final Activity activity;
    private final WebView webView;

    public interface OnCursorModeChangedListener {
        void onCursorModeChanged(boolean enabled);
    }

    private OnCursorModeChangedListener listener;

    public CursorController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    public void setOnCursorModeChangedListener(OnCursorModeChangedListener l) {
        this.listener = l;
    }

    public boolean isCursorMode() {
        return cursorMode;
    }

    // ==================== KEY HANDLING ====================

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // MENU → toggle cursor mode
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_GUIDE) {
            toggleCursorMode();
            return true;
        }

        // ---------- CURSOR MODE ----------
        if (cursorMode) {
            return handleCursorKeyDown(keyCode, event);
        }

        // ---------- DEFAULT MODE (video navigation) ----------
        return handleDefaultKeyDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Cursor mode: handle OK release (click vs long-press)
        if (cursorMode) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                if (okPressed) {
                    okPressed = false;
                    handler.removeCallbacks(longPressRunnable);
                    performLeftClick();
                }
                return true;
            }
            return false;
        }

        // Default mode: OK release → toggle play/pause
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            togglePlayPause();
            return true;
        }

        return false;
    }

    // ==================== CURSOR MODE KEY HANDLING ====================

    private boolean handleCursorKeyDown(int keyCode, KeyEvent event) {
        boolean shift = event.isLongPress();
        currentStep = shift ? STEP_SLOW : STEP_NORMAL;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveCursor(-currentStep, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveCursor(currentStep, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                moveCursor(0, -currentStep);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveCursor(0, currentStep);
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (!okPressed) {
                    okPressed = true;
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                return true;

            case KeyEvent.KEYCODE_PAGE_UP:
                webView.scrollBy(0, -SCROLL_AMOUNT);
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                webView.scrollBy(0, SCROLL_AMOUNT);
                return true;

            case KeyEvent.KEYCODE_BACK:
                toggleCursorMode();
                return true;

            default:
                return false;
        }
    }

    // ==================== DEFAULT MODE: VIDEO NAVIGATION ====================

    private boolean handleDefaultKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                // Switch to previous video
                navigateVideo("prev");
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Switch to next video
                navigateVideo("next");
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Let WebView handle (may focus on rewind button or do nothing)
                return false;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Let WebView handle (may focus on forward button or do nothing)
                return false;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                // Handled in onKeyUp → togglePlayPause()
                return true;

            default:
                return false;
        }
    }

    /**
     * Navigate to prev/next video.
     * Works on Douyin PC recommend/jingxuan pages where videos are in a scrollable list.
     */
    private void navigateVideo(String direction) {
        String js =
            "(function() {" +
            "  var dir = '" + direction + "';" +
            // Strategy 1: Douyin PC single-video pages (scroll to sibling)
            "  var cards = document.querySelectorAll('[class*=\"video-card\"], [class*=\"videoCard\"], [class*=\"feed-card\"], [class*=\"feedCard\"], [class*=\"waterfall\"] > div, [class*=\"list\"] > div > div');" +
            "  if (cards.length < 2) {" +
            // Fallback: try generic list items in main content area
            "    cards = document.querySelectorAll('main li, [class*=\"content\"] li, [class*=\"main\"] > div > div > div');" +
            "  }" +
            "  if (cards.length < 2) {" +
            // Last resort: scroll by viewport height
            "    window.scrollBy(0, dir === 'next' ? window.innerHeight * 0.85 : -window.innerHeight * 0.85);" +
            "    return;" +
            "  }" +
            // Find which card is most "visible" (closest to viewport center)
            "  var mid = window.innerHeight / 2;" +
            "  var bestIdx = 0;" +
            "  var bestDist = Infinity;" +
            "  cards.forEach(function(c, i) {" +
            "    var r = c.getBoundingClientRect();" +
            "    var center = r.top + r.height / 2;" +
            "    var dist = Math.abs(center - mid);" +
            "    if (dist < bestDist) { bestDist = dist; bestIdx = i; }" +
            "  });" +
            // Scroll to prev/next card
            "  var targetIdx = dir === 'next' ? Math.min(bestIdx + 1, cards.length - 1) : Math.max(bestIdx - 1, 0);" +
            "  if (targetIdx !== bestIdx) {" +
            "    cards[targetIdx].scrollIntoView({ behavior: 'smooth', block: 'center' });" +
            "  } else {" +
            // At boundary, scroll by viewport height
            "    window.scrollBy(0, dir === 'next' ? window.innerHeight * 0.85 : -window.innerHeight * 0.85);" +
            "  }" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    /**
     * Toggle video play/pause.
     * Dispatches click on the video element or its play/pause button.
     */
    private void togglePlayPause() {
        String js =
            "(function() {" +
            // Find the main video element (most visible one)
            "  var videos = document.querySelectorAll('video');" +
            "  if (!videos.length) return;" +
            "  var best = null;" +
            "  var bestArea = 0;" +
            "  videos.forEach(function(v) {" +
            "    var r = v.getBoundingClientRect();" +
            "    var vis = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0));" +
            "    var area = vis * Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));" +
            "    if (area > bestArea) { bestArea = area; best = v; }" +
            "  });" +
            "  if (!best) return;" +
            // Try clicking the video element itself (most sites toggle play on click)
            "  best.click();" +
            // Also try dispatching a more realistic click event
            "  var rect = best.getBoundingClientRect();" +
            "  var cx = rect.left + rect.width / 2;" +
            "  var cy = rect.top + rect.height / 2;" +
            "  ['mousedown', 'mouseup', 'click'].forEach(function(type) {" +
            "    best.dispatchEvent(new MouseEvent(type, {" +
            "      bubbles: true, cancelable: true, view: window," +
            "      clientX: cx, clientY: cy, button: 0" +
            "    }));" +
            "  });" +
            // Also try clicking play/pause overlay button if exists
            "  var btn = document.querySelector('[class*=\"play\"], [class*=\"Play\"], [class*=\"pause\"], [class*=\"Pause\"], [class*=\"xgplayer\"] button');" +
            "  if (btn) btn.click();" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ==================== CURSOR MODE: MOVEMENT & CLICKS ====================

    private void toggleCursorMode() {
        cursorMode = !cursorMode;
        if (cursorMode) {
            cursorX = webView.getWidth() / 2f;
            cursorY = webView.getHeight() / 2f;
        }
        updateCursorPosition();
        if (listener != null) {
            listener.onCursorModeChanged(cursorMode);
        }
    }

    private void moveCursor(float dx, float dy) {
        cursorX = Math.max(0, Math.min(webView.getWidth(), cursorX + dx));
        cursorY = Math.max(0, Math.min(webView.getHeight(), cursorY + dy));
        updateCursorPosition();
    }

    private void updateCursorPosition() {
        String js = String.format(
            "if(window.__tvCursor) window.__tvCursor.updatePosition(%f, %f, %b);",
            cursorX, cursorY, cursorMode
        );
        webView.evaluateJavascript(js, null);
    }

    private void performLeftClick() {
        String js = String.format(
            "if(window.__tvCursor) window.__tvCursor.clickAt(%f, %f, 'left');",
            cursorX, cursorY
        );
        webView.evaluateJavascript(js, null);
    }

    private void performRightClick() {
        String js = String.format(
            "if(window.__tvCursor) window.__tvCursor.clickAt(%f, %f, 'right');",
            cursorX, cursorY
        );
        webView.evaluateJavascript(js, null);
    }

    public float getCursorX() { return cursorX; }
    public float getCursorY() { return cursorY; }
}
