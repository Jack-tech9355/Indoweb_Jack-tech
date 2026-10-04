package com.example.ui.scripts

import com.example.data.model.UserScript

object ScriptTemplates {

    val STORE_REPOSITORY_SCRIPTS: List<UserScript> = listOf(
        UserScript(
            id = "store-yt-skip-ads",
            name = "YouTube Auto-Skip Ads",
            description = "Automatically detects and clicks the 'Skip Ad' button as soon as it appears on YouTube.",
            urlPattern = "*youtube.com*",
            isEnabled = true,
            runAt = "document_start",
            author = "Indoweb Store",
            version = "3.2",
            isBuiltIn = false,
            scriptCode = """
                (function() {
                    function skipYouTubeAds() {
                        const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                        if (skipBtn) {
                            skipBtn.click();
                            console.log('[Indoweb] Auto-skipped YouTube Ad');
                        }
                        const adOverlay = document.querySelector('.ytp-ad-overlay-close-button');
                        if (adOverlay) adOverlay.click();
                        
                        const video = document.querySelector('video');
                        const ad = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                        if (ad && video && !isNaN(video.duration)) {
                            video.currentTime = video.duration;
                        }
                    }
                    setInterval(skipYouTubeAds, 600);
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "store-video-speed-booster",
            name = "Video Speed Controller & Booster",
            description = "Adds on-screen speed controls (0.75x, 1x, 1.25x, 1.5x, 2x, 3x) and volume booster for all HTML5 videos.",
            urlPattern = "*",
            isEnabled = true,
            runAt = "document_end",
            author = "Indoweb Store",
            version = "2.0",
            isBuiltIn = false,
            scriptCode = """
                (function() {
                    function addSpeedButtons() {
                        document.querySelectorAll('video').forEach(v => {
                            if (v.dataset.speedAttached) return;
                            v.dataset.speedAttached = 'true';
                            
                            const container = document.createElement('div');
                            container.style.cssText = 'position:absolute;top:12px;left:12px;z-index:99999;display:flex;gap:4px;background:rgba(0,0,0,0.7);padding:4px 8px;border-radius:12px;';
                            
                            [1, 1.5, 2, 2.5, 3].forEach(rate => {
                                const btn = document.createElement('button');
                                btn.innerText = rate + 'x';
                                btn.style.cssText = 'background:#2563eb;color:#fff;border:none;padding:3px 6px;border-radius:6px;font-size:11px;font-weight:bold;cursor:pointer;';
                                btn.onclick = (e) => {
                                    e.stopPropagation();
                                    v.playbackRate = rate;
                                    console.log('Playback rate set to ' + rate);
                                };
                                container.appendChild(btn);
                            });
                            v.parentElement && v.parentElement.appendChild(container);
                        });
                    }
                    setInterval(addSpeedButtons, 2000);
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "store-copy-unlocker",
            name = "Copy & Text Selection Unlocker",
            description = "Bypasses right-click disabled, copy/cut disabled, and user-select:none website restrictions.",
            urlPattern = "*",
            isEnabled = false,
            runAt = "document_start",
            author = "Indoweb Store",
            version = "1.5",
            isBuiltIn = false,
            scriptCode = """
                (function() {
                    const unlock = () => {
                        const style = document.createElement('style');
                        style.innerHTML = '* { -webkit-user-select: auto !important; user-select: auto !important; }';
                        (document.head || document.documentElement).appendChild(style);
                        
                        ['contextmenu', 'copy', 'cut', 'paste', 'selectstart', 'dragstart'].forEach(event => {
                            document.addEventListener(event, e => e.stopPropagation(), true);
                        });
                    };
                    unlock();
                    document.addEventListener('DOMContentLoaded', unlock);
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "store-image-downloader",
            name = "Image Gallery & Downloader Helper",
            description = "Hover or long-press high-res images to instantly inspect and extract direct links.",
            urlPattern = "*",
            isEnabled = false,
            runAt = "document_end",
            author = "Indoweb Store",
            version = "1.2",
            isBuiltIn = false,
            scriptCode = """
                (function() {
                    document.querySelectorAll('img').forEach(img => {
                        if (img.width > 120 && img.height > 120) {
                            img.style.cursor = 'pointer';
                            img.title = 'Indoweb: Tap to open image direct source';
                            img.addEventListener('dblclick', () => {
                                window.open(img.src || img.currentSrc, '_blank');
                            });
                        }
                    });
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "store-clean-redirects",
            name = "Clean URLs & Tracker Stripper",
            description = "Strips analytics, UTM parameters, fbclid, and gclid from links to protect privacy.",
            urlPattern = "*",
            isEnabled = true,
            runAt = "document_end",
            author = "Indoweb Store",
            version = "1.4",
            isBuiltIn = false,
            scriptCode = """
                (function() {
                    function cleanLinks() {
                        document.querySelectorAll('a[href]').forEach(a => {
                            try {
                                const url = new URL(a.href);
                                const trackers = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'fbclid', 'gclid', 'msclkid'];
                                let cleaned = false;
                                trackers.forEach(t => {
                                    if (url.searchParams.has(t)) {
                                        url.searchParams.delete(t);
                                        cleaned = true;
                                    }
                                });
                                if (cleaned) a.href = url.toString();
                            } catch(e) {}
                        });
                    }
                    cleanLinks();
                    setInterval(cleanLinks, 3000);
                })();
            """.trimIndent()
        )
    )

    val BUILT_IN_SCRIPTS: List<UserScript> = listOf(
        UserScript(
            id = "builtin-dark-mode",
            name = "Smart Dark Mode Inverter",
            description = "Applies high-contrast OLED dark theme with smart image and video preservation.",
            urlPattern = "*",
            isEnabled = false,
            runAt = "document_start",
            author = "Indoweb Team",
            version = "1.3",
            isBuiltIn = true,
            scriptCode = """
                (function() {
                    function applyDark() {
                        const styleId = 'indoweb-dark-mode-style';
                        if (document.getElementById(styleId)) return;
                        const style = document.createElement('style');
                        style.id = styleId;
                        style.innerHTML = `
                            html {
                                filter: invert(90%) hue-rotate(180deg) !important;
                                background: #111 !important;
                            }
                            img, video, iframe, canvas, svg, [style*="background-image"] {
                                filter: invert(100%) hue-rotate(180deg) !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(style);
                    }
                    if (document.head || document.documentElement) {
                        applyDark();
                    } else {
                        document.addEventListener('DOMContentLoaded', applyDark);
                    }
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "builtin-ad-blocker",
            name = "Ad Shield & Overlay Cleaner",
            description = "Removes floating ad banners, cookie popups, and sticky marketing overlays.",
            urlPattern = "*",
            isEnabled = true,
            runAt = "document_start",
            author = "Indoweb Team",
            version = "2.1",
            isBuiltIn = true,
            scriptCode = """
                (function() {
                    const adSelectors = [
                        '.ad', '.ads', '.advertisement', '.ad-box', '.ad-banner',
                        '[id*="google_ads"]', '[id*="banner-ad"]', '[class*="sticky-ad"]',
                        '.cookie-banner', '.gdpr-modal', '#cookie-consent',
                        '.floating-banner', '[aria-label*="advertisement" i]',
                        '.ad-container', '.sponsored-post', '#ad-header'
                    ];
                    function injectAdRules() {
                        const styleId = 'indoweb-adblock-rules';
                        if (document.getElementById(styleId)) return;
                        const style = document.createElement('style');
                        style.id = styleId;
                        style.innerHTML = adSelectors.join(', ') + ' { display: none !important; visibility: hidden !important; height: 0 !important; }';
                        (document.head || document.documentElement).appendChild(style);
                    }
                    injectAdRules();
                    document.addEventListener('DOMContentLoaded', injectAdRules);
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "builtin-auto-scroll",
            name = "Floating Auto-Scroller",
            description = "Injects a floating widget to smoothly scroll long articles or jump to top.",
            urlPattern = "*",
            isEnabled = false,
            runAt = "document_end",
            author = "Indoweb Team",
            version = "1.0",
            isBuiltIn = true,
            scriptCode = """
                (function() {
                    if (document.getElementById('indoweb-scroll-widget')) return;
                    let scrollInterval = null;
                    const widget = document.createElement('div');
                    widget.id = 'indoweb-scroll-widget';
                    widget.style.cssText = 'position:fixed;bottom:90px;right:16px;z-index:999999;display:flex;flex-direction:column;gap:6px;background:rgba(20,24,36,0.85);backdrop-filter:blur(8px);padding:8px;border-radius:24px;box-shadow:0 4px 16px rgba(0,0,0,0.3);border:1px solid rgba(255,255,255,0.15);font-family:sans-serif;';

                    const topBtn = document.createElement('button');
                    topBtn.innerHTML = '▲';
                    topBtn.title = 'Scroll to Top';
                    topBtn.style.cssText = 'width:36px;height:36px;border-radius:50%;border:none;background:#2563eb;color:#fff;font-weight:bold;cursor:pointer;';
                    topBtn.onclick = () => window.scrollTo({top: 0, behavior: 'smooth'});

                    const playBtn = document.createElement('button');
                    playBtn.innerHTML = '▶';
                    playBtn.title = 'Start / Stop Auto Scroll';
                    playBtn.style.cssText = 'width:36px;height:36px;border-radius:50%;border:none;background:#10b981;color:#fff;font-weight:bold;cursor:pointer;';
                    playBtn.onclick = () => {
                        if (scrollInterval) {
                            clearInterval(scrollInterval);
                            scrollInterval = null;
                            playBtn.innerHTML = '▶';
                            playBtn.style.background = '#10b981';
                        } else {
                            scrollInterval = setInterval(() => {
                                window.scrollBy({top: 3, behavior: 'smooth'});
                            }, 50);
                            playBtn.innerHTML = '❚❚';
                            playBtn.style.background = '#ef4444';
                        }
                    };

                    widget.appendChild(topBtn);
                    widget.appendChild(playBtn);
                    document.body.appendChild(widget);
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "builtin-reader-mode",
            name = "Article Typography Enhancer",
            description = "Improves text readability with optimal font sizes and comfortable line height.",
            urlPattern = "*",
            isEnabled = false,
            runAt = "document_start",
            author = "Indoweb Team",
            version = "1.0",
            isBuiltIn = true,
            scriptCode = """
                (function() {
                    const style = document.createElement('style');
                    style.innerHTML = `
                        p, article, .article-content, .entry-content, main {
                            font-size: 19px !important;
                            line-height: 1.75 !important;
                            letter-spacing: 0.015em !important;
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                })();
            """.trimIndent()
        )
    )

    data class Template(
        val title: String,
        val description: String,
        val defaultUrl: String,
        val runAt: String,
        val code: String
    )

    val STARTER_TEMPLATES = listOf(
        Template(
            title = "DOM Element Modifier",
            description = "Select and modify specific elements or change text",
            defaultUrl = "*",
            runAt = "document_end",
            code = """
                (function() {
                    const headers = document.querySelectorAll('h1, h2');
                    headers.forEach(h => {
                        h.style.color = '#3b82f6';
                    });
                    console.log('Indoweb: Modified ' + headers.length + ' headers');
                })();
            """.trimIndent()
        ),
        Template(
            title = "Early CSS / Style Injector",
            description = "Inject custom CSS stylesheets early before page renders",
            defaultUrl = "*",
            runAt = "document_start",
            code = """
                (function() {
                    const style = document.createElement('style');
                    style.innerHTML = `
                        /* Custom CSS rules */
                        body {
                            font-size: 16px;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                })();
            """.trimIndent()
        ),
        Template(
            title = "Auto-Clicker / Automator",
            description = "Automatically click buttons (e.g., skip ad, accept cookies)",
            defaultUrl = "*",
            runAt = "document_end",
            code = """
                (function() {
                    const interval = setInterval(() => {
                        const target = document.querySelector('.accept-all, #accept-btn, .skip-button');
                        if (target) {
                            target.click();
                            console.log('Indoweb: Auto-clicked target element');
                            clearInterval(interval);
                        }
                    }, 500);
                    setTimeout(() => clearInterval(interval), 10000);
                })();
            """.trimIndent()
        )
    )
}
