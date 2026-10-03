package expo.modules.subtext.messenger

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.*
import android.view.View
import expo.modules.subtext.SubtextRuntime
import kotlinx.coroutines.*
import org.json.JSONObject

class SubtextLoginActivity : Activity() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var browser: WebView
  private lateinit var status: TextView
  private var submitting = false
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
    layout.addView(Button(this).apply { text = "Wróć do Subtext"; setOnClickListener { finish() } })
    status = TextView(this).apply { text = "Zaloguj się do Facebooka, aby połączyć Messengera."; setPadding(24, 12, 24, 12) }
    layout.addView(status)
    browser = createMetaLoginWebView(MetaUserAgent.DESKTOP_CHROME,
      onPageFinished = { collectCookies() }, onBlocked = { status.text = "Otwórz ten krok w aplikacji Facebook, a potem wróć tutaj." })
    layout.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(layout)
    browser.loadUrl("https://www.facebook.com/login/")
    scope.launch { while (isActive) { delay(1000); collectCookies() } }
  }
  override fun onResume() { super.onResume(); if (::browser.isInitialized && !submitting) browser.resumeMetaCheckpoint() }
  private fun collectCookies() {
    if (submitting || isFinishing) return
    val cookies = CookieManager.getInstance()
    val values = linkedMapOf<String, String>()
    listOf("https://www.facebook.com", "https://facebook.com").forEach { url ->
      cookies.getCookie(url).orEmpty().split(';').forEach { pair ->
        val at = pair.indexOf('=')
        if (at > 0) values[pair.substring(0, at).trim()] = pair.substring(at + 1).trim()
      }
    }
    if (listOf("c_user", "xs", "datr").any { values[it].isNullOrBlank() }) return
    submitting = true; status.text = "Łączenie z Messengerem…"; browser.visibility = View.INVISIBLE
    scope.launch {
      val runtime = SubtextRuntime.get(this@SubtextLoginActivity)
      runtime.startConnections()
      runtime.messenger.login(JSONObject(values as Map<*, *>).toString()).onSuccess {
        cookies.removeAllCookies(null); finish()
      }.onFailure {
        status.text = "Nie udało się połączyć. Wróć i spróbuj ponownie."; browser.visibility = View.VISIBLE
        // Do not repeatedly retry the same rejected cookies automatically.
      }
    }
  }
  override fun onDestroy() { scope.cancel(); if (::browser.isInitialized) browser.destroy(); super.onDestroy() }
}
