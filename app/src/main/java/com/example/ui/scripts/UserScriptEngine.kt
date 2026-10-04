package com.example.ui.scripts

import com.example.data.model.UserScript

object UserScriptEngine {

    /**
     * Builds a self-executing JavaScript wrapper containing complete Greasemonkey / Tampermonkey
     * compatibility shims (GM_addStyle, GM_setValue, GM_getValue, GM_deleteValue, GM_listValues,
     * GM_xmlhttpRequest, GM_log, unsafeWindow, GM4) so third-party GreasyFork scripts execute without errors.
     */
    fun wrapScript(script: UserScript, stage: String): String {
        val scriptKey = "indoweb_gm_" + script.id.replace("-", "_")
        val escapedName = script.name.replace("'", "\\'").replace("\n", " ").trim()
        val escapedDesc = script.description.replace("'", "\\'").replace("\n", " ").trim()

        return """
            (function() {
                try {
                    console.log('[Indoweb Tampermonkey] Injecting: ' + '$escapedName' + ' at $stage');

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

                    function GM_xmlhttpRequest(details) {
                        const url = details.url;
                        const method = details.method || 'GET';
                        const headers = details.headers || {};
                        const data = details.data || null;

                        const xhr = new XMLHttpRequest();
                        xhr.open(method, url, true);
                        for (const h in headers) {
                            xhr.setRequestHeader(h, headers[h]);
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
                            if (details.onerror) details.onerror({ status: xhr.status, statusText: 'Network error' });
                        };
                        xhr.send(data);
                    }

                    // GM4 Promise-based compatibility
                    const GM = {
                        addStyle: GM_addStyle,
                        setValue: (k, v) => Promise.resolve(GM_setValue(k, v)),
                        getValue: (k, d) => Promise.resolve(GM_getValue(k, d)),
                        deleteValue: (k) => Promise.resolve(GM_deleteValue(k)),
                        listValues: () => Promise.resolve(GM_listValues()),
                        xmlHttpRequest: GM_xmlhttpRequest,
                        info: {
                            script: {
                                name: '$escapedName',
                                version: '${script.version}',
                                description: '$escapedDesc'
                            }
                        }
                    };

                    // Run the UserScript code inside isolated closure
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
