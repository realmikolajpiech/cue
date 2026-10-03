package expo.modules.subtext

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Public project credentials only. DeepSeek credentials never enter the APK. */
class SupabaseGateway(context: Context) {
  private val sessionStore = SecureValue(context, "subtext_supabase_session")
  private fun request(path: String, body: JSONObject, token: String? = null): Pair<Int, JSONObject> {
    val connection = URL("$BASE$path").openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 20000; connection.readTimeout = 70000
      connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
      connection.setRequestProperty("Content-Type", "application/json")
      if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
      connection.doOutput = true
      connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      return code to (runCatching { JSONObject(text) }.getOrNull() ?: JSONObject())
    } finally { connection.disconnect() }
  }
  @Synchronized private fun accessToken(forceRefresh: Boolean = false): String {
    val saved = sessionStore.get()?.let { runCatching { JSONObject(it) }.getOrNull() }
    if (!forceRefresh && saved != null && saved.optLong("expires_at") > System.currentTimeMillis() / 1000 + 60) {
      return saved.getString("access_token")
    }
    val refresh = saved?.optString("refresh_token")?.takeIf { it.isNotBlank() }
    var result = if (refresh != null) request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
      else request("/auth/v1/signup", JSONObject())
    // Only an invalid/expired refresh credential should create another anonymous session.
    if (refresh != null && result.first in listOf(400, 401, 403)) result = request("/auth/v1/signup", JSONObject())
    check(result.first in 200..299) { "Nie można połączyć z AI. Sprawdź połączenie i konfigurację anonimowego dostępu Supabase (HTTP ${result.first})." }
    val session = result.second
    session.put("expires_at", System.currentTimeMillis() / 1000 + session.getLong("expires_in"))
    val token = session.getString("access_token")
    sessionStore.set(JSONObject().put("access_token", token).put("refresh_token", session.getString("refresh_token"))
      .put("expires_at", session.getLong("expires_at")).toString())
    return token
  }
  fun analyze(body: JSONObject): JSONObject {
    var result = request("/functions/v1/deepseek-analyze", body, accessToken())
    if (result.first == 401) result = request("/functions/v1/deepseek-analyze", body, accessToken(true))
    check(result.first in 200..299) { when (result.first) {
      429 -> "Osiągnięto limit analiz. Spróbuj ponownie później."
      503 -> "AI nie jest jeszcze skonfigurowane na serwerze."
      402 -> "Brak środków na koncie AI."
      else -> "Analiza AI jest niedostępna (HTTP ${result.first}). Spróbuj ponownie."
    } }
    return result.second
  }
  companion object {
    private const val BASE = "https://qajdybynwafehizuaxad.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_25tSEu3C4wIMY7Q5e0HqAg_ZPeFV_a8"
  }
}
