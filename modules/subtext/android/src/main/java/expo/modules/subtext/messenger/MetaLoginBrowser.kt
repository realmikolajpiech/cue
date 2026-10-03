// Adapted from realmikolajpiech/arie, commit ff217daadf321eca73e5245d6a85e96b059f8e8d.
package expo.modules.subtext.messenger

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Which browser identity the sign-in page sees. */
internal enum class MetaUserAgent {
    /** Mobile Chrome identity without WebView markers, for phone-sized sign-in controls. */
    MOBILE_CHROME,
    /** Desktop Chrome. m.facebook.com's cookie dialog never paints in WebView (grey screen). */
    DESKTOP_CHROME,

    /** The stock WebView agent, which instagram.com renders correctly. */
    WEBVIEW,
}

/**
 * Embedded Facebook/Instagram sign-in that looks less like an app WebView to Meta.
 *
 * Meta treats obvious WebViews (the `wv` user-agent token and the `X-Requested-With` header) as
 * untrusted: the "approve from your other device" checkpoint then spins forever. We drop the
 * header, let `intent://` / `fb://` links open the Meta apps so the user can approve there, and
 * reload a pending checkpoint when the user comes back.
 */
@SuppressLint("SetJavaScriptEnabled", "RestrictedApi") // RestrictedApi: X-Requested-With allow-list, guarded by a runtime feature check.
internal fun Context.createMetaLoginWebView(
    userAgent: MetaUserAgent,
    onPageFinished: (String?) -> Unit,
    onBlocked: (Uri) -> Unit,
    onPageStarted: (String?) -> Unit = {},
    onProgress: (Int) -> Unit = {},
    onLoadError: () -> Unit = {},
): WebView = WebView(this).also { browser ->
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(browser, true)
    // Compose's AndroidView defaults to WRAP_CONTENT, which makes Chromium report a zero-height
    // viewport: `100vh` becomes 0 and Meta's dialogs (e.g. "Try another way") render invisibly.
    browser.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    browser.setBackgroundColor(android.graphics.Color.WHITE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) browser.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    browser.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        // Honor Meta's viewport and fit desktop fallback pages to the phone.
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        if (userAgent == MetaUserAgent.DESKTOP_CHROME) {
            userAgentString = desktopChromeUserAgent(WebSettings.getDefaultUserAgent(this@createMetaLoginWebView))
        } else if (userAgent == MetaUserAgent.MOBILE_CHROME) {
            userAgentString = mobileChromeUserAgent(WebSettings.getDefaultUserAgent(this@createMetaLoginWebView))
        }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(browser.settings, emptySet())
    }
    browser.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onPageStarted(url)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) onLoadError()
        }

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
            if (request?.isForMainFrame == true && (response?.statusCode ?: 0) >= 400) onLoadError()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return true
            return when (classifyMetaNavigation(uri)) {
                MetaNavigation.LOAD -> false
                MetaNavigation.OPEN_META_APP -> { openMetaApp(uri); true }
                MetaNavigation.BLOCK -> {
                    // Sub-frames (captcha, telemetry) cannot take over the page; only police the main frame.
                    if (!request.isForMainFrame) return false
                    onBlocked(uri)
                    true
                }
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            cookies.flush()
            onPageFinished(url)
        }
    }
    browser.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) { onProgress(newProgress) }

        // Meta opens some login steps with window.open; keep them in the same, policed browser.
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val popup = WebView(view.context)
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(popupView: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.let { uri ->
                        when (classifyMetaNavigation(uri)) {
                            MetaNavigation.LOAD -> browser.loadUrl(uri.toString())
                            MetaNavigation.OPEN_META_APP -> openMetaApp(uri)
                            MetaNavigation.BLOCK -> onBlocked(uri)
                        }
                    }
                    popupView?.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }
}

/**
 * Reloads a waiting sign-in checkpoint so an approval made in the Facebook/Instagram app is picked
 * up. Skipped when the page shows a code field: the user likely left to fetch an SMS or
 * authenticator code, and a reload could reset the verification method they picked.
 */
internal fun WebView.resumeMetaCheckpoint() {
    if (!isMetaCheckpoint(url)) return
    evaluateJavascript(HAS_CODE_FIELD_JS) { hasCodeField -> if (hasCodeField != "true") reload() }
}

private const val HAS_CODE_FIELD_JS =
    "(function(){return Array.from(document.querySelectorAll('input')).some(function(i){" +
        "return ['text','tel','number'].indexOf(i.type)>=0&&i.offsetParent!==null;});})()"

private fun Context.openMetaApp(uri: Uri) {
    val intent = if (uri.scheme == "intent") {
        runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?.apply {
                // Never let web content target a component or grant URI permissions.
                component = null
                selector = null
                flags = 0
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
    } else {
        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    } ?: return
    if (intent.`package` != null && intent.`package` !in META_PACKAGES) return
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // The Meta app is not installed; the user can still choose another verification method.
    }
}

internal enum class MetaNavigation { LOAD, OPEN_META_APP, BLOCK }

internal fun classifyMetaNavigation(uri: Uri): MetaNavigation =
    classifyMetaNavigation(uri.scheme, uri.host, intentPackage = uri.toString().substringAfter(";package=", "").substringBefore(';'))

internal fun classifyMetaNavigation(scheme: String?, host: String?, intentPackage: String = ""): MetaNavigation {
    return when (scheme?.lowercase()) {
        "https" -> if (isMetaLoginHost(host)) MetaNavigation.LOAD else MetaNavigation.BLOCK
        "fb", "fb-messenger", "instagram" -> MetaNavigation.OPEN_META_APP
        "intent" -> if (intentPackage in META_PACKAGES) MetaNavigation.OPEN_META_APP else MetaNavigation.BLOCK
        else -> MetaNavigation.BLOCK
    }
}

internal fun isMetaLoginHost(host: String?): Boolean {
    val normalized = host?.lowercase().orEmpty()
    return META_HOSTS.any { normalized == it || normalized.endsWith(".$it") }
}

internal fun isMetaCheckpoint(url: String?): Boolean {
    val normalized = url?.lowercase() ?: return false
    return CHECKPOINT_MARKERS.any(normalized::contains)
}

/**
 * Desktop Chrome agent carrying the device's real Chrome version, so Meta does not flag an
 * outdated browser. Falls back to a fixed recent version when the WebView agent has none.
 */
internal fun desktopChromeUserAgent(webViewUserAgent: String): String {
    val version = Regex("""Chrome/([\d.]+)""").find(webViewUserAgent)?.groupValues?.get(1)
        ?.substringBefore('.')?.let { "$it.0.0.0" } ?: FALLBACK_CHROME_VERSION
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
}

internal fun mobileChromeUserAgent(webViewUserAgent: String): String =
    webViewUserAgent.replace("; wv", "").replace(" Version/4.0", "")

private val META_HOSTS = listOf("facebook.com", "messenger.com", "instagram.com", "meta.com", "fb.com")
private val META_PACKAGES = setOf("com.facebook.katana", "com.facebook.orca", "com.instagram.android", "com.facebook.lite")
private val CHECKPOINT_MARKERS = listOf("checkpoint", "two_step_verification", "two_factor", "login/device-based", "/challenge")
private const val FALLBACK_CHROME_VERSION = "140.0.0.0"
