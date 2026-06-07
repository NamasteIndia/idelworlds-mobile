package com.idleworlds.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var notificationStore: GameNotificationStore
    private var pageFinishedLoading = false
    private var pendingNotificationRequest: PermissionRequest? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingNotificationRequest?.let { request ->
            if (granted) {
                request.grant(arrayOf(WEBVIEW_RESOURCE_NOTIFICATION))
            } else {
                request.deny()
            }
            pendingNotificationRequest = null
        }

        if (granted && notificationStore.pushPollingEnabled) {
            NotificationPollingService.start(this)
        }
    }

    enum class StageState { PENDING, ACTIVE, DONE }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationStore = GameNotificationStore(this)
        NotificationHelper.createChannels(this)

        webView = findViewById(R.id.webview)
        loadingOverlay = findViewById(R.id.loading_overlay)
        findViewById<ImageButton>(R.id.menu_button).setOnClickListener { showAppMenu(it) }

        setupWebViewSettings()
        injectPushPolyfill()
        webView.addJavascriptInterface(WebAppInterface(), "Android")
        webView.webChromeClient = setupWebChromeClient()
        webView.webViewClient = setupWebViewClient()

        requestNotificationPermissionIfNeeded()
        if (notificationStore.pushPollingEnabled) {
            NotificationPollingService.start(this)
        }

        webView.loadUrl(GAME_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationHelper.hasPermission(this)) return

        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showAppMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        MenuInflater(this).inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reload_game -> {
                    refreshPage()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    private fun injectPushPolyfill() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return
        }

        val polyfill = assets.open("js/push_polyfill.js").bufferedReader().use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            polyfill,
            setOf(GAME_HOST, "https://*.idleworlds.com")
        )
    }

    private fun setupWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) {
                    return
                }

                if (!request.resources.contains(WEBVIEW_RESOURCE_NOTIFICATION)) {
                    request.deny()
                    return
                }

                if (NotificationHelper.hasPermission(this@MainActivity)) {
                    request.grant(arrayOf(WEBVIEW_RESOURCE_NOTIFICATION))
                } else {
                    pendingNotificationRequest = request
                    requestNotificationPermissionIfNeeded()
                }
            }
        }
    }

    private fun setupWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.startsWith(GAME_HOST) == true) {
                    pageFinishedLoading = false
                    showLoadingScreen()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.startsWith(GAME_HOST) != true || pageFinishedLoading) {
                    if (url?.startsWith(GAME_HOST) != true) {
                        hideLoadingScreen()
                    }
                    return
                }

                pageFinishedLoading = true
                injectLegacyPolyfillIfNeeded()
                disableLongPressOutsideInputs()
                reportCurrentSection()
                finishLoadingSequence()
            }
        }
    }

    private fun injectLegacyPolyfillIfNeeded() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return
        }

        val polyfill = assets.open("js/push_polyfill.js").bufferedReader().use { it.readText() }
        webView.evaluateJavascript(polyfill, null)
    }

    private fun disableLongPressOutsideInputs() {
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
            }
        }, true);
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    private fun reportCurrentSection() {
        webView.evaluateJavascript(
            """
            (function() {
                var path = window.location.pathname || '/';
                var section = path.split('/').filter(Boolean)[0] || 'task';
                if (window.Android && window.Android.reportGameSection) {
                    window.Android.reportGameSection(section);
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun finishLoadingSequence() {
        lifecycleScope.launch {
            delay(500)
            setStageState(1, StageState.DONE)
            setStageState(2, StageState.ACTIVE)
            delay(700)
            setStageState(2, StageState.DONE)
            delay(250)
            hideLoadingScreen()
        }
    }

    private fun setStageState(stage: Int, state: StageState) {
        val spinnerId: Int
        val iconId: Int
        val textId: Int

        when (stage) {
            1 -> {
                spinnerId = R.id.stage1_spinner
                iconId = R.id.stage1_icon
                textId = R.id.stage1_text
            }
            2 -> {
                spinnerId = R.id.stage2_spinner
                iconId = R.id.stage2_icon
                textId = R.id.stage2_text
            }
            else -> return
        }

        val spinner = findViewById<ProgressBar>(spinnerId)
        val icon = findViewById<ImageView>(iconId)
        val text = findViewById<TextView>(textId)

        when (state) {
            StageState.PENDING -> {
                spinner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_stage_pending)
                text.alpha = 0.5f
            }
            StageState.ACTIVE -> {
                spinner.visibility = View.VISIBLE
                icon.visibility = View.GONE
                text.alpha = 1.0f
            }
            StageState.DONE -> {
                spinner.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_stage_done)
                text.alpha = 1.0f
            }
        }
    }

    private fun showLoadingScreen() {
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
        setStageState(1, StageState.ACTIVE)
        setStageState(2, StageState.PENDING)
    }

    private fun hideLoadingScreen() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction { loadingOverlay.visibility = View.GONE }
            .start()
    }

    private fun refreshPage() {
        webView.reload()
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun enablePushPolling() {
            notificationStore.pushPollingEnabled = true
            if (NotificationHelper.hasPermission(this@MainActivity)) {
                NotificationPollingService.start(this@MainActivity)
            } else {
                requestNotificationPermissionIfNeeded()
            }
        }

        @JavascriptInterface
        fun disablePushPolling() {
            notificationStore.pushPollingEnabled = false
            NotificationPollingService.stop(this@MainActivity)
        }

        @JavascriptInterface
        fun reportGameSection(section: String) {
            if (section.isNotBlank()) {
                notificationStore.gameSection = section
            }
        }

        @JavascriptInterface
        fun showNotification(title: String, body: String) {
            if (!NotificationHelper.hasPermission(this@MainActivity)) {
                requestNotificationPermissionIfNeeded()
                return
            }

            NotificationHelper.showGameNotification(
                context = this@MainActivity,
                notificationId = (title + body).hashCode(),
                title = title.ifBlank { getString(R.string.app_name) },
                body = body.ifBlank { "Notification" }
            )
        }

        @JavascriptInterface
        fun refreshPage() {
            runOnUiThread { this@MainActivity.refreshPage() }
        }
    }

    companion object {
        private const val GAME_URL = "https://www.idleworlds.com/"
        private const val GAME_HOST = "https://www.idleworlds.com"
        private const val WEBVIEW_RESOURCE_NOTIFICATION = "android.webkit.resource.NOTIFICATION"
    }
}
