package expo.modules.subtext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object DeepSeek {
  const val MODEL = "deepseek-flash"
  suspend fun analyze(gateway: SupabaseGateway, messages: JSONArray, draft: String): JSONObject = withContext(Dispatchers.IO) {
    validate(gateway.analyze(messages, draft.take(4000)), messages)
  }
  fun validate(raw: JSONObject, messages: JSONArray): JSONObject {
    val ids = (0 until messages.length()).map { messages.getJSONObject(it).getString("id") }.toSet()
    val clean = JSONObject().put("summary", raw.getString("summary").take(1200))
      .put("beforeReply", raw.getString("beforeReply").take(1200))
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
      require(item.getString("text").isNotBlank()) { "Pusta sugestia AI." }
      output.put(JSONObject().put("tone", item.getString("tone").take(60)).put("text", item.getString("text").take(2000)))
    }
    require(output.length() > 0) { "AI nie zwróciło podpowiedzi." }
    return clean.put("suggestions", output).put("createdAt", System.currentTimeMillis()).put("model", MODEL).put("messageCount", messages.length())
  }
}
