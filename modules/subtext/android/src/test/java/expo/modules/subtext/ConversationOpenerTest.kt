package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationOpenerTest {
  private val day = 24 * 60 * 60 * 1000L
  private fun chat(vararg messages: Pair<Boolean, Long>) = JSONArray(messages.mapIndexed { i, (me, time) ->
    JSONObject().put("id", "m$i").put("isMe", me).put("timestamp", time).put("text", "x") })

  @Test fun recognisesFirstMessageReachOutAndLateReply() {
    val now = 100 * day
    assertEquals(ConversationOpener.Kind.FIRST, ConversationOpener.situation(JSONArray(), now)?.kind)
    assertNull(ConversationOpener.situation(chat(false to now - 2 * 60 * 60 * 1000L), now))
    assertEquals(ConversationOpener.Kind.LATE_REPLY, ConversationOpener.situation(chat(true to now - 4 * day, false to now - 3 * day), now)?.kind)
    val reachOut = ConversationOpener.situation(chat(false to now - 40 * day, true to now - 30 * day), now)!!
    assertEquals(ConversationOpener.Kind.REACH_OUT, reachOut.kind)
    assertEquals(1, reachOut.unansweredAttempts)
  }

  @Test fun countsSeparateUnansweredAttemptsNotBursts() {
    val now = 100 * day
    val ignored = chat(false to now - 20 * day, true to now - 10 * day, true to now - 10 * day + 60000, true to now - 3 * day)
    assertEquals(2, ConversationOpener.situation(ignored, now)!!.unansweredAttempts)
    val text = ConversationOpener.instruction(ConversationOpener.situation(ignored, now)!!)
    assertTrue(text.contains("no_reply z radą"))
  }

  @Test fun gapIsGivenInWordsAndChangesTheAdvice() {
    assertEquals("2 dni", ConversationOpener.gapLabel(2 * day))
    assertEquals("3 tygodnie", ConversationOpener.gapLabel(21 * day))
    assertEquals("5 miesięcy", ConversationOpener.gapLabel(150 * day))
    assertEquals("2 lata", ConversationOpener.gapLabel(800 * day))
    val short = ConversationOpener.instruction(ConversationOpener.Situation(ConversationOpener.Kind.REACH_OUT, 2 * day, 1))
    val long = ConversationOpener.instruction(ConversationOpener.Situation(ConversationOpener.Kind.REACH_OUT, 400 * day, 1))
    assertTrue(short.contains("Krótka przerwa") && long.contains("Bardzo długa przerwa"))
  }

  @Test fun newPersonContextStaysInDataAndFitsTheGateway() {
    val situation = ConversationOpener.instruction(ConversationOpener.Situation(ConversationOpener.Kind.FIRST), context = "koncert")
    val intent = ConversationGoal.intent("", "flirt", "", 2, listOf("a".repeat(200)), situation = situation, personContext = "koncert, ma husky")
    assertTrue(intent.length <= ConversationGoal.LIMIT)
    assertEquals("koncert, ma husky", JSONObject(intent.substringAfter("Dane użytkownika w JSON: ")).getString("kontekstOsoby"))
  }
}
