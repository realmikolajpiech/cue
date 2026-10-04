package expo.modules.subtext

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SubtextStore(context: Context) {
  private val file = AtomicFile(File(context.noBackupFilesDir, "subtext-conversations.json"))
  private var data = runCatching { JSONObject(String(file.readFully())) }.getOrDefault(JSONObject())
  private val memoryFile = AtomicFile(File(context.noBackupFilesDir, "subtext-person-memory.json"))
  private var memories = runCatching { JSONObject(String(memoryFile.readFully())) }.getOrDefault(JSONObject())
  init {
    val excluded = data.keys().asSequence().filter { data.getJSONObject(it).optString("kind") == "GROUP" }.toList()
    excluded.forEach { data.remove(it) }
    if (excluded.isNotEmpty()) save()
    // Bootstrap existing histories once; update() is idempotent across restarts and repeated syncs.
    rooms().filterNot { it.optBoolean("demo") }.forEach { room ->
      val history = room.getJSONArray("messages")
      updateMemory(room.getString("id"), (0 until history.length()).map { history.getJSONObject(it) })
    }
    saveMemories()
  }
  @Synchronized fun rooms(): List<JSONObject> = data.keys().asSequence().map { JSONObject(data.getJSONObject(it).toString()) }
    .filter { it.optString("kind") == "PRIVATE" }
    .sortedByDescending { it.optLong("updatedAt") }.toList()
  @Synchronized fun room(key: String): JSONObject? = data.optJSONObject(key)?.takeIf { it.optString("kind") == "PRIVATE" }?.let { JSONObject(it.toString()).put("aiExcluded", memories.optJSONObject(key)?.optBoolean("aiExcluded") ?: false) }
  // The inbox should not serialize hundreds of message bodies per conversation.
  @Synchronized fun summaries(): List<JSONObject> = data.keys().asSequence().map { data.getJSONObject(it) }
    .filter { it.optString("kind") == "PRIVATE" }.map { room ->
      JSONObject().apply {
        listOf("id", "remoteId", "network", "name", "kind", "updatedAt", "snippet", "profile", "demo", "demoStage", "avatarUri").forEach { key ->
          if (room.has(key)) {
            val value = room.get(key)
            put(key, if (value is JSONObject) JSONObject(value.toString()) else value)
          }
        }
        put("messageCount", room.getJSONArray("messages").length())
        put("aiExcluded", memories.optJSONObject(room.getString("id"))?.optBoolean("aiExcluded") ?: false)
      }
    }.sortedByDescending { it.optLong("updatedAt") }.toList()
  @Synchronized fun merge(network: String, id: String, name: String, kind: String, messages: List<JSONObject> = emptyList(), timestamp: Long = 0) {
    val key = "$network:$id"
    if (kind != "PRIVATE") {
      if (kind == "GROUP") data.remove(key)
      return
    }
    val room = data.optJSONObject(key) ?: JSONObject().put("id", key).put("remoteId", id).put("network", network)
      .put("messages", JSONArray()).put("profile", JSONObject.NULL)
    if (name.isNotBlank()) room.put("name", name.take(160))
    if (!room.has("name")) room.put("name", "Rozmowa")
    room.put("kind", kind)
    val all = linkedMapOf<String, JSONObject>()
    val previous = room.getJSONArray("messages")
    for (i in 0 until previous.length()) previous.getJSONObject(i).let { all[it.getString("id")] = it }
    val resetBefore = memories.optJSONObject(key)?.optLong("resetBefore") ?: 0
    messages.filter { it.optString("id").isNotBlank() && it.optString("text").isNotBlank() &&
      (resetBefore == 0L || it.optLong("timestamp") > resetBefore) }.forEach { all[it.getString("id")] = it }
    if (!room.optBoolean("demo") && id != "subtext-demo") updateMemory(key, all.values.toList())
    val retained = all.values.sortedBy { it.optLong("timestamp") }.takeLast(200)
    room.put("messages", JSONArray(retained))
    room.put("updatedAt", maxOf(timestamp, room.optLong("updatedAt"), retained.lastOrNull()?.optLong("timestamp") ?: 0))
    room.put("snippet", retained.lastOrNull()?.let { MessageMedia.preview(it.optString("text"), it.optBoolean("isMe")) }?.take(160) ?: room.optString("snippet"))
    data.put(key, room)
    if (data.length() > 150) rooms().drop(150).forEach { data.remove(it.getString("id")) }
  }
  @Synchronized fun avatar(key: String, uri: String?) {
    val room = data.optJSONObject(key) ?: return
    if (uri == null) room.remove("avatarUri") else room.put("avatarUri", uri)
    save()
  }
  @Synchronized fun flush() { save(); saveMemories() }
  private fun updateMemory(key: String, messages: List<JSONObject>) {
    val memory = memories.optJSONObject(key) ?: JSONObject()
    PersonMemory.update(memory, messages)
    memories.put(key, memory)
  }
  @Synchronized fun memory(key: String): JSONObject = JSONObject((memories.optJSONObject(key) ?: JSONObject()).toString())
  @Synchronized fun updateContext(key: String, updates: JSONArray, messages: JSONArray, revision: Long,
    reminderUpdates: JSONArray = JSONArray(), reminderSnapshot: JSONArray = JSONArray()) {
    val memory = memories.optJSONObject(key) ?: return
    PersonMemory.apply(memory, updates, messages, revision)
    ConversationReminders.apply(memory, reminderUpdates, messages, reminderSnapshot)
    memory.put("reminderVersion", 1)
    saveMemories()
  }
  @Synchronized fun editReminder(key: String, id: String, patch: JSONObject) {
    val memory = requireNotNull(memories.optJSONObject(key)) { "Nie znaleziono pamięci rozmowy." }
    ConversationReminders.edit(memory, id, patch)
    saveMemories()
  }
  @Synchronized fun contextFailed(key: String, error: String) {
    memories.optJSONObject(key)?.put("contextError", error.take(300))?.put("attemptedAt", System.currentTimeMillis())
    saveMemories()
  }
  @Synchronized fun setAIExcluded(key: String, excluded: Boolean) {
    requireNotNull(data.optJSONObject(key)) { "Nie znaleziono rozmowy." }
    val memory = memories.optJSONObject(key) ?: JSONObject()
    memories.put(key, memory.put("aiExcluded", excluded)); saveMemories()
  }
  @Synchronized fun demoStage(stage: Int, now: Long = System.currentTimeMillis()): String {
    require(stage in 0..3)
    val key = CueDemo.ID
    val existing = data.optJSONObject(key)
    require(stage == 0 || (existing?.optInt("demoStage", -1) ?: -1) + 1 == stage) { "Otwórz kolejne kroki po kolei." }
    if (stage == 0) { data.remove(key); memories.remove(key) }
    val base = if (stage == 0) now else existing!!.getLong("demoBase")
    val history = CueDemo.messages(stage, base)
    merge("messenger", "subtext-demo", "Marta · demo", "PRIVATE", history)
    data.getJSONObject(key).put("demo", true).put("demoStage", stage).put("demoBase", base)
    updateMemory(key, history)
    save(); saveMemories()
    return key
  }
  @Synchronized fun markDemo(key: String) { data.getJSONObject(key).put("demo", true); save() }
  @Synchronized fun profile(key: String, profile: JSONObject) {
    requireNotNull(data.optJSONObject(key)) { "Rozmowa nie istnieje." }.put("profile", profile)
    save()
  }
  /** Drops a chat that turned out to be a duplicate of another one. */
  @Synchronized fun forget(network: String, id: String) {
    val key = "$network:$id"
    if (data.has(key)) { data.remove(key); memories.remove(key); save(); saveMemories() }
  }
  /** Keep the identity, but never relearn messages from before this reset. */
  @Synchronized fun resetConversation(key: String, now: Long = System.currentTimeMillis()) {
    val room = requireNotNull(data.optJSONObject(key)) { "Nie znaleziono rozmowy." }
    val excluded = memories.optJSONObject(key)?.optBoolean("aiExcluded") ?: false
    room.put("messages", JSONArray()).put("profile", JSONObject.NULL).put("snippet", "").put("updatedAt", now)
    memories.put(key, JSONObject().put("resetBefore", now).put("aiExcluded", excluded))
    save(); saveMemories()
  }
  @Synchronized fun clear(network: String? = null) {
    if (network == null) { data = JSONObject(); memories = JSONObject() }
    else {
      data.keys().asSequence().filter { it.startsWith("$network:") }.toList().forEach { data.remove(it) }
      memories.keys().asSequence().filter { it.startsWith("$network:") }.toList().forEach { memories.remove(it) }
    }
    save(); saveMemories()
  }
  private fun saveMemories() {
    val stream = memoryFile.startWrite()
    try { stream.write(memories.toString().toByteArray()); memoryFile.finishWrite(stream) }
    catch (error: Exception) { memoryFile.failWrite(stream); throw error }
  }
  private fun save() {
    val stream = file.startWrite()
    try { stream.write(data.toString().toByteArray()); file.finishWrite(stream) }
    catch (error: Exception) { file.failWrite(stream); throw error }
  }
}
