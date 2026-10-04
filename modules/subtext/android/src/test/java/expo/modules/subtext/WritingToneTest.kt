package expo.modules.subtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingToneTest {
  @Test fun everyIntensityHasItsOwnInstruction() {
    assertEquals(WritingTone.instruction("flirt"), WritingTone.instruction("flirt", 1))
    for (tone in listOf("flirt", "assertive", "empathetic", "calming"))
      assertEquals(3, (0..2).map { WritingTone.instruction(tone, it) }.distinct().size)
    assertTrue(WritingTone.instruction("flirt", 1).contains("Natężenie zalotne"))
    assertTrue(WritingTone.instruction("flirt", 2).contains("Natężenie odważne"))
    assertTrue(WritingTone.instruction("assertive", 0).contains("Natężenie uprzejme"))
    assertTrue(ConversationGoal.intent("hej", "flirt", "", 2).contains("Natężenie odważne"))
  }
  @Test fun intensitySetsTheTempoThroughThePlan() {
    assertTrue(ConversationGoal.intent("", "flirt", "piątek 18", 2).contains("Tempo aktywne"))
    assertTrue(ConversationGoal.intent("", "flirt", "piątek 18", 0).contains("Tempo spokojne"))
    assertTrue(ConversationGoal.intent("", "natural", "piątek 18", 2).contains("Tempo naturalne"))
    assertFalse(ConversationGoal.intent("", "flirt", "", 2).contains("Tempo "))
    assertFalse(ConversationGoal.intent("", "flirt", "", 2).contains("goalPlan:"))
  }
  @Test fun savedPlanAndTopicTravelWithTheGoal() {
    val plan = org.json.JSONObject("""{"steps":["Ocieplić","Wybadać","Propozycja"],"stage":2,"moment":"wait","note":"czekamy"}""")
    val payload = org.json.JSONObject(ConversationGoal.intent("", "flirt", "kawa", 1, plan = plan, topic = "koncert")
      .substringAfter("Dane użytkownika w JSON: "))
    assertEquals(2, payload.getJSONObject("planCelu").getInt("stage"))
    assertEquals("koncert", payload.getString("tematRozmowy"))
    // Without a goal a stale plan is not sent.
    assertFalse(org.json.JSONObject(ConversationGoal.intent("", "flirt", "", 1, plan = plan).substringAfter("Dane użytkownika w JSON: ")).has("planCelu"))
    assertTrue(ConversationGoal.background("kawa", plan, "").contains("planCelu"))
    assertFalse(ConversationGoal.background("", plan, "").contains("goalPlan"))
  }
  @Test fun topicIsALightOpenerAndARecentTopicOnlyContext() {
    val chosen = ConversationGoal.intent("", "flirt", "", 1, topic = "koncert", recentTopic = "serial")
    assertTrue(chosen.contains("nie cel") && chosen.contains("step=keep"))
    val chosenData = org.json.JSONObject(chosen.substringAfter("Dane użytkownika w JSON: "))
    assertFalse(chosenData.has("ostatniTemat"))
    val later = ConversationGoal.intent("", "flirt", "", 1, recentTopic = "koncert")
    assertTrue(later.contains("temat wygasł"))
    assertEquals("koncert", org.json.JSONObject(later.substringAfter("Dane użytkownika w JSON: ")).getString("ostatniTemat"))
    assertFalse(ConversationGoal.intent("", "flirt", "", 1).contains("ostatniTemat"))
  }
  @Test fun reachedOrDroppedGoalsFinish() {
    assertTrue("done" in ConversationGoal.FINISHED && "dropped" in ConversationGoal.FINISHED && "paused" !in ConversationGoal.FINISHED)
    assertEquals("dropped", ConversationGoal.cleanPlan(org.json.JSONObject("""{"steps":["a","b"],"stage":1,"moment":"dropped"}"""))!!.getString("moment"))
  }
  @Test fun planIsClampedAndValidated() {
    val clean = ConversationGoal.cleanPlan(org.json.JSONObject("""{"steps":["a"," ","b","c"],"stage":9,"moment":"odd"}"""))!!
    assertEquals(3, clean.getJSONArray("steps").length())
    assertEquals(3, clean.getInt("stage"))
    assertEquals("wait", clean.getString("moment"))
    assertEquals(2, ConversationGoal.cleanPlan(org.json.JSONObject("""{"steps":["a","b"],"stage":1,"moment":"done"}"""))!!.getInt("stage"))
    assertEquals(null, ConversationGoal.cleanPlan(org.json.JSONObject("""{"steps":["a"],"stage":1}""")))
  }
  @Test fun regenerationListsRejectedRepliesWithinLimit() {
    val intent = ConversationGoal.intent("hej", "flirt", "piątek 18", 2, listOf("a".repeat(500), "b", "c"))
    assertTrue(intent.contains("nie pasowały"))
    assertTrue(intent.length <= ConversationGoal.LIMIT)
    assertTrue(org.json.JSONObject(intent.substringAfter("Dane użytkownika w JSON: ")).has("odrzucone"))
    assertFalse(ConversationGoal.intent("hej", "flirt", "", 1).contains("nie pasowały"))
  }
  @Test fun naturalToneHasNoLevelsAndOutOfRangeIsClamped() {
    assertTrue(WritingTone.levels("natural").isEmpty())
    assertFalse(WritingTone.instruction("natural", 2).contains("Natężenie"))
    assertEquals(2, WritingTone.clampIntensity("calming", 9))
    assertEquals(WritingTone.hint("empathetic", 2), WritingTone.hint("empathetic", 5))
  }
  @Test fun flirtFollowsTheOtherPersonsTemperatureWithoutShowingIt() {
    val flirt = WritingTone.instruction("flirt", 2)
    assertTrue(flirt.contains("pułap 4") && flirt.contains("najwyżej stopień cieplej"))
    assertTrue(flirt.contains("Temperatury nie podawaj w JSON"))
    assertFalse(WritingTone.instruction("assertive", 2).contains("temperaturę rozmówcy"))
    // The instruction must leave room for the draft and goal inside the gateway limit.
    assertTrue(ConversationGoal.intent("", "flirt", "", 2).length < 3200)
  }
}
