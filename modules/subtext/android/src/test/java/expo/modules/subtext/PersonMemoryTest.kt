package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PersonMemoryTest {
  private fun message(id: String, text: String = "dobra spoko", me: Boolean = true, time: Long = 1) =
    JSONObject().put("id", id).put("text", text).put("isMe", me).put("timestamp", time)

  @Test fun synchronizationAndRestartNeverCountMessagesTwice() {
    val memory = JSONObject()
    val history = (1..10).map { message("m$it", time = it.toLong()) }
    PersonMemory.update(memory, history, 100)
    val restored = JSONObject(memory.toString())
    PersonMemory.update(restored, history, 200)
    assertEquals(10, restored.getInt("sampleCount"))
    assertEquals(10, restored.getLong("revision"))
    assertEquals(100, restored.getLong("updatedAt"))
    PersonMemory.update(restored, history + message("new", time = 11), 300)
    assertEquals(11, restored.getInt("sampleCount"))
    assertEquals(11, PersonMemory.overview(restored).getJSONArray("phrases").getJSONObject(0).getInt("count"))
  }

  @Test fun countsOnlyOwnUsableMessagesAndKeepsSeparateMemories() {
    val first = JSONObject(); val second = JSONObject()
    PersonMemory.update(first, listOf(message("same"), message("incoming", "cudzy styl", false),
      message("url", "https://example.com"), message("secret", "sk-abcdefghijklmnop")))
    PersonMemory.update(second, listOf(message("same", "Dziękuję, wrócę do tematu.")))
    assertEquals(1, first.getInt("sampleCount"))
    assertEquals(1, second.getInt("sampleCount"))
    assertEquals("dobra spoko", PersonMemory.overview(first).getJSONArray("examples").getJSONObject(0).getString("reply"))
    assertEquals("Dziękuję, wrócę do tematu.", PersonMemory.overview(second).getJSONArray("examples").getJSONObject(0).getString("reply"))
  }

  @Test fun retainsLongAuthenticExamplesWhenHistoryIsTrimmed() {
    val memory = JSONObject()
    val long = "chciałem ci jeszcze napisać o szczegółach spotkania i o tym jak się przygotujemy. ".repeat(4)
    PersonMemory.update(memory, listOf(message("incoming", "jak to zrobimy?", false, 1), message("long", long, time = 2)))
    PersonMemory.update(memory, (3..260).map { message("m$it", "dobra spoko $it", time = it.toLong()) })
    val overview = PersonMemory.overview(memory)
    assertEquals(259, overview.getInt("sampleCount"))
    assertTrue((0 until overview.getJSONArray("examples").length()).any {
      overview.getJSONArray("examples").getJSONObject(it).getString("reply") == long.trim()
    })
    assertEquals("jak to zrobimy?", overview.getJSONArray("examples").getJSONObject(0).getString("incoming"))
  }

  @Test fun newHabitsEvolveGraduallyAndRequireMultipleSamples() {
    val memory = JSONObject()
    PersonMemory.update(memory, (1..10).map { message("m$it", time = it.toLong()) })
    PersonMemory.update(memory, listOf(message("formal", "Dziękuję, sprawdzę to.", time = 11)))
    assertTrue(PersonMemory.overview(memory).getJSONArray("habits").toString().contains("Małe litery"))
    PersonMemory.update(memory, (12..90).map { message("m$it", "Dziękuję, sprawdzę szczegóły.", time = it.toLong()) })
    val overview = PersonMemory.overview(memory)
    assertFalse(overview.getJSONArray("habits").toString().contains("Małe litery"))
    assertTrue(overview.getJSONArray("habits").toString().contains("Wielka litera"))
    assertEquals(60, overview.getJSONArray("traits").getJSONObject(0).getInt("sampleSize"))
    val tiny = JSONObject()
    PersonMemory.update(tiny, listOf(message("one")))
    assertEquals(0, PersonMemory.overview(tiny).getJSONArray("habits").length())
  }

  @Test fun preservesContextAndAppliesOnlyGroundedAddReplaceAndDelete() {
    val memory = JSONObject()
    val messages = JSONArray().put(message("proof", "wolę podsumowanie w punktach", false))
    PersonMemory.apply(memory, JSONArray().put(JSONObject().put("text", "Preferuje podsumowania w punktach")
      .put("evidenceIds", JSONArray().put("proof"))), messages, 1, 10)
    val id = memory.getJSONArray("relationship").getJSONObject(0).getString("id")
    PersonMemory.apply(memory, JSONArray(), messages, 2, 20)
    assertEquals(1, memory.getJSONArray("relationship").length())
    PersonMemory.apply(memory, JSONArray().put(JSONObject().put("replaceId", id).put("text", "niepotwierdzona zmiana")
      .put("evidenceIds", JSONArray().put("invented"))), messages, 3, 30)
    assertEquals("Preferuje podsumowania w punktach", memory.getJSONArray("relationship").getJSONObject(0).getString("text"))
    PersonMemory.apply(memory, JSONArray().put(JSONObject().put("replaceId", id).put("text", "Nowa preferencja")
      .put("evidenceIds", JSONArray().put("proof"))), messages, 4, 40)
    assertEquals(id, memory.getJSONArray("relationship").getJSONObject(0).getString("id"))
    assertEquals("Nowa preferencja", memory.getJSONArray("relationship").getJSONObject(0).getString("text"))
    PersonMemory.apply(memory, JSONArray().put(JSONObject().put("replaceId", id).put("text", "")
      .put("evidenceIds", JSONArray().put("proof"))), messages, 5, 50)
    assertEquals(0, memory.getJSONArray("relationship").length())
  }

  @Test fun boundedDeduplicationCannotRecountOldHistory() {
    val memory = JSONObject()
    val history = (1..4200).map { message("m$it", "ok", time = it.toLong()) }
    PersonMemory.update(memory, history)
    PersonMemory.update(memory, history)
    assertEquals(4200, memory.getInt("sampleCount"))
    assertTrue(memory.getJSONObject("seen").length() <= 4096)
    assertTrue(memory.getJSONObject("phraseCounts").length() <= 256)
  }

  @Test fun onlySnapshotsAnalyzedRevisionWhileNewMessagesRemainPending() {
    val memory = JSONObject()
    PersonMemory.update(memory, listOf(message("first")))
    val revision = memory.getLong("revision")
    PersonMemory.update(memory, listOf(message("new", time = 2)))
    PersonMemory.apply(memory, JSONArray(), JSONArray().put(message("first")), revision)
    assertEquals(1, PersonMemory.overview(memory).getLong("pendingMessages"))
  }
}
