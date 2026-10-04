package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject

/** Local, bounded source excerpts survive the rolling conversation cache. Never sent as extra AI context. */
internal object MemoryEvidence {
  fun capture(ids: List<String>, messages: JSONArray): JSONArray {
    val source = (0 until messages.length()).map { messages.getJSONObject(it) }.associateBy { it.optString("id") }
    return JSONArray(ids.distinct().take(8).mapNotNull { id -> source[id]?.let { message ->
      JSONObject().put("id", id).put("text", message.optString("text").take(4000))
        .put("sender", message.optString("sender").take(160)).put("timestamp", message.optLong("timestamp"))
        .put("isMe", message.optBoolean("isMe"))
    } })
  }
}
