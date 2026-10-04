package expo.modules.subtext.messenger

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import expo.modules.subtext.R
import expo.modules.subtext.SubtextRuntime
import kotlinx.coroutines.*
import org.json.JSONObject

class SubtextLoginActivity : Activity() {
  private val isInstagram get() = intent.getStringExtra("network") == "instagram"
  private val loginUrl get() = if (isInstagram) "https://www.instagram.com/accounts/login/" else "https://www.facebook.com/login/"
  private val cookieUrls get() = if (isInstagram) listOf("https://www.instagram.com", "https://instagram.com")
    else listOf("https://www.facebook.com", "https://facebook.com")
  private val requiredCookies get() = if (isInstagram) listOf("sessionid", "csrftoken", "ds_user_id") else listOf("c_user", "xs", "datr")
  private val loadingTitle get() = if (isInstagram) R.string.cue_instagram_loading else R.string.cue_login_loading
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var browser: WebView
  private lateinit var state: View
  private lateinit var spinner: View
  private lateinit var stateTitle: TextView
  private lateinit var stateDetail: TextView
  private lateinit var retry: View
  private lateinit var reload: View
  private lateinit var progress: ProgressBar
  private lateinit var domain: TextView
  private lateinit var notice: TextView
  private var submitting = false
  private var pageFailed = false
  private var connectionFailed = false
  private var desktopMode = false
  private var loadTimeout: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = !dark
      isAppearanceLightNavigationBars = !dark
    }
    setContentView(R.layout.cue_messenger_login)
    if (isInstagram) {
      findViewById<TextView>(R.id.cue_login_network).text = "Instagram"
      findViewById<TextView>(R.id.cue_login_heading).setText(R.string.cue_instagram_heading)
      findViewById<TextView>(R.id.cue_login_description).setText(R.string.cue_instagram_description)
      findViewById<TextView>(R.id.cue_login_footer).setText(R.string.cue_instagram_footer)
      findViewById<TextView>(R.id.cue_login_state_title).setText(loadingTitle)
    }
    val root = findViewById<View>(R.id.cue_login_root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
      view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
      val compact = insets.isVisible(WindowInsetsCompat.Type.ime())
      findViewById<View>(R.id.cue_login_intro).visibility = if (compact) View.GONE else View.VISIBLE
      findViewById<View>(R.id.cue_login_footer).visibility = if (compact) View.GONE else View.VISIBLE
      insets
    }
    ViewCompat.requestApplyInsets(root)
    state = findViewById(R.id.cue_login_state)
    spinner = findViewById(R.id.cue_login_spinner)
    stateTitle = findViewById(R.id.cue_login_state_title)
    stateDetail = findViewById(R.id.cue_login_state_detail)
    retry = findViewById(R.id.cue_login_retry)
    reload = findViewById(R.id.cue_login_reload)
    progress = findViewById(R.id.cue_login_progress)
    domain = findViewById(R.id.cue_login_domain)
    notice = findViewById(R.id.cue_login_notice)
    findViewById<View>(R.id.cue_login_close).setOnClickListener { finish() }
    reload.setOnClickListener { reloadPage() }
    findViewById<View>(R.id.cue_login_options).setOnClickListener { anchor ->
      PopupMenu(this, anchor).apply {
        menu.add(if (desktopMode) R.string.cue_login_mobile else R.string.cue_login_desktop)
        setOnMenuItemClickListener {
          if (!submitting) {
            desktopMode = !desktopMode
            val original = WebSettings.getDefaultUserAgent(this@SubtextLoginActivity)
            browser.settings.userAgentString = if (desktopMode) desktopChromeUserAgent(original) else if (isInstagram) original else mobileChromeUserAgent(original)
            notice.visibility = View.GONE
            browser.stopLoading()
            // The desktop fallback must leave the mobile host as well as change the agent.
            val current = browser.url ?: loginUrl
            browser.loadUrl(if (desktopMode) current.replace("https://m.facebook.com", "https://www.facebook.com") else current)
          }
          true
        }
        show()
      }
    }
    retry.setOnClickListener {
      if (connectionFailed) {
        connectionFailed = false
        submitting = false
        collectCookies()
      } else reloadPage()
    }
    browser = createMetaLoginWebView(
      if (isInstagram) MetaUserAgent.WEBVIEW else MetaUserAgent.MOBILE_CHROME,
      onPageFinished = {
        loadTimeout?.cancel()
        if (!pageFailed && !submitting) {
          state.visibility = View.GONE
          progress.visibility = View.INVISIBLE
          collectCookies()
        }
      },
      onBlocked = {
        notice.setText(if (isInstagram) R.string.cue_instagram_checkpoint else R.string.cue_login_checkpoint)
        notice.setTextColor(getColor(R.color.cue_login_secondary))
        notice.setBackgroundColor(getColor(R.color.cue_login_border))
        notice.visibility = View.VISIBLE
      },
      onPageStarted = { url ->
        if (!submitting) {
          pageFailed = false
          domain.text = Uri.parse(url.orEmpty()).host?.removePrefix("www.") ?: if (isInstagram) "instagram.com" else "facebook.com"
          showState(loadingTitle, R.string.cue_login_loading_detail)
          progress.progress = 0
          progress.visibility = View.VISIBLE
          loadTimeout?.cancel()
          loadTimeout = scope.launch {
            delay(30_000)
            if (!submitting && state.visibility == View.VISIBLE) showPageError()
          }
        }
      },
      onProgress = { value ->
        if (!pageFailed && !submitting) progress.progress = value
      },
      onLoadError = { if (!submitting) showPageError() },
    )
    findViewById<FrameLayout>(R.id.cue_login_browser).addView(browser, 0, FrameLayout.LayoutParams(-1, -1))
    browser.loadUrl(loginUrl)
    scope.launch { while (isActive) { delay(1000); collectCookies() } }
  }

  private fun showState(title: Int, detail: Int, failed: Boolean = false) {
    stateTitle.setText(title)
    stateDetail.setText(detail)
    spinner.visibility = if (failed) View.GONE else View.VISIBLE
    retry.visibility = if (failed) View.VISIBLE else View.GONE
    state.visibility = View.VISIBLE
  }

  private fun showPageError() {
    pageFailed = true
    loadTimeout?.cancel()
    progress.visibility = View.INVISIBLE
    showState(R.string.cue_login_page_error, R.string.cue_login_page_error_detail, failed = true)
  }

  private fun reloadPage() {
    if (submitting) return
    notice.visibility = View.GONE
    browser.stopLoading()
    browser.loadUrl(browser.url?.takeIf { isMetaLoginHost(Uri.parse(it).host) } ?: loginUrl)
  }

  override fun onResume() {
    super.onResume()
    if (::browser.isInitialized && !submitting) browser.resumeMetaCheckpoint()
  }

  private fun collectCookies() {
    if (submitting || isFinishing || isDestroyed || pageFailed) return
    val cookies = CookieManager.getInstance()
    val values = linkedMapOf<String, String>()
    cookieUrls.forEach { url ->
      cookies.getCookie(url).orEmpty().split(';').forEach { pair ->
        val at = pair.indexOf('=')
        if (at > 0) values[pair.substring(0, at).trim()] = pair.substring(at + 1).trim()
      }
    }
    if (requiredCookies.any { values[it].isNullOrBlank() }) return
    submitting = true
    loadTimeout?.cancel()
    notice.visibility = View.GONE
    reload.isEnabled = false
    reload.alpha = 0.35f
    progress.visibility = View.INVISIBLE
    showState(if (isInstagram) R.string.cue_instagram_connecting else R.string.cue_login_connecting, R.string.cue_login_connecting_detail)
    scope.launch {
      val result = runCatching {
        val runtime = SubtextRuntime.get(this@SubtextLoginActivity)
        runtime.startConnections()
        (if (isInstagram) runtime.instagram else runtime.messenger).login(JSONObject(values as Map<*, *>).toString()).getOrThrow()
      }
      result.onSuccess {
        clearLoginCookies()
        finish()
      }.onFailure { error ->
        if (error is CancellationException) throw error
        connectionFailed = true
        showState(if (isInstagram) R.string.cue_instagram_failed else R.string.cue_login_failed, R.string.cue_login_page_error_detail, failed = true)
        // Retry requires a tap, so rejected cookies never cause an automatic login loop.
      }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    if (::browser.isInitialized) {
      (browser.parent as? FrameLayout)?.removeView(browser)
      browser.stopLoading()
      browser.destroy()
    }
    super.onDestroy()
  }

  private fun clearLoginCookies() {
    val cookies = CookieManager.getInstance()
    val host = if (isInstagram) "instagram.com" else "facebook.com"
    cookieUrls.forEach { url ->
      cookies.getCookie(url).orEmpty().split(';').forEach { pair ->
        val name = pair.substringBefore('=').trim()
        if (name.isNotBlank()) {
          cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
          cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$host; Secure")
        }
      }
    }
    cookies.flush()
  }
}
