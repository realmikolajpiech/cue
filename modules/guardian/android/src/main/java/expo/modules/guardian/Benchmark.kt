package expo.modules.guardian

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

object Benchmark {
  fun metrics(rows: List<JSONObject>): JSONObject {
    val tp = rows.count { it.getBoolean("expectedHigh") && it.optString("risk") == "high" }
    val fp = rows.count { !it.getBoolean("expectedHigh") && it.optString("risk") == "high" }
    val fn = rows.count { it.getBoolean("expectedHigh") && it.optString("risk") != "high" }
    val tn = rows.count { !it.getBoolean("expectedHigh") && it.optString("risk") != "high" }
    val times = rows.map { it.getLong("latencyMs") }.sorted()
    fun ratio(n: Int, d: Int): Any = if (d == 0) JSONObject.NULL else n.toDouble() / d
    fun percentile(p: Double): Long = times[(kotlin.math.ceil(times.size * p).toInt() - 1).coerceAtLeast(0)]
    return JSONObject().put("tp", tp).put("fp", fp).put("fn", fn).put("tn", tn)
      .put("precision", ratio(tp, tp + fp)).put("recall", ratio(tp, tp + fn))
      .put("falsePositiveRate", ratio(fp, fp + tn))
      .put("uncertainRate", ratio(rows.count { it.optString("risk") == "uncertain" }, rows.size))
      .put("jsonValidity", ratio(rows.count { it.getBoolean("valid") }, rows.size))
      .put("p50Ms", percentile(0.5)).put("p95Ms", percentile(0.95))
  }
  suspend fun run(runtime: GuardianRuntime, progress: (Int) -> Unit): JSONObject = withContext(Dispatchers.IO) {
    val cases = JSONObject(runtime.context.assets.open("cases.json").bufferedReader().use { it.readText() }).getJSONArray("cases")
    val rows = mutableListOf<JSONObject>()
    var peakPssKb = 0
    for (i in 0 until cases.length()) {
      ensureActive()
      val case = cases.getJSONObject(i)
      val messages = case.getJSONArray("messages")
      val start = SystemClock.elapsedRealtime()
      var valid = true
      val parsed = try { runtime.inference.analyze((0 until messages.length()).map { messages.getString(it) }) }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { valid = false; JSONObject().put("risk", "uncertain").put("category", "unknown") }
      val info = Debug.MemoryInfo(); Debug.getMemoryInfo(info); peakPssKb = maxOf(peakPssKb, info.totalPss)
      rows.add(JSONObject().put("id", case.getString("id")).put("expectedHigh", case.getBoolean("expectedHigh"))
        .put("risk", parsed.getString("risk")).put("category", parsed.getString("category")).put("valid", valid)
        .put("latencyMs", SystemClock.elapsedRealtime() - start))
      progress(i + 1)
    }
    val report = JSONObject().put("schemaVersion", 1).put("createdAt", System.currentTimeMillis())
      .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("androidSdk", Build.VERSION.SDK_INT)
      .put("runtimeVersion", "0.15.0").put("promptVersion", runtime.inference.promptVersion)
      .put("modelSha256", runtime.status()["modelSha256"]).put("backend", runtime.inference.backend)
      .put("sampler", JSONObject().put("topK", 1).put("topP", 0.9).put("temperature", 0.0))
      .put("maxOutputToken", 256).put("initializationMs", runtime.inference.initializationMs)
      .put("peakObservedPssKb", peakPssKb).put("metrics", metrics(rows)).put("cases", JSONArray(rows))
    java.io.File(runtime.context.noBackupFilesDir, "guardian-benchmark.json").writeText(report.toString(2))
    report
  }
}
