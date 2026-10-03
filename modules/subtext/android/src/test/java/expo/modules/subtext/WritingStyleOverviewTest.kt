package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WritingStyleOverviewTest {
  private fun room(id: String, reply: String, demo: Boolean = false) = JSONObject()
    .put("id", id).put("demo", demo).put("messages", JSONArray()
      .put(JSONObject().put("id", "in").put("isMe", false).put("text", "Hej?").put("timestamp", 1))
      .put(JSONObject().put("id", "out").put("isMe", true).put("text", reply).put("timestamp", 2)))

  @Test fun selectedConversationKeepsOnlyItsOwnVoice() {
    val chosen = room("chosen", "jasne xd")
    val other = room("other", "Dziękuję uprzejmie.")
    val local = writingStyleOverview(listOf(chosen))
    assertEquals(1, local.getInt("sampleCount"))
    assertEquals("jasne xd", local.getJSONArray("examples").getJSONObject(0).getString("reply"))
    assertFalse(local.toString().contains("Dziękuję"))
    assertEquals(2, writingStyleOverview(listOf(chosen, other)).getInt("sampleCount"))
  }

  @Test fun demoCannotBecomePersonalStyle() {
    val overview = writingStyleOverview(listOf(room("demo", "przykład", true)))
    assertEquals(0, overview.getInt("sampleCount"))
    assertEquals(0, overview.getJSONArray("examples").length())
  }
}
