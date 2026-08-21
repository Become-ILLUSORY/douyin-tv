/**
 * DouyinTV - Injected JS: virtual cursor + video navigation + resource blocking
 */
(function() {
    'use strict';
    if (window.__douyinTV) return;
    window.__douyinTV = true;

    // ==================== VIRTUAL CURSOR ====================
    var cursor = document.createElement('div');
    cursor.id = '__tv-cursor';
    cursor.style.cssText = [
        'position:fixed', 'width:48px', 'height:48px', 'border-radius:50%',
        'background:radial-gradient(circle, rgba(255,60,60,0.95) 0%, rgba(255,0,0,0.7) 50%, transparent 70%)',
        'border:4px solid #fff',
        'box-shadow:0 0 0 2px rgba(255,0,0,0.8), 0 0 20px 6px rgba(255,50,50,0.7), 0 0 40px 12px rgba(255,0,0,0.3), inset 0 0 8px rgba(255,255,255,0.5)',
        'pointer-events:none', 'z-index:2147483647',
        'transform:translate(-50%,-50%)',
        'transition:left 0.04s ease-out, top 0.04s ease-out',
        'display:none', 'will-change:left, top'
    ].join(';');
    document.documentElement.appendChild(cursor);

    // Crosshair lines
    var crossH = document.createElement('div');
    crossH.style.cssText = 'position:fixed;width:100vw;height:2px;background:linear-gradient(90deg,transparent,rgba(255,50,50,0.4),transparent);pointer-events:none;z-index:2147483646;display:none';
    var crossV = document.createElement('div');
    crossV.style.cssText = 'position:fixed;width:2px;height:100vh;background:linear-gradient(180deg,transparent,rgba(255,50,50,0.4),transparent);pointer-events:none;z-index:2147483646;display:none';
    document.documentElement.appendChild(crossH);
    document.documentElement.appendChild(crossV);

    function ripple(x, y, color) {
        var r = document.createElement('div');
        r.style.cssText = 'position:fixed;left:'+x+'px;top:'+y+'px;width:10px;height:10px;border-radius:50%;background:'+color+';transform:translate(-50%,-50%);pointer-events:none;z-index:2147483647;animation:__tvR 0.4s ease-out forwards';
        document.documentElement.appendChild(r);
        setTimeout(function(){ r.remove(); }, 500);
    }

    var animStyle = document.createElement('style');
    animStyle.textContent = '@keyframes __tvR{0%{width:10px;height:10px;opacity:1}100%{width:60px;height:60px;opacity:0}}';
    document.head.appendChild(animStyle);

    window.__tvCursor = {
        updatePosition: function(x, y, visible) {
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

    // ==================== DOM CLEANUP ====================
    function cleanupDOM() {
        var sels = [
            '[class*="download"]','[class*="Download"]',
            '[class*="appBanner"]','[class*="app-promote"]',
            '[class*="openApp"]','[class*="open-app"]',
            '[class*="guide"]','[class*="Guide"]',
            '[class*="float"]','[class*="Float"]',
            '[class*="tooltip"]','[class*="Tooltip"]',
            '[class*="notification"]','[class*="Notification"]',
            '[class*="cookie"]','[class*="Cookie"]',
            '[class*="privacy"]','[class*="consent"]'
        ];
        sels.forEach(function(sel){
            try{
                document.querySelectorAll(sel).forEach(function(el){
                    var s = window.getComputedStyle(el);
                    if(s.position==='fixed'||s.position==='sticky') el.style.display='none';
                });
            }catch(e){}
        });
        document.querySelectorAll('*').forEach(function(el){
            try{
                var s = window.getComputedStyle(el);
                if((s.position==='fixed'||s.position==='sticky') && el.offsetHeight<200 && el.offsetWidth>window.innerWidth*0.8 && el.offsetHeight<100){
                    var t = el.innerText||'';
                    if(t.indexOf('下载')>=0||t.indexOf('打开')>=0||t.indexOf('APP')>=0||t.indexOf('Download')>=0||t.indexOf('浏览器')>=0)
                        el.style.display='none';
                }
            }catch(e){}
        });
    }
    setInterval(cleanupDOM, 3000);
    if(document.readyState==='complete') cleanupDOM();
    else window.addEventListener('load', cleanupDOM);

    // ==================== VIDEO HELPERS ====================
    // Expose a helper to auto-scroll to the best video (used by Java navigateVideo fallback)
    window.__tvVideoNav = function(dir) {
        var videos = document.querySelectorAll('video');
        if (!videos.length) {
            window.scrollBy(0, dir==='next' ? window.innerHeight*0.85 : -window.innerHeight*0.85);
            return;
        }
        var best = null, bestArea = 0;
        videos.forEach(function(v){
            var r = v.getBoundingClientRect();
            var vis = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0));
            var area = vis;
            if (area > bestArea) { bestArea = area; best = v; }
        });
        if (!best) return;
        // Find the closest scrollable parent or use window
        var parent = best.parentElement;
        while (parent && parent !== document.body) {
            var cs = window.getComputedStyle(parent);
            if (cs.overflow === 'auto' || cs.overflow === 'scroll' ||
                cs.overflowY === 'auto' || cs.overflowY === 'scroll') {
                if (dir === 'next') parent.scrollTop += window.innerHeight * 0.85;
                else parent.scrollTop -= window.innerHeight * 0.85;
                return;
            }
            parent = parent.parentElement;
        }
        window.scrollBy(0, dir==='next' ? window.innerHeight*0.85 : -window.innerHeight*0.85);
    };

    // ==================== VIDEO OPTIMIZATION ====================
    function optimizeVideos() {
        document.querySelectorAll('video').forEach(function(v){
            if(v.preload!=='none') v.preload='metadata';
            v.disablePictureInPicture = true;
        });
    }
    setInterval(optimizeVideos, 5000);

    // Passive scroll
    document.addEventListener('scroll', function(){}, {passive:true});

    // ==================== PAGE READY ====================
    function signalReady() {
        try { if(window.Android) window.Android.onPageReady(); } catch(e){}
    }
    if(document.readyState==='complete') signalReady();
    else window.addEventListener('load', signalReady);
})();
