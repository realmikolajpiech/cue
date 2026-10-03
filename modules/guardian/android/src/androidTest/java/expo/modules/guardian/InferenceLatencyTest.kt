package expo.modules.guardian

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Opt-in CPU comparison. Never connects a listener or reads the user's notification history. */
@RunWith(AndroidJUnit4::class)
class InferenceLatencyTest {
  @Test fun compareCpuThreadsWithIdenticalInputsAndValidatedOutputs() = runBlocking {
    val arguments = InstrumentationRegistry.getArguments()
    val modelPath = arguments.getString("gemmaModelPath")
    assumeTrue("A licensed Gemma artifact must be supplied", modelPath != null)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.cacheDir, "latency-test-${UUID.randomUUID()}").also { it.mkdirs() }
    val file = File(directory, "guardian-model.litertlm")
    val reportFile = File(context.noBackupFilesDir, "guardian-latency-threads.json")
    val fixtures = listOf(
      listOf("Dziękuję za pomoc. Do zobaczenia jutro."),
      listOf("Cześć mamo, to mój nowy numer. Stary telefon się zepsuł.", "Pilnie przelej mi 2500 zł na nowe konto, nie dzwoń i nikomu nie mów."),
      listOf("Nie podawaj nikomu kodu. Kod jest tylko do Twojego logowania."),
      listOf("Twoje konto zostanie dziś zablokowane. Wyślij mi hasło i kod SMS, żebym mógł je odblokować."),
    )
    val rounds = JSONArray()
    val expected = mutableMapOf<Int, String>()
    val report = JSONObject().put("schemaVersion", 1).put("complete", false).put("createdAt", System.currentTimeMillis())
      .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
      .put("androidSdk", Build.VERSION.SDK_INT).put("runtimeVersion", "0.15.0")
      .put("promptVersion", "guardian-pl-v3-evidence").put("rounds", rounds)
    var matches = true
    try {
      reportFile.writeText(report.toString(2))
      File(requireNotNull(modelPath)).copyTo(file)
      val model = GemmaModel.load(context)
      assertTrue(model.verify(file))
      report.put("modelSha256", model.sha256)
      // Repeat the default at the end to expose warming / thermal / competing-work effects.
      val configs = listOf<Int?>(null, 2, 6, null)
      for ((roundIndex, threads) in configs.withIndex()) {
        val inference = GuardianInference(threads)
        val rows = JSONArray()
        val round = JSONObject().put("cpuThreads", threads ?: JSONObject.NULL).put("cases", rows)
        rounds.put(round)
        try {
          inference.initialize(file)
          assertEquals("ready", inference.state)
          round.put("initializationMs", inference.initializationMs)
          println("GUARDIAN_LATENCY_STAGE=round_$roundIndex threads=${threads ?: "default"}")
          for ((caseIndex, messages) in fixtures.withIndex()) {
            val start = SystemClock.elapsedRealtime()
            var failure: String? = null
            val result = try { inference.analyze(messages) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
              failure = e.message?.takeIf { it in setOf("model_evidence_not_in_input", "model_missing_evidence", "analysis_timeout", "model_output_too_long") }
                ?: e.javaClass.simpleName
              null
            }
            val latency = SystemClock.elapsedRealtime() - start
            val signature = if (result == null) "failure:$failure" else "${result.getString("risk")}|${result.getString("category")}|${result.getJSONArray("signals")}"
            if (roundIndex == 0) expected[caseIndex] = signature
            val same = signature == expected[caseIndex]
            matches = matches && same
            val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            rows.put(JSONObject().put("case", caseIndex).put("latencyMs", latency)
              .put("assessment", result ?: JSONObject.NULL).put("valid", result != null)
              .put("failure", failure ?: JSONObject.NULL).put("matchesBaseline", same).put("observedPssKb", memory.totalPss))
            report.put("outputsIdentical", matches)
            reportFile.writeText(report.toString(2))
            println("GUARDIAN_LATENCY_CASE=round_$roundIndex case_$caseIndex ms=$latency match=$same")
          }
        } finally { inference.close() }
      }
      report.put("complete", true)
      reportFile.writeText(report.toString(2))
      println("GUARDIAN_LATENCY_REPORT=$report")
      assertTrue("CPU configuration changed one or more assessments; inspect report", matches)
    } finally { directory.deleteRecursively() }
  }
}
