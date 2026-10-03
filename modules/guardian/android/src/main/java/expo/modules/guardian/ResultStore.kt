package expo.modules.guardian

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

class ResultStore(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
  private val file = AtomicFile(File(context.noBackupFilesDir, "guardian-results-v1.json"))
  private val retention = 7 * 24 * 60 * 60 * 1000L
  // Immutable, sanitized snapshots: reading history never borrows mutable cached JSON.
  private data class Entry(val id: String, val createdAt: Long, val json: String)
  private data class Stamp(val modified: Long, val size: Long, val backupModified: Long, val backupSize: Long)
  private var cached: List<Entry>? = null
  private var cachedStamp: Stamp? = null
  private fun stamp(): Stamp {
    val backup = File("${file.baseFile.path}.bak")
    return Stamp(file.baseFile.lastModified(), file.baseFile.length(), backup.lastModified(), backup.length())
  }
  private fun entry(result: JSONObject) = Entry(result.getString("id"), result.getLong("createdAt"), result.toString())
  private fun read(): List<Entry> {
    if (cached == null || cachedStamp != stamp()) {
      var pruned = false
      val loaded = if (!file.baseFile.exists() && !File("${file.baseFile.path}.bak").exists()) emptyList() else try {
        val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 1)
        val array = root.getJSONArray("results")
        val cutoff = clock() - retention
        (0 until array.length()).map { array.getJSONObject(it) }.filter {
          it.getInt("schemaVersion") == 1 && it.getLong("createdAt") > cutoff
        }.take(200).map(::entry).also { pruned = it.size != array.length() }
      } catch (_: Exception) { file.delete(); emptyList() }
      if (pruned) write(loaded)
      else { cached = loaded; cachedStamp = stamp() }
    }
    val current = checkNotNull(cached)
    val cutoff = clock() - retention
    val retained = current.filter { it.createdAt > cutoff }
    if (retained.size != current.size) write(retained)
    return checkNotNull(cached)
  }
  private fun write(results: List<Entry>) {
    val bytes = "{\"schemaVersion\":1,\"results\":[${results.joinToString(",") { it.json }}]}".toByteArray(Charsets.UTF_8)
    val stream = file.startWrite()
    try {
      stream.write(bytes)
      file.finishWrite(stream)
    } catch (e: Exception) { file.failWrite(stream); throw e }
    cached = results
    cachedStamp = stamp()
  }
  @Synchronized fun serialized(): List<String> = read().map { it.json }
  @Synchronized fun list(): List<JSONObject> = serialized().map(::JSONObject)
  @Synchronized fun add(result: JSONObject) { write((listOf(entry(result)) + read()).distinctBy { it.id }.take(200)) }
  @Synchronized fun review(id: String) {
    val results = read()
    val changed = results.map {
      if (it.id == id) entry(JSONObject(it.json).put("reviewStatus", "reviewed")) else it
    }
    if (changed != results) write(changed)
  }
  @Synchronized fun clear() { file.delete(); cached = emptyList(); cachedStamp = stamp() }
}
