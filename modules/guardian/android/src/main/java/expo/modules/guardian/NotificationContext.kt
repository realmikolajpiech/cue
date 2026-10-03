package expo.modules.guardian

import org.json.JSONArray
import org.json.JSONObject

/** Only elevated-risk results retain a short snapshot of the actual inference input. */
object NotificationContext {
  fun attach(result: JSONObject, title: String, postedAt: Long, messages: List<PrivateMessage>): JSONObject {
    if (result.getString("risk") !in setOf("medium", "high") || messages.isEmpty()) return result
    val previews = messages.takeLast(5).map { message ->
      JSONObject().put("sender", message.sender.take(120))
        .put("text", message.text.take(300) + if (message.text.length > 300) "…" else "")
    }
    return result.put("notificationContext", JSONObject()
      .put("title", title.take(120)).put("receivedAt", postedAt)
      .put("messages", JSONArray(previews)))
  }
}
