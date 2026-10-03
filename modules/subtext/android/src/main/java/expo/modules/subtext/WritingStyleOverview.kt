package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject

/** Local, authentic examples. Never substitute invented replies for the user's voice. */
internal fun writingStyleOverview(rooms: List<JSONObject>): JSONObject {
  val real = rooms.filterNot { it.optBoolean("demo") }
  val samples = DeepSeek.generalWritingHistory(real)
  val texts = (0 until samples.length()).map { samples.getJSONObject(it).getString("text") }
  val habits = mutableListOf<String>()
  if (texts.size >= 5) {
    if (texts.count { it.length <= 60 } * 2 >= texts.size) habits.add("Krótkie wiadomości")
    if (texts.count { it.firstOrNull(Char::isLetter)?.isLowerCase() == true } * 2 >= texts.size) habits.add("Małe litery")
    if (texts.count { it.lastOrNull() !in listOf('.', '!', '?') } * 2 >= texts.size) habits.add("Bez końcowych kropek")
    if (texts.count { text -> text.codePoints().anyMatch { it >= 0x1F000 } } * 4 >= texts.size) habits.add("Emoji w rozmowie")
  }
  val profile = real.mapNotNull { it.optJSONObject("profile") }
    .filter { it.optJSONObject("writingStyle")?.optString("general")?.isNotBlank() == true }
    .maxByOrNull { it.optLong("createdAt") }
  val candidates = real.map { room ->
    val history = room.optJSONArray("messages") ?: JSONArray()
    (1 until history.length()).mapNotNull { i ->
      val incoming = history.getJSONObject(i - 1)
      val outgoing = history.getJSONObject(i)
      if (incoming.optBoolean("isMe") || !outgoing.optBoolean("isMe") ||
        incoming.optString("text").length !in 1..220 || outgoing.optString("text").length !in 1..220 ||
        outgoing.optLong("timestamp") - incoming.optLong("timestamp") !in 0..86_400_000 ||
        Regex("https?://|sk-[A-Za-z0-9_-]{12,}", RegexOption.IGNORE_CASE)
          .containsMatchIn(incoming.optString("text") + outgoing.optString("text"))) null
      else JSONObject().put("id", room.getString("id") + ":" + outgoing.getString("id"))
        .put("incoming", incoming.getString("text")).put("reply", outgoing.getString("text"))
        .put("timestamp", outgoing.optLong("timestamp"))
    }.sortedByDescending { it.optLong("timestamp") }
  }
  // Prefer varied conversations, then fill remaining slots with other exchanges.
  val examples = (candidates.mapNotNull { it.firstOrNull() } + candidates.flatten())
    .distinctBy { it.getString("reply") }.take(5)
  return JSONObject().put("sampleCount", texts.size)
    .put("conversationCount", real.count { room ->
      val history = room.optJSONArray("messages") ?: JSONArray()
      (0 until history.length()).any { history.getJSONObject(it).optBoolean("isMe") }
    }).put("summary", profile?.getJSONObject("writingStyle")?.optString("general") ?: "")
    .put("habits", JSONArray(habits)).put("examples", JSONArray(examples))
}
