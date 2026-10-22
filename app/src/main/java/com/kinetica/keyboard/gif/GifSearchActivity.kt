package com.kinetica.keyboard.gif

import android.annotation.SuppressLint
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.kinetica.keyboard.engine.KineticaConstants
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.kinetica.keyboard.R
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** A visible Google Images browser, with no DOM scraper, JS bridge or private API credentials. */
class GifSearchActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var query: EditText
    private val worker = Executors.newSingleThreadExecutor()
    private var download: Future<*>? = null
    private var generation = 0
    private var dialog: AlertDialog? = null
    private var showQueryOnWindowFocus = false
    private val chromeScript by lazy { assets.open("gif-search-chrome.js").bufferedReader().use { it.readText() } }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This screen is only launched by our own IME with a destination, never exported.
        if (!GifTransfer.isSearchActive(intent.getStringExtra(SEARCH_TOKEN))) { finish(); return }
        title = getString(R.string.gif_search)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = FrameLayout(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val color = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, color, true)
            setBackgroundColor(color.data)
            // Consume touches on the sheet, while the space above it dismisses search.
            isClickable = true
        }
        var preferredHeight = (intent.getFloatExtra(SHEET_HEIGHT_DP, 0f)
            .takeIf { it.isFinite() && it > 0f } ?: KineticaConstants.GIF_SHEET_DEFAULT_DP.toFloat())
            .times(resources.displayMetrics.density).toInt()
        root.addView(layout, FrameLayout.LayoutParams(-1, GifSheetSizing.height(preferredHeight, Int.MAX_VALUE), Gravity.BOTTOM))
        fun resizeSheet() {
            if (root.height <= 0) return
            val height = GifSheetSizing.height(preferredHeight, root.height - root.paddingTop - root.paddingBottom)
            if (layout.layoutParams.height != height) {
                layout.layoutParams = (layout.layoutParams as FrameLayout.LayoutParams).apply { this.height = height }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // A fold, rotation or keyboard resize can change the live keyboard height.
            if (insets.isVisible(WindowInsetsCompat.Type.ime()) && ime.bottom > bars.bottom) {
                preferredHeight = ime.bottom - bars.bottom
            }
            root.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            resizeSheet()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resizeSheet() }
        root.setOnClickListener { finish() }
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val padding = dp(KineticaConstants.GIF_TOOLBAR_PADDING_DP)
            setPadding(padding, padding, padding, padding)
        }
        query = EditText(this).apply {
            hint = getString(R.string.gif_query)
            contentDescription = hint
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setText(savedInstanceState?.getString("query").orEmpty())
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
            }
        }
        controls.addView(query, LinearLayout.LayoutParams(0, -2, 1f))
        fun addAction(icon: Int, label: Int, action: () -> Unit) {
            controls.addView(GifIconButton(this, icon, label, action),
                LinearLayout.LayoutParams(dp(KineticaConstants.GIF_ACTION_HEIGHT_DP),
                    dp(KineticaConstants.GIF_ACTION_HEIGHT_DP)).apply {
                    marginStart = dp(KineticaConstants.GIF_ACTION_GAP_DP)
                })
        }
        addAction(R.drawable.ic_gif_search, R.string.gif_search_go) { search() }
        addAction(R.drawable.ic_gif_help, R.string.gif_help) {
            showDetails(getString(R.string.gif_browser_hint), getString(R.string.gif_search))
        }
        addAction(R.drawable.ic_gif_close, R.string.gif_close) { finish() }
        layout.addView(controls)
        status = TextView(this).apply {
            visibility = View.GONE
            setPadding(dp(16), dp(8), dp(16), dp(8))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            // Only transient progress/errors occupy space; tap for an untruncated message.
            setOnClickListener { showDetails(text) }
        }
        layout.addView(status)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val url = request.url.toString()
                    if (!GifPolicy.httpsUrl(url)) { showStatus(R.string.gif_https_only); return true }
                    if (request.url.path.orEmpty().endsWith(".gif", true)) { choose(url); return true }
                    return false
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (download == null) showStatus(null)
                }
                override fun onPageCommitVisible(view: WebView, url: String?) {
                    hideGoogleChrome(view, url)
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    hideGoogleChrome(view, url)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) showStatus(R.string.gif_browser_error)
                }
            }
            setDownloadListener { url, _, _, _, _ -> choose(url) }
            setOnLongClickListener {
                val hit = hitTestResult
                if (hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    hit.extra?.let { choose(it) }
                    true
                } else false
            }
        }
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(GifPolicy.searchUrl(query.text.toString()))
        }
        // Only initial entry (or restoring query editing) focuses the field. Returning from
        // a GIF preview must not reopen the keyboard over the results.
        showQueryOnWindowFocus = savedInstanceState?.getBoolean("editingQuery") ?: true
        if (showQueryOnWindowFocus) query.requestFocus()
    }

    private fun hideGoogleChrome(view: WebView, url: String?) {
        if (GifSearchChrome.isResultsPage(url)) view.evaluateJavascript(chromeScript, null)
    }

    private fun showDetails(message: CharSequence, title: String? = null) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
        styleDialogButtons()
    }

    private fun styleDialogButtons() {
        val color = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, color, true)
        val textColors = if (color.resourceId != 0) getColorStateList(color.resourceId)
            else android.content.res.ColorStateList.valueOf(color.data)
        // Use the theme's readable primary text, not the saturated accent on dark gray.
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach {
            dialog?.getButton(it)?.setTextColor(textColors)
        }
    }

    private fun showStatus(message: Int?) {
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) status.setText(message)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && showQueryOnWindowFocus && ::query.isInitialized) {
            showQueryOnWindowFocus = false
            query.requestFocus()
            WindowCompat.getInsetsController(window, query).show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun search() {
        cancelDownload()
        showQueryOnWindowFocus = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
        query.clearFocus()
        web.requestFocus()
        web.loadUrl(GifPolicy.searchUrl(query.text.toString()))
    }

    private fun choose(url: String) {
        if (!GifPolicy.httpsUrl(url)) { showStatus(R.string.gif_not_gif); return }
        cancelDownload()
        val ticket = generation
        showStatus(R.string.gif_downloading)
        download = worker.submit {
            val result = runCatching { GifStore(this).download(url) }
            runOnUiThread {
                if (ticket != generation || isFinishing || isDestroyed) return@runOnUiThread
                download = null
                result.fold(onSuccess = { id ->
                    val file = GifStore(this).file(id) ?: return@fold
                    showStatus(null)
                    dialog?.dismiss()
                    dialog = AlertDialog.Builder(this)
                        .setTitle(R.string.gif_use)
                        .setView(GifThumbnail(this, file).apply { minimumHeight = (200 * resources.displayMetrics.density).toInt() })
                        .setMessage(R.string.gif_return_hint)
                        .setPositiveButton(R.string.gif_use) { _, _ ->
                            if (GifTransfer.confirmSearch(intent.getStringExtra(SEARCH_TOKEN), id,
                                    android.os.SystemClock.elapsedRealtime())) {
                                showQueryOnWindowFocus = false
                                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                                    .hideSoftInputFromWindow(query.windowToken, 0)
                                finish()
                            } else showStatus(R.string.gif_search_expired)
                        }.setNegativeButton(android.R.string.cancel, null).show()
                    styleDialogButtons()
                }, onFailure = { showStatus(R.string.gif_download_error) })
            }
        }
    }

    private fun cancelDownload() { generation++; download?.cancel(true); download = null; dialog?.dismiss(); dialog = null }

    @Deprecated("Legacy back dispatch for API 26 compatibility")
    override fun onBackPressed() {
        if (download != null) { cancelDownload(); showStatus(null) }
        else if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query.text.toString())
        outState.putBoolean("editingQuery", query.hasFocus())
        web.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (isFinishing) GifTransfer.cancelSearch(intent.getStringExtra(SEARCH_TOKEN))
        cancelDownload(); worker.shutdownNow()
        if (::web.isInitialized) { (web.parent as? LinearLayout)?.removeView(web); web.destroy() }
        super.onDestroy()
    }
    companion object {
        const val SHEET_HEIGHT_DP = "gif_sheet_height_dp"
        const val SEARCH_TOKEN = "gif_search_token"
    }
}
