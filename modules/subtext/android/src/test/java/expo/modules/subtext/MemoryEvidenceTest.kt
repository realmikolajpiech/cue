package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MemoryEvidenceTest {
  private fun message(id: String = "m1") = JSONObject().put("id", id).put("text", "Oddam Ci 50 zł jutro.")
    .put("sender", "Marta").put("isMe", false).put("timestamp", 1000).put("mediaId", "private-binary-reference")

  @Test fun snapshotsCopyOnlyBoundedKnownSourceFields() {
    val source = message().put("text", "x".repeat(5000))
    val result = MemoryEvidence.capture(listOf("m1", "missing", "m1"), JSONArray().put(source))
    assertEquals(1, result.length())
    assertEquals(4000, result.getJSONObject(0).getString("text").length)
    assertFalse(result.getJSONObject(0).has("mediaId"))
    source.put("text", "changed")
    assertEquals(4000, result.getJSONObject(0).getString("text").length)
  }

  @Test fun sourceSurvivesSerializationButIsNotExtraCloudContext() {
    val memory = JSONObject()
    val change = JSONObject().put("text", "Marta ma oddać Ci 50 zł").put("replaceId", "")
      .put("kind", "commitment").put("owner", "other").put("status", "open").put("dueDate", "")
      .put("evidenceIds", JSONArray().put("m1"))
    ConversationReminders.apply(memory, JSONArray().put(change), JSONArray().put(message()), JSONArray())
    PersonMemory.apply(memory, JSONArray().put(change), JSONArray().put(message()), 1)
    val restored = JSONObject(memory.toString())
    assertEquals("Oddam Ci 50 zł jutro.", restored.getJSONArray("reminders").getJSONObject(0).getJSONArray("sources").getJSONObject(0).getString("text"))
    val input = PersonMemory.input(restored)
    assertFalse(input.getJSONArray("reminders").getJSONObject(0).has("sources"))
    assertFalse(input.getJSONArray("relationship").getJSONObject(0).has("sources"))
    assertTrue(restored.getJSONArray("relationship").getJSONObject(0).has("sources"))
  }

  @Test fun manualEditRetainsSourcesAndMarksTheirOrigin() {
    val memory = JSONObject()
    val change = JSONObject().put("text", "Dług").put("kind", "commitment").put("owner", "other")
      .put("status", "open").put("dueDate", "").put("evidenceIds", JSONArray().put("m1"))
    ConversationReminders.apply(memory, JSONArray().put(change), JSONArray().put(message()), JSONArray(), 100)
    val item = memory.getJSONArray("reminders").getJSONObject(0)
    ConversationReminders.edit(memory, item.getString("id"), JSONObject().put("text", "Ręczna korekta"), 200)
    assertEquals(200L, item.getLong("manualAt"))
    assertEquals("m1", item.getJSONArray("sources").getJSONObject(0).getString("id"))
  }

  @Test fun demoProgressionHasStableIdsAndOnlySyntheticMessages() {
    val first = CueDemo.messages(0, 1000000)
    val last = CueDemo.messages(3, 1000000)
    assertEquals(4, first.size); assertEquals(10, last.size)
    assertEquals(first.map { it.toString() }, last.take(4).map { it.toString() })
    assertEquals(10, last.map { it.getString("id") }.toSet().size)
  }

  @Test fun closingKnownReminderWithEmptyLabelPreservesItsMeaning() {
    val memory = JSONObject()
    val change = JSONObject().put("text", "Marta ma oddać Ci 50 zł").put("kind", "commitment")
      .put("owner", "other").put("status", "open").put("dueDate", "").put("evidenceIds", JSONArray().put("m1"))
    ConversationReminders.apply(memory, JSONArray().put(change), JSONArray().put(message()), JSONArray(), 100)
    val snapshot = JSONArray(memory.getJSONArray("reminders").toString())
    change.put("replaceId", snapshot.getJSONObject(0).getString("id")).put("text", "").put("status", "done")
    ConversationReminders.apply(memory, JSONArray().put(change), JSONArray().put(message()), snapshot, 200)
    val saved = memory.getJSONArray("reminders").getJSONObject(0)
    assertEquals("done", saved.getString("status"))
    assertEquals("Marta ma oddać Ci 50 zł", saved.getString("text"))
  }
}
