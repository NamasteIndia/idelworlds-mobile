package com.idleworlds.android

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.MenuInflater
import android.widget.ImageButton
import android.widget.PopupMenu
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import at.pardus.android.webview.gm.run.WebViewGmApi
import at.pardus.android.webview.gm.store.ScriptStoreSQLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var userScriptManager: UserScriptManager
    private lateinit var systemScriptManager: SystemScriptManager
    private lateinit var scriptStore: ScriptStoreSQLite
    private lateinit var scriptSyncManager: ScriptSyncManager
    private lateinit var gmScriptInjector: GmScriptInjector
    private var pageFinishedLoading = false
    private var currentUrl: String = GAME_URL
    private var resumeCount = 0
    private var isInitialOrRefresh = true
    private var scriptSyncJob: Job? = null
    private val registeredScriptHandlers = mutableListOf<ScriptHandler>()

    private val jsBridgeName = "WebViewGM"
    private val secret = UUID.randomUUID().toString()

    enum class StageState { PENDING, ACTIVE, DONE }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        loadingOverlay = findViewById(R.id.loading_overlay)
        findViewById<ImageButton>(R.id.menu_button).setOnClickListener { showAppMenu(it) }

        setupWebViewSettings()

        scriptStore = ScriptStoreSQLite(this)
        userScriptManager = UserScriptManager(this, lifecycleScope)
        scriptSyncManager = ScriptSyncManager(userScriptManager, scriptStore)
        gmScriptInjector = GmScriptInjector(scriptStore, jsBridgeName, secret)
        systemScriptManager = SystemScriptManager(this, webView)

        webView.addJavascriptInterface(
            WebViewGmApi(webView, scriptStore, secret),
            jsBridgeName
        )
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        webView.webChromeClient = setupWebChromeClient()
        webView.webViewClient = setupWebViewClient()

        scriptSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            scriptStore.open()
            val updateJob = userScriptManager.updateEnabledScripts {
                scriptSyncManager.syncScripts()
            }
            updateJob.join()

            withContext(Dispatchers.Main) {
                isInitialOrRefresh = true
                applyDocumentStartScripts()
                webView.loadUrl(GAME_URL)
            }
        }

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

    private fun showAppMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        MenuInflater(this).inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_script_manager -> {
                    openScriptManager()
                    true
                }
                R.id.action_reload_game -> {
                    refreshPage()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        if (resumeCount <= 1) return

        scriptSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            val updateJob = userScriptManager.updateEnabledScripts {
                scriptSyncManager.syncScripts()
            }
            updateJob.join()

            withContext(Dispatchers.Main) {
                applyDocumentStartScripts()
            }
        }
    }

    private fun applyDocumentStartScripts() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            registeredScriptHandlers.forEach { it.remove() }
            registeredScriptHandlers.clear()

            val scripts = gmScriptInjector.getDocumentStartScripts(GAME_URL)
            for (scriptJs in scripts) {
                val handler = WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    scriptJs,
                    DOCUMENT_START_ORIGINS
                )
                registeredScriptHandlers.add(handler)
            }
            Log.i("MainActivity", "Cleaned and re-registered ${scripts.size} document-start scripts")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            scriptStore.close()
        } catch (_: Exception) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    private fun setupWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }
        }
    }

    private fun setupWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                if (url?.startsWith(GAME_HOST) == true) {
                    currentUrl = url

                    if (isInitialOrRefresh) {
                        showLoadingScreen()
                    }

                    pageFinishedLoading = false

                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        injectDocumentStartScriptsLegacy(url)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val isGameSite = url?.startsWith(GAME_HOST) == true

                if (isGameSite && !pageFinishedLoading) {
                    currentUrl = url ?: currentUrl

                    if (isInitialOrRefresh) {
                        injectDocumentEndScripts(currentUrl)
                    } else {
                        performSilentInjection(currentUrl)
                    }

                    pageFinishedLoading = true
                    isInitialOrRefresh = false
                } else if (!isGameSite) {
                    hideLoadingScreen()
                }
            }
        }
    }

    private fun injectDocumentStartScriptsLegacy(url: String) {
        lifecycleScope.launch {
            systemScriptManager.injectLZString()
            scriptSyncJob?.join()
            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, false)
            }
        }
    }

    private fun performSilentInjection(url: String) {
        lifecycleScope.launch {
            scriptSyncJob?.join()
            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, true)
                systemScriptManager.disableLongClick()
            }
        }
    }

    private fun injectDocumentEndScripts(url: String) {
        lifecycleScope.launch {
            delay(1000L)

            runOnUiThread {
                setStageState(1, StageState.DONE)
                setStageState(2, StageState.ACTIVE)
            }

            val enabledCount = userScriptManager.getEnabledScriptCount()
            if (enabledCount > 0) {
                runOnUiThread {
                    findViewById<TextView>(R.id.stage2_text).text =
                        "Preparing $enabledCount script(s)..."
                }
            }

            scriptSyncJob?.join()

            withContext(Dispatchers.Main) {
                gmScriptInjector.injectScripts(webView, url, true)
            }

            runOnUiThread {
                setStageState(2, StageState.DONE)
                setStageState(3, StageState.ACTIVE)
            }

            systemScriptManager.disableLongClick()

            delay(1500L)

            runOnUiThread {
                setStageState(3, StageState.DONE)
            }

            delay(300L)

            runOnUiThread {
                hideLoadingScreen()
            }
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
            3 -> {
                spinnerId = R.id.stage3_spinner
                iconId = R.id.stage3_icon
                textId = R.id.stage3_text
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
                if (stage == 3) {
                    spinner.visibility = View.VISIBLE
                    icon.visibility = View.GONE
                } else {
                    spinner.visibility = View.GONE
                    icon.visibility = View.VISIBLE
                    icon.setImageResource(R.drawable.ic_stage_done)
                }
                text.alpha = 1.0f
            }
        }
    }

    private fun showLoadingScreen() {
        loadingOverlay.alpha = 1f
        loadingOverlay.visibility = View.VISIBLE
        setStageState(1, StageState.ACTIVE)
        setStageState(2, StageState.PENDING)
        setStageState(3, StageState.PENDING)
        findViewById<TextView>(R.id.stage2_text).text = "Preparing scripts..."
    }

    private fun hideLoadingScreen() {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                loadingOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun openScriptManager() {
        val intent = Intent(this, ScriptManagerActivity::class.java)
        startActivity(intent)
    }

    @JavascriptInterface
    fun refreshPage() {
        runOnUiThread {
            isInitialOrRefresh = true
            webView.reload()
        }
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun openScriptManager() {
            this@MainActivity.openScriptManager()
        }

        @JavascriptInterface
        fun refreshPage() {
            this@MainActivity.refreshPage()
        }
    }

    companion object {
        private const val GAME_URL = "https://www.idleworlds.com/"
        private const val GAME_HOST = "https://www.idleworlds.com"
        private val DOCUMENT_START_ORIGINS = setOf(
            "https://www.idleworlds.com",
            "https://*.idleworlds.com"
        )
    }
}
