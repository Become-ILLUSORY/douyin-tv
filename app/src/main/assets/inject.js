/**
 * DouyinTV - Injected JS: virtual cursor + live stream blocking + unmute
 */
(function() {
    'use strict';
    if (window.__douyinTV) return;
    window.__douyinTV = true;

    // ==================== VIRTUAL CURSOR (lazy) ====================
    var cursor = null, crossH = null, crossV = null, cursorCreated = false;

    function ensureCursor() {
        if (cursorCreated) return;
        cursorCreated = true;
        cursor = document.createElement('div');
        cursor.id = '__tv-cursor';
        cursor.style.cssText = 'position:fixed;width:48px;height:48px;border-radius:50%;background:radial-gradient(circle,rgba(255,60,60,0.95) 0%,rgba(255,0,0,0.7) 50%,transparent 70%);border:4px solid #fff;box-shadow:0 0 0 2px rgba(255,0,0,0.8),0 0 20px 6px rgba(255,50,50,0.7),0 0 40px 12px rgba(255,0,0,0.3),inset 0 0 8px rgba(255,255,255,0.5);pointer-events:none;z-index:2147483647;transform:translate(-50%,-50%);transition:left 0.04s ease-out,top 0.04s ease-out;display:none;will-change:left,top';
        document.documentElement.appendChild(cursor);
        crossH = document.createElement('div');
        crossH.style.cssText = 'position:fixed;width:100vw;height:2px;background:linear-gradient(90deg,transparent,rgba(255,50,50,0.4),transparent);pointer-events:none;z-index:2147483646;display:none';
        crossV = document.createElement('div');
        crossV.style.cssText = 'position:fixed;width:2px;height:100vh;background:linear-gradient(180deg,transparent,rgba(255,50,50,0.4),transparent);pointer-events:none;z-index:2147483646;display:none';
        document.documentElement.appendChild(crossH);
        document.documentElement.appendChild(crossV);
    }

    function ripple(x, y, color) {
        var r = document.createElement('div');
        r.style.cssText = 'position:fixed;left:'+x+'px;top:'+y+'px;width:10px;height:10px;border-radius:50%;background:'+color+';transform:translate(-50%,-50%);pointer-events:none;z-index:2147483647;animation:__tvR 0.4s ease-out forwards';
        document.documentElement.appendChild(r);
        setTimeout(function(){ r.remove(); }, 500);
    }

    var s = document.createElement('style');
    s.textContent = '@keyframes __tvR{0%{width:10px;height:10px;opacity:1}100%{width:60px;height:60px;opacity:0}}';
    document.head.appendChild(s);

    window.__tvCursor = {
        updatePosition: function(x, y, visible) {
            ensureCursor();
            cursor.style.left = x + 'px';
            cursor.style.top = y + 'px';
            cursor.style.display = visible ? 'block' : 'none';
            crossH.style.top = y + 'px';
            crossH.style.display = visible ? 'block' : 'none';
            crossV.style.left = x + 'px';
            crossV.style.display = visible ? 'block' : 'none';
        },
        clickAt: function(x, y, button) {
            ripple(x, y, button === 'right' ? 'rgba(255,255,0,0.8)' : 'rgba(255,80,80,0.8)');
            var el = document.elementFromPoint(x, y);
            if (!el) return;
            if (button === 'right') {
                el.dispatchEvent(new MouseEvent('contextmenu', {bubbles:true,cancelable:true,clientX:x,clientY:y,button:2}));
            } else {
                ['pointerdown','mousedown'].forEach(function(t){
                    el.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0}));
                });
                ['pointerup','mouseup','click'].forEach(function(t){
                    el.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0}));
                });
            }
        }
    };

    // ==================== BLOCK LIVE STREAMS ====================
    function blockLiveStreams() {
        // Hide all live stream containers
        document.querySelectorAll('[class*="LivePlayer"],[class*="LiveLink"],[class*="time-live-tag"]').forEach(function(el) {
            // Walk up to find the feed item container
            var p = el;
            for (var i = 0; i < 5; i++) {
                p = p.parentElement;
                if (!p) break;
                var cls = p.className || '';
                // Stop at a reasonable container level
                if (cls.includes('feed') || cls.includes('card') || cls.includes('item') || cls.includes('waterfall') || cls.includes('list')) {
                    p.style.display = 'none';
                    return;
                }
            }
            // Fallback: hide the direct parent
            if (el.parentElement) el.parentElement.style.display = 'none';
        });
    }

    // ==================== UNMUTE VIDEOS ====================
    function unmuteVideos() {
        document.querySelectorAll('video').forEach(function(v) {
            if (v.muted) v.muted = false;
            if (v.volume < 0.5) v.volume = 1.0;
        });
    }

    // ==================== AUTO-NAVIGATE TO RECOMMEND (one-shot) ====================
    var recommendTried = false;
    function switchToRecommend() {
        if (recommendTried) return;
        if (location.pathname !== '/jingxuan') return;
        recommendTried = true;
        // Find and click "推荐" link
        var links = document.querySelectorAll('a');
        for (var i = 0; i < links.length; i++) {
            if (links[i].innerText?.trim() === '推荐') {
                links[i].click();
                return;
            }
        }
    }

    // ==================== HIDE LOGIN OVERLAY ====================
    function hideLoginOverlay() {
        // Don't remove the login button itself, just remove blocking overlays
        document.querySelectorAll('[class*="login-mask"],[class*="loginModal"],[class*="login-dialog"],[class*="login-guide"]').forEach(function(el) {
            if (el.style.position === 'fixed' || window.getComputedStyle(el).position === 'fixed') {
                el.style.display = 'none';
            }
        });
    }

    // Run all maintenance tasks periodically
    function maintain() {
        blockLiveStreams();
        unmuteVideos();
        hideLoginOverlay();
    }

    // MutationObserver for SPA navigation — only block live + unmute
    var observer = new MutationObserver(function() {
        maintain();
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
    setInterval(maintain, 2000);

    // Passive scroll
    document.addEventListener('scroll', function(){}, {passive:true});

    // ==================== PAGE READY ====================
    function signalReady() {
        try { if (window.Android) window.Android.onPageReady(); } catch(e) {}
    }
    if (document.readyState === 'complete') signalReady();
    else window.addEventListener('load', function() {
        signalReady();
        // Initial run after page load
        setTimeout(maintain, 1000);
        setTimeout(switchToRecommend, 2000);
    });
})();
