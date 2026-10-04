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
  private fun canonicalKey(key: String): String {
    var current = key
    val visited = mutableSetOf<String>()
    while (visited.add(current)) {
      val next = memories.optJSONObject(current)?.optString("aliasOf")?.takeIf { it.isNotBlank() } ?: return current
      current = next
    }
    return key
  }
  @Synchronized fun conversationKeys(key: String): Set<String> {
    val target = canonicalKey(key)
    return memories.keys().asSequence().filter { canonicalKey(it) == target }.toSet() + target
  }
  @Synchronized fun rooms(): List<JSONObject> = data.keys().asSequence().map { JSONObject(data.getJSONObject(it).toString()) }
    .filter { it.optString("kind") == "PRIVATE" }
    .sortedByDescending { it.optLong("updatedAt") }.toList()
  @Synchronized fun room(key: String): JSONObject? = data.optJSONObject(canonicalKey(key))?.takeIf { it.optString("kind") == "PRIVATE" }?.let { JSONObject(it.toString()).put("aiExcluded", memories.optJSONObject(canonicalKey(key))?.optBoolean("aiExcluded") ?: false) }
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
    val key = canonicalKey("$network:$id")
    if (kind != "PRIVATE") {
      if (kind == "GROUP") data.remove(key)
      return
    }
    val room = data.optJSONObject(key) ?: JSONObject().put("id", key).put("remoteId", key.substringAfter(":" )).put("network", network)
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
  @Synchronized fun avatar(rawKey: String, uri: String?) {
    val key = canonicalKey(rawKey)
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
  @Synchronized fun memory(key: String): JSONObject = JSONObject((memories.optJSONObject(canonicalKey(key)) ?: JSONObject()).toString())
  @Synchronized fun updateContext(rawKey: String, updates: JSONArray, messages: JSONArray, revision: Long,
    reminderUpdates: JSONArray = JSONArray(), reminderSnapshot: JSONArray = JSONArray()) {
    val key = canonicalKey(rawKey)
    val memory = memories.optJSONObject(key) ?: return
    PersonMemory.apply(memory, updates, messages, revision)
    ConversationReminders.apply(memory, reminderUpdates, messages, reminderSnapshot)
    memory.put("reminderVersion", 1)
    saveMemories()
  }
  @Synchronized fun editReminder(rawKey: String, id: String, patch: JSONObject) {
    val key = canonicalKey(rawKey)
    val memory = requireNotNull(memories.optJSONObject(key)) { "Nie znaleziono pamięci rozmowy." }
    ConversationReminders.edit(memory, id, patch)
    saveMemories()
  }
  @Synchronized fun contextFailed(rawKey: String, error: String) {
    val key = canonicalKey(rawKey)
    memories.optJSONObject(key)?.put("contextError", error.take(300))?.put("attemptedAt", System.currentTimeMillis())
    saveMemories()
  }
  @Synchronized fun setAIExcluded(rawKey: String, excluded: Boolean) {
    val key = canonicalKey(rawKey)
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
  @Synchronized fun profile(rawKey: String, profile: JSONObject) {
    val key = canonicalKey(rawKey)
    requireNotNull(data.optJSONObject(canonicalKey(key))) { "Rozmowa nie istnieje." }.put("profile", profile)
    save()
  }
  /** Fold a proven Messenger contact alias into its thread without matching display names. */
  @Synchronized fun rehome(sourceKey: String, targetKey: String) {
    val source = canonicalKey(sourceKey); val target = canonicalKey(targetKey)
    if (source == target || source.substringBefore(":") != target.substringBefore(":")) return
    val old = data.optJSONObject(source)
    val targetRoom = data.optJSONObject(target)
    val sourceMemory = memory(source); val targetMemory = memory(target)
    val cutoff = maxOf(sourceMemory.optLong("resetBefore"), targetMemory.optLong("resetBefore"))
    val eligible = listOf(sourceMemory, targetMemory).filter { it.optLong("resetBefore") == cutoff }
    val base = JSONObject((eligible.maxByOrNull { it.optInt("sampleCount") } ?: JSONObject()).toString())
    for (field in listOf("relationship", "reminders", "dismissedReminders")) {
      val values = eligible.flatMap { memory ->
        val array = memory.optJSONArray(field) ?: JSONArray()
        (0 until array.length()).map { array.get(it) }
      }.distinctBy { if (it is JSONObject) it.optString("id").ifBlank { it.toString() } else it.toString() }
      if (values.isNotEmpty()) base.put(field, JSONArray(values))
    }
    val histories = listOfNotNull(old, targetRoom).flatMap { room ->
      val messages = room.getJSONArray("messages")
      (0 until messages.length()).map { messages.getJSONObject(it) }
    }.filter { cutoff == 0L || it.optLong("timestamp") > cutoff }
    if (old != null || targetRoom != null) {
      val identity = JSONObject((targetRoom ?: old!!).toString()).put("id", target).put("remoteId", target.substringAfter(":"))
      identity.put("messages", JSONArray()).put("snippet", "")
      if (cutoff > 0) identity.put("profile", JSONObject.NULL)
      data.put(target, identity); memories.put(target, base)
      merge(identity.getString("network"), identity.getString("remoteId"), identity.getString("name"), "PRIVATE", histories)
    }
    data.remove(source)
    memories.put(source, JSONObject().put("aliasOf", target))
    save(); saveMemories()
  }
  /** Drops a chat that turned out to be a duplicate of another one. */
  @Synchronized fun forget(network: String, id: String) {
    val key = "$network:$id"
    if (data.has(key)) { data.remove(key); memories.remove(key); save(); saveMemories() }
  }
  /** Keep the identity, but never relearn messages from before this reset. */
  @Synchronized fun resetConversation(key: String, now: Long = System.currentTimeMillis()) {
    val target = canonicalKey(key)
    val room = requireNotNull(data.optJSONObject(target)) { "Nie znaleziono rozmowy." }
    val excluded = memories.optJSONObject(target)?.optBoolean("aiExcluded") ?: false
    room.put("messages", JSONArray()).put("profile", JSONObject.NULL).put("snippet", "").put("updatedAt", now)
    memories.put(target, JSONObject().put("resetBefore", now).put("aiExcluded", excluded))
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
