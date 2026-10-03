package expo.modules.guardian

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ResultStore(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
  private val file = AtomicFile(File(context.noBackupFilesDir, "guardian-results-v1.json"))
  private val retention = 7 * 24 * 60 * 60 * 1000L
  private fun read(): MutableList<JSONObject> {
    if (!file.baseFile.exists()) return mutableListOf()
    return try {
      val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
      if (root.getInt("schemaVersion") != 1) { file.delete(); return mutableListOf() }
      val array = root.getJSONArray("results")
      (0 until array.length()).map { array.getJSONObject(it) }.filter {
        it.getInt("schemaVersion") == 1 && it.getLong("createdAt") > clock() - retention
      }.take(200).toMutableList()
    } catch (_: Exception) { file.delete(); mutableListOf() }
  }
  private fun write(results: List<JSONObject>) {
    val stream = file.startWrite()
    try {
      stream.write(JSONObject().put("schemaVersion", 1).put("results", JSONArray(results)).toString().toByteArray())
      file.finishWrite(stream)
    } catch (e: Exception) { file.failWrite(stream); throw e }
  }
  @Synchronized fun list(): List<JSONObject> = read().also { write(it) }
  @Synchronized fun add(result: JSONObject) { write((listOf(result) + read()).distinctBy { it.getString("id") }.take(200)) }
  @Synchronized fun review(id: String) { write(read().onEach { if (it.getString("id") == id) it.put("reviewStatus", "reviewed") }) }
  @Synchronized fun clear() { file.delete() }
}
