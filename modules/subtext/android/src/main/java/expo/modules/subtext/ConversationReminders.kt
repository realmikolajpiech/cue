package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

internal object ConversationReminders {
  private val kinds = setOf("meeting", "commitment", "waiting", "important")
  private val owners = setOf("me", "other", "both")
  private val states = setOf("open", "tentative", "done", "cancelled")
  private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
  private fun key(item: JSONObject) = listOf(item.optString("kind"), item.optString("owner"),
    item.optString("text").trim().lowercase(), item.optString("dueDate")).joinToString("|")

  /** Date-only deadlines remain current through the entire local day. Never guess a time. */
  fun dueAt(date: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (date.isBlank()) return null
    return if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(date))
      LocalDate.parse(date).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    else OffsetDateTime.parse(date).toInstant().toEpochMilli()
  }

  fun overview(memory: JSONObject, now: Long = System.currentTimeMillis()): JSONArray {
    val items = objects(memory.optJSONArray("reminders") ?: JSONArray()).map { original ->
      val item = JSONObject(original.toString())
      val status = item.getString("status")
      val due = runCatching { dueAt(item.optString("dueDate")) }.getOrNull()
      val effective = when {
        status in setOf("done", "cancelled") -> status
        due != null && due < now -> when (item.getString("kind")) {
          "meeting" -> "past"
          "important" -> "expired"
          else -> "overdue"
        }
        status == "tentative" -> status
        item.getString("kind") == "waiting" -> "waiting"
        due != null -> "upcoming"
        else -> "open"
      }
      item.put("effectiveStatus", effective).put("dueAt", due ?: JSONObject.NULL)
    }
    return JSONArray(items.sortedWith(compareBy<JSONObject> {
      if (it.optString("effectiveStatus") in setOf("done", "cancelled", "expired")) 1 else 0
    }.thenBy { if (it.optString("effectiveStatus") == "overdue") 0 else 1 }
      .thenBy { if (it.isNull("dueAt")) Long.MAX_VALUE else it.optLong("dueAt") }
      .thenByDescending { it.optLong("updatedAt") }))
  }

  fun apply(memory: JSONObject, updates: JSONArray, messages: JSONArray, snapshot: JSONArray,
    now: Long = System.currentTimeMillis()) {
    val source = objects(messages).associateBy { it.getString("id") }
    val previous = objects(snapshot).associateBy { it.getString("id") }
    val entries = objects(memory.optJSONArray("reminders") ?: JSONArray()).associateBy { it.getString("id") }.toMutableMap()
    val dismissed = memory.optJSONArray("dismissedReminders") ?: JSONArray()
    val ignored = (0 until dismissed.length()).map { dismissed.getString(it) }.toSet()
    for (i in 0 until minOf(updates.length(), 8)) {
      val change = updates.optJSONObject(i) ?: continue
      val evidence = change.optJSONArray("evidenceIds") ?: continue
      val valid = (0 until evidence.length()).map { evidence.optString(it) }.distinct().filter { it in source }.take(8)
      if (valid.isEmpty()) continue
      val text = change.optString("text").trim().take(400)
      val kind = change.optString("kind"); val owner = change.optString("owner"); val status = change.optString("status")
      val date = change.optString("dueDate").take(40)
      if (text.isBlank() || kind !in kinds || owner !in owners || status !in states ||
        runCatching { dueAt(date) }.isFailure) continue
      var replace = change.optString("replaceId")
      if (replace.isBlank()) replace = entries.values.firstOrNull {
        it.optString("kind") == kind && it.optString("owner") == owner && it.optString("text").equals(text, true)
      }?.getString("id") ?: ""
      val existing = entries[replace]
      if (replace.isNotBlank()) {
        if (existing == null || previous[replace]?.optLong("updatedAt") != existing.optLong("updatedAt")) continue
        // A manual decision cannot be undone by replaying messages that predate that decision.
        if (existing.optLong("manualAt") > 0 && valid.none { source[it]!!.optLong("timestamp") > existing.optLong("manualAt") }) continue
      }
      val candidate = JSONObject().put("text", text).put("kind", kind).put("owner", owner).put("status", status).put("dueDate", date)
      if (key(candidate) in ignored) continue
      if (existing == null && entries.size >= 64) {
        val archived = entries.values.filter { it.optString("status") in setOf("done", "cancelled") }
          .minByOrNull { it.optLong("updatedAt") } ?: continue
        entries.remove(archived.getString("id"))
      }
      val id = replace.ifBlank { UUID.randomUUID().toString() }
      entries[id] = candidate.put("id", id).put("evidenceIds", JSONArray(valid)).put("updatedAt", now)
        .put("createdAt", existing?.optLong("createdAt") ?: now)
    }
    memory.put("reminders", JSONArray(entries.values))
  }

  fun edit(memory: JSONObject, id: String, patch: JSONObject, now: Long = System.currentTimeMillis()) {
    val entries = objects(memory.optJSONArray("reminders") ?: JSONArray()).toMutableList()
    val item = requireNotNull(entries.find { it.getString("id") == id }) { "Nie znaleziono wpisu." }
    if (patch.optBoolean("delete")) {
      val ignored = memory.optJSONArray("dismissedReminders") ?: JSONArray()
      memory.put("dismissedReminders", JSONArray(((0 until ignored.length()).map { ignored.getString(it) } + key(item)).distinct().takeLast(128)))
      entries.remove(item)
    } else {
      val text = if (patch.has("text")) patch.getString("text").trim() else item.getString("text")
      val date = if (patch.has("dueDate")) patch.getString("dueDate").trim() else item.optString("dueDate")
      val status = if (patch.has("status")) patch.getString("status") else item.getString("status")
      require(text.isNotBlank() && text.length <= 400 && date.length <= 40 && status in states) { "Nieprawidłowy wpis." }
      dueAt(date) // Reject invalid dates before mutating any saved fields.
      item.put("text", text).put("dueDate", date).put("status", status).put("updatedAt", now).put("manualAt", now)
    }
    memory.put("reminders", JSONArray(entries))
  }
}
