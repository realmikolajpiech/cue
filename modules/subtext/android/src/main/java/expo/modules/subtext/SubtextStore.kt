package expo.modules.subtext

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SubtextStore(context: Context) {
  private val file = AtomicFile(File(context.noBackupFilesDir, "subtext-conversations.json"))
  private var data = runCatching { JSONObject(String(file.readFully())) }.getOrDefault(JSONObject())
  @Synchronized fun rooms(): List<JSONObject> = data.keys().asSequence().map { JSONObject(data.getJSONObject(it).toString()) }
    .sortedByDescending { it.optLong("updatedAt") }.toList()
  @Synchronized fun room(key: String): JSONObject? = data.optJSONObject(key)?.let { JSONObject(it.toString()) }
  @Synchronized fun merge(network: String, id: String, name: String, kind: String, messages: List<JSONObject> = emptyList(), timestamp: Long = 0) {
    val key = "$network:$id"
    val room = data.optJSONObject(key) ?: JSONObject().put("id", key).put("remoteId", id).put("network", network)
      .put("messages", JSONArray()).put("profile", JSONObject.NULL)
    if (name.isNotBlank()) room.put("name", name.take(160))
    if (!room.has("name")) room.put("name", "Rozmowa")
    room.put("kind", kind)
    val all = linkedMapOf<String, JSONObject>()
    val previous = room.getJSONArray("messages")
    for (i in 0 until previous.length()) previous.getJSONObject(i).let { all[it.getString("id")] = it }
    messages.filter { it.optString("id").isNotBlank() && it.optString("text").isNotBlank() }.forEach { all[it.getString("id")] = it }
    val retained = all.values.sortedBy { it.optLong("timestamp") }.takeLast(200)
    room.put("messages", JSONArray(retained))
    room.put("updatedAt", maxOf(timestamp, room.optLong("updatedAt"), retained.lastOrNull()?.optLong("timestamp") ?: 0))
    room.put("snippet", retained.lastOrNull()?.optString("text")?.take(160) ?: room.optString("snippet"))
    data.put(key, room)
    if (data.length() > 150) rooms().drop(150).forEach { data.remove(it.getString("id")) }
  }
  @Synchronized fun flush() { save() }
  @Synchronized fun markDemo(key: String) { data.getJSONObject(key).put("demo", true); save() }
  @Synchronized fun profile(key: String, profile: JSONObject) {
    requireNotNull(data.optJSONObject(key)) { "Rozmowa nie istnieje." }.put("profile", profile)
    save()
  }
  @Synchronized fun clear(network: String? = null) {
    if (network == null) data = JSONObject()
    else data.keys().asSequence().filter { it.startsWith("$network:") }.toList().forEach { data.remove(it) }
    save()
  }
  private fun save() {
    val stream = file.startWrite()
    try { stream.write(data.toString().toByteArray()); file.finishWrite(stream) }
    catch (error: Exception) { file.failWrite(stream); throw error }
  }
}
