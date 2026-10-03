package expo.modules.subtext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object DeepSeek {
  const val MODEL = "deepseek-flash"
  suspend fun analyze(gateway: SupabaseGateway, messages: JSONArray, draft: String,
    memory: JSONObject = JSONObject(), memoryOnly: Boolean = false, images: JSONArray = JSONArray()): JSONObject = withContext(Dispatchers.IO) {
    validate(gateway.analyze(userContent(messages, draft, generalHistory = JSONArray())
      .put("personMemory", PersonMemory.input(memory)).put("memoryOnly", memoryOnly).put("images", images)), messages, memoryOnly)
  }

  // Balance the general sample so one prolific conversation cannot dominate it.
  internal fun generalWritingHistory(rooms: List<JSONObject>): JSONArray {
    val own = rooms.filterNot { it.optBoolean("demo") }.flatMap { room ->
      val history = room.optJSONArray("messages") ?: JSONArray()
      (0 until history.length()).map { history.getJSONObject(it) }
        .filter { it.optBoolean("isMe", false) && usableSample(it.optString("text")) }.takeLast(10)
    }.sortedBy { it.optLong("timestamp") }.takeLast(80)
    return JSONArray(own)
  }
  private fun usableSample(text: String): Boolean = text.isNotBlank() && text.length <= 500 &&
    !text.startsWith(MessageMedia.PHOTO) && text.any(Char::isLetter) && !Regex("https?://|sk-[A-Za-z0-9_-]{12,}", RegexOption.IGNORE_CASE).containsMatchIn(text)

  internal fun userContent(messages: JSONArray, draft: String, history: JSONArray = messages,
    generalHistory: JSONArray = history): JSONObject {
    fun samples(input: JSONArray, limit: Int) = (0 until input.length()).map { input.getJSONObject(it) }
      .filter { it.optBoolean("isMe", false) }.map { it.optString("text").trim() }
      .filter(::usableSample).takeLast(limit)
    val local = samples(history, 40)
    val general = samples(generalHistory, 80)
    // A handful of acknowledgements is not enough to infer a relationship-specific voice.
    val enoughLocal = local.size >= 8 && local.sumOf { it.length } >= 160
    val source = if (enoughLocal) "conversation" else if (general.isNotEmpty()) "general" else "insufficient"
    return JSONObject().put("messages", messages).put("draft", draft.take(4000))
      .put("styleInput", JSONObject().put("conversationExamples", JSONArray(local))
        .put("generalExamples", JSONArray(general)).put("activeSource", source))
  }
  fun validate(raw: JSONObject, messages: JSONArray, memoryOnly: Boolean = false): JSONObject {
    val ids = (0 until messages.length()).map { messages.getJSONObject(it).getString("id") }.toSet()
    val clean = JSONObject().put("summary", raw.getString("summary").take(1200))
      .put("beforeReply", raw.getString("beforeReply").take(1200))
    val updates = raw.optJSONArray("memoryUpdates") ?: JSONArray()
    val cleanUpdates = JSONArray()
    for (i in 0 until minOf(updates.length(), 8)) {
      val update = updates.optJSONObject(i) ?: continue
      val evidence = update.optJSONArray("evidenceIds") ?: continue
      val valid = (0 until evidence.length()).map { evidence.optString(it) }.distinct().filter { it in ids }.take(8)
      if (valid.isNotEmpty()) cleanUpdates.put(JSONObject().put("replaceId", update.optString("replaceId").take(64))
        .put("text", update.optString("text").take(600)).put("evidenceIds", JSONArray(valid)))
    }
    clean.put("memoryUpdates", cleanUpdates)
    val reminderUpdates = raw.optJSONArray("reminderUpdates") ?: JSONArray()
    // Typed fields, dates and evidence are checked again against the saved memory by the store.
    clean.put("reminderUpdates", JSONArray((0 until minOf(reminderUpdates.length(), 8)).mapNotNull { i ->
      reminderUpdates.optJSONObject(i)?.let { item ->
        val evidence = item.optJSONArray("evidenceIds") ?: JSONArray()
        val valid = (0 until evidence.length()).map { evidence.optString(it) }.distinct().filter { it in ids }.take(8)
        if (valid.isEmpty()) null else JSONObject().put("replaceId", item.optString("replaceId").take(64))
          .put("text", item.optString("text").take(400)).put("kind", item.optString("kind").take(20))
          .put("owner", item.optString("owner").take(10)).put("status", item.optString("status").take(20))
          .put("dueDate", item.optString("dueDate").take(40)).put("evidenceIds", JSONArray(valid))
      }
    }))
    raw.optJSONObject("writingStyle")?.let { style ->
      val habits = style.optJSONArray("habits") ?: JSONArray()
      clean.put("writingStyle", JSONObject()
        .put("summary", style.optString("summary").take(600))
        .put("general", style.optString("general").take(800))
        .put("conversation", style.optString("conversation").take(800))
        .put("habits", JSONArray((0 until minOf(habits.length(), 8)).map { habits.optString(it).take(200) })))
    }
    for (field in listOf("observations", "commitments")) {
      val input = raw.getJSONArray(field); val output = JSONArray()
      for (i in 0 until minOf(input.length(), 8)) {
        val item = input.getJSONObject(i); val evidence = item.getJSONArray("evidenceIds")
        val valid = (0 until evidence.length()).map { evidence.getString(it) }.distinct().filter { it in ids }
        if (valid.isNotEmpty()) output.put(JSONObject().put("text", item.getString("text").take(600)).put("evidenceIds", JSONArray(valid)))
      }
      clean.put(field, output)
    }
    val suggestions = raw.getJSONArray("suggestions"); val output = JSONArray()
    for (i in 0 until minOf(suggestions.length(), 3)) {
      val item = suggestions.getJSONObject(i)
      val action = item.optString("action", "reply")
      require(action == "reply" || action == "no_reply") { "Nieznany rodzaj sugestii AI." }
      val suggestion = JSONObject().put("action", action)
      if (action == "no_reply") {
        require(item.optString("reason").isNotBlank()) { "Brak uzasadnienia nieodpisywania." }
        suggestion.put("tone", "Nie odpisuj").put("text", "").put("reason", item.getString("reason").take(600))
      } else {
        require(item.getString("text").isNotBlank()) { "Pusta sugestia AI." }
        suggestion.put("tone", item.getString("tone").take(60)).put("text", item.getString("text").take(2000))
        if (item.optString("reason").isNotBlank()) suggestion.put("reason", item.getString("reason").take(600))
      }
      output.put(suggestion)
    }
    require(memoryOnly || output.length() > 0) { "AI nie zwróciło podpowiedzi." }
    return clean.put("suggestions", output).put("createdAt", System.currentTimeMillis()).put("model", MODEL).put("messageCount", messages.length())
  }
}
