package com.douyin.tv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * D-pad remote control handler.
 *
 * 默认模式:
 *   上/下  → 切换视频
 *   OK单击 → 暂停/继续
 *   OK双击 → 网页全屏播放
 *
 * 光标模式 (菜单键切换):
 *   方向键 → 移动光标
 *   OK     → 左键 / 长按右键
 */
public class CursorController {

    private boolean cursorMode = false;
    private float cursorX = 960f;
    private float cursorY = 540f;

    private static final int STEP_NORMAL = 60;
    private static final int STEP_SLOW = 15;
    private int currentStep = STEP_NORMAL;

    // Long-press for right-click (cursor mode)
    private boolean okPressed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long LONG_PRESS_MS = 500;
    private final Runnable longPressRunnable = () -> {
        if (okPressed) {
            okPressed = false;
            performRightClick();
        }
    };

    // Double-click for fullscreen (default mode)
    private long lastOkUpTime = 0;
    private static final long DOUBLE_CLICK_MS = 300;
    private boolean pendingSingleClick = false;
    private final Runnable singleClickRunnable = () -> {
        if (pendingSingleClick) {
            pendingSingleClick = false;
            togglePlayPause();
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

    public boolean isCursorMode() { return cursorMode; }

    // ==================== KEY HANDLING ====================

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_GUIDE) {
            toggleCursorMode();
            return true;
        }
        if (cursorMode) return handleCursorKeyDown(keyCode, event);
        return handleDefaultKeyDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
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

        // Default mode: OK release → double-click detection
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            long now = System.currentTimeMillis();
            if (now - lastOkUpTime < DOUBLE_CLICK_MS) {
                // Double click → fullscreen
                handler.removeCallbacks(singleClickRunnable);
                pendingSingleClick = false;
                lastOkUpTime = 0;
                toggleFullscreen();
            } else {
                // Possible single click → wait to confirm
                lastOkUpTime = now;
                pendingSingleClick = true;
                handler.removeCallbacks(singleClickRunnable);
                handler.postDelayed(singleClickRunnable, DOUBLE_CLICK_MS);
            }
            return true;
        }
        return false;
    }

    // ==================== CURSOR MODE ====================

    private boolean handleCursorKeyDown(int keyCode, KeyEvent event) {
        boolean shift = event.isLongPress();
        currentStep = shift ? STEP_SLOW : STEP_NORMAL;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  moveCursor(-currentStep, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveCursor(currentStep, 0); return true;
            case KeyEvent.KEYCODE_DPAD_UP:    moveCursor(0, -currentStep); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  moveCursor(0, currentStep); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (!okPressed) {
                    okPressed = true;
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:   webView.scrollBy(0, -SCROLL_AMOUNT); return true;
            case KeyEvent.KEYCODE_PAGE_DOWN: webView.scrollBy(0, SCROLL_AMOUNT); return true;
            case KeyEvent.KEYCODE_BACK: toggleCursorMode(); return true;
            default: return false;
        }
    }

    // ==================== DEFAULT MODE ====================

    private boolean handleDefaultKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:   navigateVideo("prev"); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: navigateVideo("next"); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                // Handled in onKeyUp (double-click detection)
                return true;
            default: return false;
        }
    }

    // ==================== VIDEO NAVIGATION ====================

    private void navigateVideo(String direction) {
        String js =
            "(function(){" +
            "var dir='" + direction + "';" +
            "var cards=document.querySelectorAll('[class*=\"video-card\"],[class*=\"videoCard\"],[class*=\"feed-card\"],[class*=\"feedCard\"],[class*=\"waterfall\"]>div,[class*=\"list\"]>div>div');" +
            "if(cards.length<2)cards=document.querySelectorAll('main li,[class*=\"content\"] li,[class*=\"main\"]>div>div>div');" +
            "if(cards.length<2){window.scrollBy(0,dir==='next'?window.innerHeight*0.85:-window.innerHeight*0.85);return;}" +
            "var mid=window.innerHeight/2,bestIdx=0,bestDist=Infinity;" +
            "cards.forEach(function(c,i){var r=c.getBoundingClientRect();var d=Math.abs(r.top+r.height/2-mid);if(d<bestDist){bestDist=d;bestIdx=i;}});" +
            "var t=dir==='next'?Math.min(bestIdx+1,cards.length-1):Math.max(bestIdx-1,0);" +
            "if(t!==bestIdx)cards[t].scrollIntoView({behavior:'smooth',block:'center'});" +
            "else window.scrollBy(0,dir==='next'?window.innerHeight*0.85:-window.innerHeight*0.85);" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ==================== PLAY/PAUSE ====================

    private void togglePlayPause() {
        String js =
            "(function(){" +
            "var vs=document.querySelectorAll('video');" +
            "if(!vs.length)return;" +
            "var best=null,ba=0;" +
            "vs.forEach(function(v){var r=v.getBoundingClientRect();var a=Math.max(0,Math.min(r.bottom,innerHeight)-Math.max(r.top,0));if(a>ba){ba=a;best=v;}});" +
            "if(!best)return;" +
            "best.click();" +
            "var r=best.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2;" +
            "['mousedown','mouseup','click'].forEach(function(t){best.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy,button:0}));});" +
            "var btn=document.querySelector('[class*=\"play\"],[class*=\"Play\"],[class*=\"pause\"],[class*=\"Pause\"] button');" +
            "if(btn)btn.click();" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ==================== FULLSCREEN ====================

    private void toggleFullscreen() {
        // Use JavaScript Fullscreen API on the video element
        String js =
            "(function(){" +
            "var v=document.querySelector('video');" +
            "if(!v)return;" +
            // Try to find the player container (xgplayer or similar)
            "var p=v.closest('[class*=\"player\"],[class*=\"xgplayer\"],[class*=\"video-container\"]')||v.parentElement;" +
            "if(!p)p=v;" +
            // Request fullscreen on the player container, fallback to document
            "if(p.requestFullscreen)p.requestFullscreen();" +
            "else if(p.webkitRequestFullscreen)p.webkitRequestFullscreen();" +
            "else if(p.msRequestFullscreen)p.msRequestFullscreen();" +
            "else if(v.requestFullscreen)v.requestFullscreen();" +
            "else if(v.webkitRequestFullscreen)v.webkitRequestFullscreen();" +
            "else{document.documentElement.webkitRequestFullscreen();}" +
            // Also unmute
            "v.muted=false;v.volume=1.0;" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ==================== CURSOR MODE ====================

    private void toggleCursorMode() {
        cursorMode = !cursorMode;
        if (cursorMode) {
            cursorX = webView.getWidth() / 2f;
            cursorY = webView.getHeight() / 2f;
        }
        updateCursorPosition();
        if (listener != null) listener.onCursorModeChanged(cursorMode);
    }

    private void moveCursor(float dx, float dy) {
        cursorX = Math.max(0, Math.min(webView.getWidth(), cursorX + dx));
        cursorY = Math.max(0, Math.min(webView.getHeight(), cursorY + dy));
        updateCursorPosition();
    }

    private void updateCursorPosition() {
        webView.evaluateJavascript(String.format(
            "if(window.__tvCursor)window.__tvCursor.updatePosition(%f,%f,%b);",
            cursorX, cursorY, cursorMode), null);
    }

    private void performLeftClick() {
        webView.evaluateJavascript(String.format(
            "if(window.__tvCursor)window.__tvCursor.clickAt(%f,%f,'left');",
            cursorX, cursorY), null);
    }

    private void performRightClick() {
        webView.evaluateJavascript(String.format(
            "if(window.__tvCursor)window.__tvCursor.clickAt(%f,%f,'right');",
            cursorX, cursorY), null);
    }
}
