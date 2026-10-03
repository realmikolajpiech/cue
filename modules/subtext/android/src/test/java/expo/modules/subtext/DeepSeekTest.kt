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
}
