package expo.modules.guardian
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class BenchmarkTest {
  private fun row(expected: Boolean, risk: String, time: Long = 10) = JSONObject().put("expectedHigh", expected).put("risk", risk).put("latencyMs", time).put("valid", true)
  @Test fun `uncertain is not a successful negative for scam recall`() {
    val m = Benchmark.metrics(listOf(row(true, "high"), row(true, "uncertain"), row(false, "high"), row(false, "low")))
    assertEquals(0.5, m.getDouble("precision"), 0.001)
    assertEquals(0.5, m.getDouble("recall"), 0.001)
    assertEquals(0.5, m.getDouble("falsePositiveRate"), 0.001)
    assertEquals(0.25, m.getDouble("uncertainRate"), 0.001)
  }
  @Test fun `zero predicted positives yields undefined precision`() {
    val m = Benchmark.metrics(listOf(row(true, "low")))
    assertTrue(m.isNull("precision")); assertEquals(0.0, m.getDouble("recall"), 0.001)
  }
}
