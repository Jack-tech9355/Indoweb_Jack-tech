package com.example.ui.scripts

import com.example.data.model.UserScript

object ScriptTemplates {

    val BUILT_IN_SCRIPTS: List<UserScript> = listOf(
        UserScript(
            id = "builtin-dark-mode",
            name = "Smart Dark Mode Inverter",
            description = "Applies high-contrast dark theme with smart image and video preservation.",
            urlPattern = "*",
            isEnabled = false,
            runAt = "document_end",
            author = "Indoweb Team",
            version = "1.2",
            isBuiltIn = true,
            scriptCode = """
                (function() {
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
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "builtin-ad-blocker",
            name = "Ad Shield & Overlay Cleaner",
            description = "Removes floating ad banners, cookie popups, and sticky marketing overlays.",
            urlPattern = "*",
            isEnabled = true,
            runAt = "document_end",
            author = "Indoweb Team",
            version = "2.0",
            isBuiltIn = true,
            scriptCode = """
                (function() {
                    const adSelectors = [
                        '.ad', '.ads', '.advertisement', '.ad-box', '.ad-banner',
                        '[id*="google_ads"]', '[id*="banner-ad"]', '[class*="sticky-ad"]',
                        '.cookie-banner', '.gdpr-modal', '#cookie-consent',
                        '.floating-banner', '[aria-label*="advertisement" i]'
                    ];
                    function removeAds() {
                        adSelectors.forEach(sel => {
                            document.querySelectorAll(sel).forEach(el => {
                                el.style.setProperty('display', 'none', 'important');
                            });
                        });
                    }
                    removeAds();
                    setTimeout(removeAds, 1000);
                    setTimeout(removeAds, 3000);
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
                    widget.style.cssText = 'position:fixed;bottom:80px;right:16px;z-index:999999;display:flex;flex-direction:column;gap:6px;background:rgba(20,24,36,0.85);backdrop-filter:blur(8px);padding:8px;border-radius:24px;box-shadow:0 4px 16px rgba(0,0,0,0.3);border:1px solid rgba(255,255,255,0.15);font-family:sans-serif;';

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
            runAt = "document_end",
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
                    document.head.appendChild(style);
                })();
            """.trimIndent()
        ),
        UserScript(
            id = "builtin-video-enhancer",
            name = "HTML5 Video Speed Booster",
            description = "Adds playback speed controls (1x, 1.5x, 2x) directly onto all web videos.",
            urlPattern = "*",
            isEnabled = true,
            runAt = "document_end",
            author = "Indoweb Team",
            version = "1.1",
            isBuiltIn = true,
            scriptCode = """
                (function() {
                    function enhanceVideos() {
                        const videos = document.querySelectorAll('video');
                        videos.forEach(v => {
                            if (v.dataset.speedEnhanced) return;
                            v.dataset.speedEnhanced = 'true';
                            v.addEventListener('dblclick', () => {
                                const speeds = [1, 1.25, 1.5, 2];
                                const current = v.playbackRate;
                                const nextIndex = (speeds.indexOf(current) + 1) % speeds.length;
                                v.playbackRate = speeds[nextIndex];
                                console.log('Video speed set to: ' + v.playbackRate + 'x');
                            });
                        });
                    }
                    enhanceVideos();
                    setInterval(enhanceVideos, 3000);
                })();
            """.trimIndent()
        )
    )

    data class Template(
        val title: String,
        val description: String,
        val defaultUrl: String,
        val code: String
    )

    val STARTER_TEMPLATES = listOf(
        Template(
            title = "DOM Element Modifier",
            description = "Select and modify specific elements or change text",
            defaultUrl = "*",
            code = """
                (function() {
                    // Example: find header and add a custom highlight banner
                    const headers = document.querySelectorAll('h1, h2');
                    headers.forEach(h => {
                        h.style.color = '#3b82f6';
                    });
                    console.log('Indoweb: Modified ' + headers.length + ' headers');
                })();
            """.trimIndent()
        ),
        Template(
            title = "Custom CSS Injector",
            description = "Inject custom CSS stylesheets into any webpage",
            defaultUrl = "*",
            code = """
                (function() {
                    const style = document.createElement('style');
                    style.innerHTML = `
                        /* Add your custom styles here */
                        body {
                            /* custom background or font */
                        }
                    `;
                    document.head.appendChild(style);
                })();
            """.trimIndent()
        ),
        Template(
            title = "Auto-Clicker / Automator",
            description = "Automatically click buttons (e.g., skip ad, accept cookies)",
            defaultUrl = "*",
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
                    // Stop checking after 10 seconds
                    setTimeout(() => clearInterval(interval), 10000);
                })();
            """.trimIndent()
        ),
        Template(
            title = "Link Cleaner / Unredirect",
            description = "Remove referral trackers and redirects from links",
            defaultUrl = "*",
            code = """
                (function() {
                    document.querySelectorAll('a[href]').forEach(a => {
                        try {
                            const url = new URL(a.href);
                            ['utm_source', 'utm_medium', 'utm_campaign', 'fbclid', 'gclid'].forEach(param => {
                                url.searchParams.delete(param);
                            });
                            a.href = url.toString();
                        } catch(e) {}
                    });
                })();
            """.trimIndent()
        )
    )
}
