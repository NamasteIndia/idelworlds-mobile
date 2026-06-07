package com.idleworlds.android

import android.content.Context
import android.webkit.WebView

class SystemScriptManager(private val context: Context, private val webView: WebView) {

    fun disableLongClick() {
        val jsCode = """
        document.addEventListener('contextmenu', (e) => {
            const target = e.target;
            const isEditable = target && (
                target.isContentEditable ||
                target.tagName === 'INPUT' ||
                target.tagName === 'TEXTAREA' ||
                target.closest('[contenteditable="true"]')
            );
            if (!isEditable) {
                e.preventDefault();
                return false;
            }
        }, true);
        document.addEventListener('touchstart', (e) => {
            const target = e.target;
            const isEditable = target && (
                target.isContentEditable ||
                target.tagName === 'INPUT' ||
                target.tagName === 'TEXTAREA' ||
                target.closest('[contenteditable="true"]')
            );
            if (!isEditable) {
                e.target.style.webkitTouchCallout = 'none';
                e.target.style.webkitUserSelect = 'none';
            }
        }, true);
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    fun injectLZString() {
        try {
            val lzStringScript =
                context.assets.open("js/lz-string.min.js").bufferedReader().use { it.readText() }
            webView.evaluateJavascript(lzStringScript, null)
        } catch (e: Exception) {
            android.util.Log.e("SystemScriptManager", "Failed to inject LZ-String", e)
        }
    }
}
