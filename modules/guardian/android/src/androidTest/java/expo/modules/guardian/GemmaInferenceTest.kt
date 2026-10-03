package expo.modules.guardian

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in hardware test. Supply -Pandroid.testInstrumentationRunnerArguments.gemmaModelPath=... */
@RunWith(AndroidJUnit4::class)
class GemmaInferenceTest {
  @Test fun verifiedGemmaRunsRealPolishBenchmark() = runBlocking {
    val path = InstrumentationRegistry.getArguments().getString("gemmaModelPath")
    assumeTrue("A licensed Gemma artifact must be supplied", path != null)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val runtime = GuardianRuntime.get(context)
    val file = File(requireNotNull(path))
    assertTrue(GemmaModel.load(context).verify(file))
    println("GUARDIAN_STAGE=import")
    try { runtime.importModel(android.net.Uri.fromFile(file).toString()) } catch (e: Exception) {
      println("GUARDIAN_INIT_ERROR=${runtime.inference.error}")
      throw e
    }
    assertEquals("ready", runtime.inference.state)
    assertFalse(runtime.enabled)
    println("GUARDIAN_STAGE=inference backend=${runtime.inference.backend}")
    val result = try { runtime.inference.analyze(listOf("Cześć mamo, to mój nowy numer. Stary telefon się zepsuł.", "Pilnie przelej mi 2500 zł na nowe konto, nie dzwoń i nikomu nie mów.")) } catch (_: Exception) { throw AssertionError("Gemma did not return a valid assessment") }
    assertEquals(setOf("risk", "category", "signals"), result.keys().asSequence().toSet())
    println("GUARDIAN_STAGE=benchmark")
    val report = runtime.benchmark()
    println("GUARDIAN_BENCHMARK=$report")
    assertEquals(40, org.json.JSONObject(report).getJSONArray("cases").length())
    runtime.inference.close()
    runtime.setEnabled(true)
    assertEquals("ready", runtime.inference.state)
    runtime.setEnabled(false)
  }
}
