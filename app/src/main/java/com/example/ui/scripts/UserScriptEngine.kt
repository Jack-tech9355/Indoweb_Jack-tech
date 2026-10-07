package com.example.ui.scripts

import com.example.data.model.UserScript

object UserScriptEngine {

    /**
     * Builds a self-executing JavaScript wrapper containing complete Greasemonkey / Tampermonkey
     * compatibility shims (GM_addStyle, GM_setValue, GM_getValue, GM_deleteValue, GM_listValues,
     * GM_xmlhttpRequest, GM_notification, GM_log, unsafeWindow, GM4) so third-party GreasyFork scripts
     * execute without errors, with pre-injected @require dependencies and CORS-bypassing network calls.
     */
    fun wrapScript(script: UserScript, stage: String, preloadedRequires: String = ""): String {
        val scriptKey = "indoweb_gm_" + script.id.replace("-", "_")
        val escapedName = script.name.replace("'", "\\'").replace("\n", " ").trim()
        val escapedDesc = script.description.replace("'", "\\'").replace("\n", " ").trim()

        return """
            (function() {
                try {
                    console.log('[Indoweb Tampermonkey] Injecting: ' + '$escapedName' + ' at $stage');

                    // Global GM callback registry for asynchronous native calls
                    if (!window.__gmXhrCallbacks) {
                        window.__gmXhrCallbacks = {};
                    }
                    if (!window.__gmXhrCallback) {
                        window.__gmXhrCallback = function(reqId, isError, responseJsonStr) {
                            const cb = window.__gmXhrCallbacks[reqId];
                            if (!cb) return;
                            delete window.__gmXhrCallbacks[reqId];
                            try {
                                const resp = typeof responseJsonStr === 'string' ? JSON.parse(responseJsonStr) : responseJsonStr;
                                if (isError) {
                                    if (cb.onerror) cb.onerror(resp);
                                } else {
                                    if (resp.status >= 200 && resp.status < 400) {
                                        if (cb.onload) cb.onload(resp);
                                    } else {
                                        if (cb.onerror) cb.onerror(resp);
                                    }
                                }
                            } catch(e) {
                                if (cb.onerror) cb.onerror({ status: 0, statusText: e.message, responseText: '' });
                            }
                        };
                    }

                    // GM Compatibility Environment
                    const _scriptId = '$scriptKey';
                    const unsafeWindow = window;

                    function GM_addStyle(css) {
                        try {
                            const style = document.createElement('style');
                            style.textContent = css;
                            (document.head || document.documentElement).appendChild(style);
                            return style;
                        } catch (e) {
                            console.error('[Indoweb GM_addStyle error]', e);
                        }
                    }

                    function GM_setValue(key, value) {
                        try {
                            localStorage.setItem(_scriptId + '_' + key, JSON.stringify(value));
                        } catch (e) {
                            console.error('[Indoweb GM_setValue error]', e);
                        }
                    }

                    function GM_getValue(key, defaultValue) {
                        try {
                            const item = localStorage.getItem(_scriptId + '_' + key);
                            return item !== null ? JSON.parse(item) : defaultValue;
                        } catch (e) {
                            return defaultValue;
                        }
                    }

                    function GM_deleteValue(key) {
                        try {
                            localStorage.removeItem(_scriptId + '_' + key);
                        } catch (e) {}
                    }

                    function GM_listValues() {
                        const keys = [];
                        try {
                            const prefix = _scriptId + '_';
                            for (let i = 0; i < localStorage.length; i++) {
                                const k = localStorage.key(i);
                                if (k && k.startsWith(prefix)) {
                                    keys.push(k.substring(prefix.length));
                                }
                            }
                        } catch (e) {}
                        return keys;
                    }

                    function GM_log() {
                        const args = Array.from(arguments);
                        console.log.apply(console, ['[GM_log ' + '$escapedName' + ']:'].concat(args));
                    }

                    function GM_openInTab(url) {
                        return window.open(url, '_blank');
                    }

                    function GM_setClipboard(text) {
                        if (navigator.clipboard) {
                            navigator.clipboard.writeText(text);
                        }
                    }

                    function GM_notification(details, ondone) {
                        const text = typeof details === 'string' ? details : (details.text || details.title);
                        console.log('[GM_notification]:', text);
                        if (typeof ondone === 'function') ondone();
                    }

                    function GM_getResourceText(name) {
                        return '';
                    }

                    function GM_getResourceURL(name) {
                        return '';
                    }

                    function GM_xmlhttpRequest(details) {
                        const reqId = 'gm_req_' + Math.random().toString(36).substring(2, 9) + '_' + Date.now();
                        window.__gmXhrCallbacks[reqId] = details;

                        if (window.IndowebBridge && window.IndowebBridge.gmXmlHttpRequest) {
                            // Route through native asynchronous OkHttp client (completely bypassing CORS)
                            try {
                                window.IndowebBridge.gmXmlHttpRequest(reqId, JSON.stringify({
                                    url: details.url,
                                    method: details.method || 'GET',
                                    headers: details.headers || {},
                                    data: details.data || null
                                }));
                                return;
                            } catch(e) {
                                console.warn('[Indoweb] Native XHR call failed, falling back to fetch', e);
                            }
                        }

                        // Fallback in-page XMLHttpRequest
                        const xhr = new XMLHttpRequest();
                        xhr.open(details.method || 'GET', details.url, true);
                        const hdrs = details.headers || {};
                        for (const h in hdrs) {
                            xhr.setRequestHeader(h, hdrs[h]);
                        }
                        xhr.onload = function() {
                            const resp = {
                                status: xhr.status,
                                statusText: xhr.statusText,
                                responseText: xhr.responseText,
                                responseHeaders: xhr.getAllResponseHeaders()
                            };
                            if (xhr.status >= 200 && xhr.status < 400) {
                                if (details.onload) details.onload(resp);
                            } else {
                                if (details.onerror) details.onerror(resp);
                            }
                        };
                        xhr.onerror = function() {
                            if (details.onerror) details.onerror({ status: xhr.status, statusText: 'Network error', responseText: '' });
                        };
                        xhr.send(details.data || null);
                    }

                    // GM4 Promise-based compatibility
                    const GM = {
                        addStyle: GM_addStyle,
                        setValue: (k, v) => Promise.resolve(GM_setValue(k, v)),
                        getValue: (k, d) => Promise.resolve(GM_getValue(k, d)),
                        deleteValue: (k) => Promise.resolve(GM_deleteValue(k)),
                        listValues: () => Promise.resolve(GM_listValues()),
                        xmlHttpRequest: GM_xmlhttpRequest,
                        notification: GM_notification,
                        info: {
                            script: {
                                name: '$escapedName',
                                version: '${script.version}',
                                description: '$escapedDesc'
                            }
                        }
                    };

                    // --- Pre-injected @require Libraries (e.g. jQuery, Lodash) ---
                    $preloadedRequires

                    // --- Main UserScript Body ---
                    ${script.scriptCode}

                    return 'OK: $escapedName';
                } catch (err) {
                    console.error('[Indoweb Tampermonkey Error in $escapedName]:', err);
                    return 'ERROR: ' + err.message;
                }
            })();
        """.trimIndent()
    }
}
