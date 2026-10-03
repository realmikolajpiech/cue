package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeepSeekTest {
  private val messages = JSONArray("""[{"id":"m1","text":"Przygotuję dokument w piątek."}]""")
  private fun result() = JSONObject("""{
    "summary":"Rozmowa o dokumencie", "beforeReply":"Sprawdź wcześniejsze ustalenia",
    "observations":[{"text":"Ustalenie terminu","evidenceIds":["m1"]}],
    "commitments":[{"text":"Nieistniejące ustalenie","evidenceIds":["invented"]}],
    "suggestions":[{"tone":"Naturalnie","text":"Czy termin piątkowy pozostaje aktualny?"}]
  }""")
  @Test fun dropsUnsupportedClaimsAndKeepsEvidence() {
    val clean = DeepSeek.validate(result(), messages)
    assertEquals(0, clean.getJSONArray("commitments").length())
    assertEquals("m1", clean.getJSONArray("observations").getJSONObject(0).getJSONArray("evidenceIds").getString(0))
    assertEquals(1, clean.getInt("messageCount"))
  }
  @Test(expected = IllegalArgumentException::class) fun rejectsEmptySuggestion() {
    val raw = result(); raw.getJSONArray("suggestions").getJSONObject(0).put("text", " ")
    DeepSeek.validate(raw, messages)
  }
  @Test fun capsOutputAndDoesNotReturnUnvalidatedFields() {
    val raw = result().put("summary", "x".repeat(5000)).put("secret", "not part of the contract")
    val clean = DeepSeek.validate(raw, messages)
    assertEquals(1200, clean.getString("summary").length)
    assertFalse(clean.has("secret"))
  }
  @Test fun separatesOwnWritingSamplesFromConversationContext() {
    val history = JSONArray()
      .put(JSONObject().put("isMe", false).put("text", "Szanowny Panie, uprzejmie proszę."))
      .put(JSONObject().put("text", "unknown author"))
      .put(JSONObject().put("isMe", true).put("text", "dobra ogarne jutro xd"))
      .put(JSONObject().put("isMe", true).put("text", "sk-abcdefghijklmnop"))
      .put(JSONObject().put("isMe", true).put("text", "https://example.com"))
    val payload = DeepSeek.userContent(messages, "moja intencja", history)
    assertEquals("dobra ogarne jutro xd", payload.getJSONObject("styleInput").getJSONArray("conversationExamples").getString(0))
    assertEquals(1, payload.getJSONObject("styleInput").getJSONArray("conversationExamples").length())
    assertEquals(messages.toString(), payload.getJSONArray("messages").toString())
    assertEquals("moja intencja", payload.getString("draft"))
  }
  @Test fun usesRecentBoundedSamplesWithoutChangingTheirSpelling() {
    val history = JSONArray()
    repeat(45) { history.put(JSONObject().put("isMe", true).put("text", "no dobra xd $it")) }
    val samples = DeepSeek.userContent(messages, "", history).getJSONObject("styleInput").getJSONArray("conversationExamples")
    assertEquals(40, samples.length())
    assertEquals("no dobra xd 5", samples.getString(0))
    assertEquals("no dobra xd 44", samples.getString(39))
    assertEquals(0, DeepSeek.userContent(messages, "").getJSONObject("styleInput").getJSONArray("conversationExamples").length())
  }
  @Test fun storesWritingStyleSeparatelyAndBoundsItsFields() {
    val raw = result().put("writingStyle", JSONObject().put("summary", "x".repeat(900))
      .put("habits", JSONArray().put("małe litery")).put("extra", "omit"))
    val style = DeepSeek.validate(raw, messages).getJSONObject("writingStyle")
    assertEquals(600, style.getString("summary").length)
    assertEquals("małe litery", style.getJSONArray("habits").getString(0))
    assertFalse(style.has("extra"))
  }
  @Test fun fallsBackToGeneralUntilLocalStyleHasEnoughEvidence() {
    val general = JSONArray().put(JSONObject().put("isMe", true).put("text", "dobra spoko ogarne"))
    val local = JSONArray()
    repeat(7) { local.put(JSONObject().put("isMe", true).put("text", "Dziękuję, sprawdzę szczegóły wieczorem $it.")) }
    assertEquals("general", DeepSeek.userContent(messages, "", local, general).getJSONObject("styleInput").getString("activeSource"))
    local.put(JSONObject().put("isMe", true).put("text", "Dziękuję, odezwę się jutro rano."))
    assertEquals("conversation", DeepSeek.userContent(messages, "", local, general).getJSONObject("styleInput").getString("activeSource"))
    assertEquals("insufficient", DeepSeek.userContent(messages, "", JSONArray(), JSONArray()).getJSONObject("styleInput").getString("activeSource"))
  }
  @Test fun generalStyleExcludesDemoAndIncomingMessagesAndBalancesRooms() {
    val frequent = JSONArray()
    repeat(30) { frequent.put(JSONObject().put("isMe", true).put("text", "moja wiadomość $it").put("timestamp", it)) }
    frequent.put(JSONObject().put("isMe", false).put("text", "cudzy styl"))
    val history = DeepSeek.generalWritingHistory(listOf(
      JSONObject().put("messages", frequent),
      JSONObject().put("demo", true).put("messages", frequent),
      JSONObject().put("messages", JSONArray().put(JSONObject().put("isMe", true).put("text", "inna rozmowa").put("timestamp", 40)))
    ))
    assertEquals(11, history.length())
    assertEquals("inna rozmowa", history.getJSONObject(10).getString("text"))
  }
}
