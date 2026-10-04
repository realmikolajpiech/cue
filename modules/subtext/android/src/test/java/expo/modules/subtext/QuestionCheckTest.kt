package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuestionCheckTest {
  @Test fun recognisesYesNoQuestions() {
    for (text in listOf("Wanna go to a volleyball match tomorrow?", "Idziesz jutro na mecz?", "Jak tam, masz chwilę dziś?",
      "Czy możesz mi pomóc?", "Hej! Wpadniesz w sobotę?", "Do you have time tonight?"))
      assertTrue(text, QuestionCheck.looksLikeYesNo(text))
  }
  @Test fun ignoresOpenChoiceAndShortQuestions() {
    for (text in listOf("Co u ciebie?", "Where's the match and what time?", "Kino czy kręgle?", "Tea or coffee?", "Serio?",
      "Jak się masz?", "Ok.", "Kiedy wracasz?", "[Zdjęcie]\nfajne?"))
      assertFalse(text, QuestionCheck.looksLikeYesNo(text))
  }
  private fun message(id: String, text: String, me: Boolean) = JSONObject().put("id", id).put("text", text).put("isMe", me)
  @Test fun onlyUnansweredQuestionsCountAndTheAiVerdictWinsWhenCurrent() {
    val asked = JSONArray(listOf(message("1", "Hej", true), message("2", "Idziesz jutro na mecz?", false)))
    assertEquals("2", QuestionCheck.open(asked, null)!!.getString("messageId"))
    // Already answered by the user.
    assertNull(QuestionCheck.open(JSONArray(listOf(message("2", "Idziesz jutro na mecz?", false), message("3", "Jasne", true))), null))
    // The AI saw the newest message and found nothing to decide.
    assertNull(QuestionCheck.open(asked, JSONObject().put("analyzed", "2")))
    val verdict = JSONObject().put("analyzed", "2").put("messageId", "2").put("question", "Idziesz jutro na mecz siatkówki?")
    assertEquals("Idziesz jutro na mecz siatkówki?", QuestionCheck.open(asked, verdict)!!.getString("question"))
    // An older verdict falls back to the local check for the newer message.
    assertEquals("Idziesz jutro na mecz?", QuestionCheck.open(asked, JSONObject().put("analyzed", "1"))!!.getString("question"))
  }
  @Test fun decisionTravelsToThePrompt() {
    val intent = ConversationGoal.intent("", "natural", "kawa", decision = JSONObject().put("question", "Idziesz?").put("answer", "no"))
    val data = JSONObject(intent.substringAfter("Dane użytkownika w JSON: "))
    assertEquals("nie", data.getJSONObject("decyzja").getString("odpowiedz"))
    assertTrue(intent.contains("pierwszeństwo przed celem"))
    assertEquals("", data.getString("celRozmowy"))
    assertFalse(ConversationGoal.intent("", "natural", "").contains("\"decyzja\""))
  }
  @Test fun everyCombinationStaysWithinTheGatewayLimitAndTerminates() {
    val plan = JSONObject().put("steps", JSONArray(List(6) { "x".repeat(80) })).put("stage", 3).put("moment", "good").put("note", "n".repeat(200))
    val decision = JSONObject().put("question", "q".repeat(200)).put("answer", "unsure")
    for (tone in WritingTone.labels.keys) for (level in 0..2) {
      val intent = ConversationGoal.intent("d".repeat(3000), tone, "g".repeat(1000), level, List(6) { "r".repeat(300) },
        " Język: polski.", "s".repeat(600), "k".repeat(500), plan, "t".repeat(300), "", decision)
      assertTrue("$tone $level ${intent.length}", intent.length <= ConversationGoal.LIMIT)
      JSONObject(intent.substringAfter("Dane użytkownika w JSON: "))
    }
    // The instruction itself leaves room for a real draft.
    val bare = ConversationGoal.intent("", "flirt", "g", 2, plan = plan, decision = decision, topic = "t")
    assertTrue(bare.length.toString(), bare.length < ConversationGoal.LIMIT - 1500)
    assertTrue(ConversationGoal.background("g".repeat(1000), plan, " Język: polski.").length <= ConversationGoal.LIMIT)
  }
}
