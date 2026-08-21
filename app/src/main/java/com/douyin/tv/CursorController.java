package com.douyin.tv;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.WebView;

/**
 * D-pad remote → Douyin keyboard shortcuts.
 *
 * 默认模式:
 *   ↑/↓      → 翻页切换视频 (ArrowUp/ArrowDown)
 *   ←/→      → 快进快退 (ArrowLeft/ArrowRight, 长按时2倍速)
 *   OK单击    → 暂停/继续 (Space)
 *   OK双击    → 网页内全屏 (Y)
 *   菜单键    → 切换光标模式
 *
 * 光标模式:
 *   ↑/↓/←/→  → 移动鼠标光标
 *   OK单击    → 鼠标左键
 *   OK长按    → 鼠标右键
 */
public class CursorController {

    private boolean cursorMode = false;
    private float cursorX = 960f;
    private float cursorY = 540f;

    private static final int STEP_NORMAL = 60;
    private static final int STEP_SLOW = 15;

    // Long-press detection (cursor mode right-click)
    private boolean okDownTracked = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long LONG_PRESS_MS = 500;
    private final Runnable longPressRunnable = () -> {
        if (okDownTracked) {
            okDownTracked = false;
            performRightClick();
        }
    };

    // Double-click detection (default mode fullscreen)
    private long lastOkUpTime = 0;
    private static final long DOUBLE_CLICK_MS = 350;
    private boolean pendingSingle = false;
    private final Runnable singleClickRunnable = () -> {
        if (pendingSingle) {
            pendingSingle = false;
            sendKey(" "); // Space = pause/play
        }
    };

    // Long-press repeat for arrow keys (fast-forward/rewind 2x)
    private boolean arrowDown = false;
    private int arrowKeyCode = 0;
    private final Runnable arrowRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (arrowDown) {
                sendKeyCode(arrowKeyCode);
                handler.postDelayed(this, 200); // repeat every 200ms
            }
        }
    };

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

    // ==================== KEY EVENTS ====================

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // MENU → toggle cursor mode
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_GUIDE) {
            toggleCursorMode();
            return true;
        }

        if (cursorMode) return handleCursorDown(keyCode, event);
        return handleDefaultDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (cursorMode) return handleCursorUp(keyCode, event);
        return handleDefaultUp(keyCode, event);
    }

    // ==================== DEFAULT MODE ====================

    private boolean handleDefaultDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                // 切换上一个视频 → 发送 ArrowUp
                sendKeyCode(KeyEvent.KEYCODE_DPAD_UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // 切换下一个视频 → 发送 ArrowDown
                sendKeyCode(KeyEvent.KEYCODE_DPAD_DOWN);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 快退 → 发送 ArrowLeft，开始长按重复
                sendKeyCode(KeyEvent.KEYCODE_DPAD_LEFT);
                arrowDown = true;
                arrowKeyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                handler.removeCallbacks(arrowRepeatRunnable);
                handler.postDelayed(arrowRepeatRunnable, 500);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // 快进 → 发送 ArrowRight，开始长按重复
                sendKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT);
                arrowDown = true;
                arrowKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                handler.removeCallbacks(arrowRepeatRunnable);
                handler.postDelayed(arrowRepeatRunnable, 500);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                // 单击=暂停, 双击=全屏 → 在onKeyUp处理
                return true;
            default:
                return false;
        }
    }

    private boolean handleDefaultUp(int keyCode, KeyEvent event) {
        // Stop arrow repeat on release
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            arrowDown = false;
            handler.removeCallbacks(arrowRepeatRunnable);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            long now = System.currentTimeMillis();
            if (now - lastOkUpTime < DOUBLE_CLICK_MS) {
                // 双击 → 网页内全屏 (Y键)
                handler.removeCallbacks(singleClickRunnable);
                pendingSingle = false;
                lastOkUpTime = 0;
                sendKey("y");
            } else {
                // 可能单击 → 延迟确认
                lastOkUpTime = now;
                pendingSingle = true;
                handler.removeCallbacks(singleClickRunnable);
                handler.postDelayed(singleClickRunnable, DOUBLE_CLICK_MS);
            }
            return true;
        }
        return false;
    }

    // ==================== CURSOR MODE ====================

    private boolean handleCursorDown(int keyCode, KeyEvent event) {
        int step = event.isLongPress() ? STEP_SLOW : STEP_NORMAL;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  moveCursor(-step, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveCursor(step, 0); return true;
            case KeyEvent.KEYCODE_DPAD_UP:    moveCursor(0, -step); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  moveCursor(0, step); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (!okDownTracked) {
                    okDownTracked = true;
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:   webView.scrollBy(0, -400); return true;
            case KeyEvent.KEYCODE_PAGE_DOWN: webView.scrollBy(0, 400); return true;
            case KeyEvent.KEYCODE_BACK: toggleCursorMode(); return true;
            default: return false;
        }
    }

    private boolean handleCursorUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (okDownTracked) {
                okDownTracked = false;
                handler.removeCallbacks(longPressRunnable);
                performLeftClick();
            }
            return true;
        }
        return false;
    }

    // ==================== KEY SENDING ====================

    /** Send a keyboard character key to the WebView (for Space, Y, etc.) */
    private void sendKey(String keyChar) {
        String js = "(function(){" +
            "var e=new KeyboardEvent('keydown',{key:'" + keyChar + "',bubbles:true,cancelable:true});" +
            "document.dispatchEvent(e);" +
            "var e2=new KeyboardEvent('keyup',{key:'" + keyChar + "',bubbles:true,cancelable:true});" +
            "document.dispatchEvent(e2);" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    /** Send a DPAD key code to the WebView as a keyboard event */
    private void sendKeyCode(int keyCode) {
        String key;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    key = "ArrowUp"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  key = "ArrowDown"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT:  key = "ArrowLeft"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: key = "ArrowRight"; break;
            default: return;
        }
        String js = "(function(){" +
            "var e=new KeyboardEvent('keydown',{key:'" + key + "',keyCode:" + keyCode + ",bubbles:true,cancelable:true});" +
            "document.dispatchEvent(e);" +
            "document.activeElement.dispatchEvent(e);" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    // ==================== CURSOR ====================

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
