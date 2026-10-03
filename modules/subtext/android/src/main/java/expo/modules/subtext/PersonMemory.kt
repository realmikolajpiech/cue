package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** One private conversation, one persistent memory. No cross-network identity matching. */
internal object PersonMemory {
  private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
  private fun digest(id: String): String {
    val digits = "0123456789abcdef"
    return buildString(64) {
      MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8)).forEach { byte ->
        val value = byte.toInt() and 255
        append(digits[value ushr 4]); append(digits[value and 15])
      }
    }
  }
  private fun usable(text: String) = text.length in 1..2000 && text.any(Char::isLetter) &&
    !Regex("https?://|sk-[A-Za-z0-9_-]{12,}", RegexOption.IGNORE_CASE).containsMatchIn(text)

  fun update(memory: JSONObject, history: List<JSONObject>, now: Long = System.currentTimeMillis()) {
    val seen = memory.optJSONObject("seen") ?: JSONObject()
    val recent = objects(memory.optJSONArray("recent") ?: JSONArray()).toMutableList()
    val examples = objects(memory.optJSONArray("examples") ?: JSONArray()).associateBy { it.getString("id") }.toMutableMap()
    val phrases = memory.optJSONObject("phraseCounts") ?: JSONObject()
    var count = memory.optInt("sampleCount")
    var revision = memory.optLong("revision")
    val floor = memory.optLong("seenBefore", Long.MIN_VALUE)
    val sorted = history.sortedBy { it.optLong("timestamp") }
    sorted.forEachIndexed { index, message ->
      val id = message.optString("id")
      val stamp = message.optLong("timestamp")
      if (id.isBlank() || seen.has(digest(id)) || stamp <= floor || message.optString("text").isBlank()) return@forEachIndexed
      seen.put(digest(id), stamp)
      revision++
      val text = message.optString("text").trim()
      if (!message.optBoolean("isMe") || !usable(text)) return@forEachIndexed
      count++
      recent.add(JSONObject().put("text", text).put("timestamp", stamp))
      val words = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?").findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
      val terms = mutableSetOf<String>()
      if (text.length <= 80) terms.add(text.lowercase(Locale.ROOT))
      for (size in 2..3) words.windowed(size).forEach { terms.add(it.joinToString(" ")) }
      terms.filter { it.length in 3..80 }.forEach { phrases.put(it, phrases.optInt(it) + 1) }
      val incoming = sorted.getOrNull(index - 1)?.takeIf { !it.optBoolean("isMe") &&
        stamp - it.optLong("timestamp") in 0..86_400_000 && usable(it.optString("text")) }
      examples[id] = JSONObject().put("id", id).put("incoming", incoming?.optString("text") ?: "")
        .put("reply", text).put("timestamp", stamp)
    }
    if (revision == memory.optLong("revision")) return
    // Keep a deduplication window and a timestamp floor so old synchronization cannot inflate counts.
    if (seen.length() > 4096) {
      val expired = seen.keys().asSequence().toList().sortedBy { seen.getLong(it) }.take(seen.length() - 3072)
      val cutoff = expired.maxOf { seen.getLong(it) }
      seen.keys().asSequence().toList().filter { seen.getLong(it) <= cutoff }.forEach { seen.remove(it) }
      memory.put("seenBefore", maxOf(floor, cutoff))
    }
    phrases.keys().asSequence().toList().sortedByDescending { phrases.getInt(it) }.drop(256).forEach { phrases.remove(it) }
    val unique = examples.values.sortedByDescending { it.optLong("timestamp") }.distinctBy { it.getString("reply") }
    val retained = (unique.sortedByDescending { it.getString("reply").length }.take(4) + unique.take(36)).distinctBy { it.getString("id") }
    memory.put("seen", seen).put("sampleCount", count).put("revision", revision).put("updatedAt", now)
      .put("recent", JSONArray(recent.sortedBy { it.optLong("timestamp") }.takeLast(60)))
      .put("phraseCounts", phrases).put("examples", JSONArray(retained))
  }

  fun overview(memory: JSONObject): JSONObject {
    val texts = objects(memory.optJSONArray("recent") ?: JSONArray()).map { it.getString("text") }
    val habits = JSONArray()
    val traits = JSONArray()
    fun trait(label: String, matches: Int, threshold: Double) {
      if (texts.size >= 5 && matches.toDouble() / texts.size >= threshold) {
        habits.put(label)
        traits.put(JSONObject().put("text", label).put("matches", matches).put("sampleSize", texts.size))
      }
    }
    trait("Krótkie i zwięzłe wiadomości", texts.count { it.length <= 80 }, .6)
    trait("Dłuższe, rozwinięte odpowiedzi", texts.count { it.length >= 160 }, .4)
    trait("Małe litery na początku", texts.count { it.firstOrNull(Char::isLetter)?.isLowerCase() == true }, .6)
    trait("Wielka litera na początku", texts.count { it.firstOrNull(Char::isLetter)?.isUpperCase() == true }, .6)
    trait("Bez końcowych kropek", texts.count { it.lastOrNull() !in listOf('.', '!', '?') }, .6)
    trait("Emoji w rozmowie", texts.count { it.codePoints().anyMatch { cp -> cp >= 0x1F000 || cp in 0x2600..0x27BF } }, .25)
    trait("Skróty i potoczne zwroty", texts.count { Regex("\\b(xd|xD|XD|ok|spoko|nwm|btw|wgl|itp|itd)\\b").containsMatchIn(it) }, .25)
    val counts = memory.optJSONObject("phraseCounts") ?: JSONObject()
    val phrases = counts.keys().asSequence().filter { counts.getInt(it) >= 3 }
      .sortedByDescending { counts.getInt(it) }.take(10)
      .map { JSONObject().put("text", it).put("count", counts.getInt(it)) }.toList()
    val examples = objects(memory.optJSONArray("examples") ?: JSONArray())
    val selected = (examples.filter { it.getString("reply").length >= 120 }.sortedByDescending { it.getString("reply").length }.take(2) +
      examples.sortedByDescending { it.optLong("timestamp") }).distinctBy { it.getString("reply") }.take(5)
    return JSONObject().put("sampleCount", memory.optInt("sampleCount")).put("conversationCount", if (memory.optInt("sampleCount") > 0) 1 else 0)
      .put("summary", "").put("habits", habits).put("traits", traits).put("phrases", JSONArray(phrases))
      .put("examples", JSONArray(selected)).put("relationship", memory.optJSONArray("relationship") ?: JSONArray())
      .put("updatedAt", memory.optLong("updatedAt")).put("contextUpdatedAt", memory.optLong("contextUpdatedAt"))
      .put("pendingMessages", maxOf(0, memory.optLong("revision") - memory.optLong("analyzedRevision")))
      .put("contextError", memory.optString("contextError"))
  }

  fun writingSamples(memory: JSONObject): JSONArray = JSONArray(objects(memory.optJSONArray("recent") ?: JSONArray())
    .mapIndexed { index, sample -> JSONObject().put("id", "memory-style-$index").put("isMe", true)
      .put("text", sample.getString("text")).put("timestamp", sample.optLong("timestamp")) })

  fun input(memory: JSONObject): JSONObject = overview(memory).put("revision", memory.optLong("revision"))

  /** Only grounded changes are applied; unspecified memories survive subsequent analyses. */
  fun apply(memory: JSONObject, updates: JSONArray, messages: JSONArray, revision: Long, now: Long = System.currentTimeMillis()) {
    val ids = objects(messages).map { it.getString("id") }.toSet()
    val entries = objects(memory.optJSONArray("relationship") ?: JSONArray()).associateBy { it.getString("id") }.toMutableMap()
    for (i in 0 until minOf(updates.length(), 8)) {
      val change = updates.optJSONObject(i) ?: continue
      val evidence = change.optJSONArray("evidenceIds") ?: continue
      val valid = (0 until evidence.length()).map { evidence.optString(it) }.distinct().filter { it in ids }.take(8)
      if (valid.isEmpty()) continue
      val replace = change.optString("replaceId")
      if (replace.isNotBlank() && !entries.containsKey(replace)) continue
      val text = change.optString("text").trim().take(600)
      if (text.isEmpty()) { if (replace.isNotBlank()) entries.remove(replace); continue }
      if (replace.isBlank() && entries.values.any { it.optString("text") == text }) continue
      val id = replace.ifBlank { digest(text + valid.joinToString()).take(24) }
      entries[id] = JSONObject().put("id", id).put("text", text).put("evidenceIds", JSONArray(valid)).put("updatedAt", now)
    }
    memory.put("relationship", JSONArray(entries.values.sortedByDescending { it.optLong("updatedAt") }.take(24)))
      .put("analyzedRevision", revision).put("contextUpdatedAt", now).put("contextError", "")
  }
}
