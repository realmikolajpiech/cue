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
  @Test fun intensitySetsHowHardToPursueTheGoal() {
    assertTrue(ConversationGoal.intent("", "flirt", "piątek 18", 2).contains("Do celu dąż aktywnie"))
    assertTrue(ConversationGoal.intent("", "flirt", "piątek 18", 0).contains("Do celu dąż powoli"))
    assertTrue(ConversationGoal.intent("", "natural", "piątek 18", 2).contains("Do celu dąż naturalnie"))
    assertFalse(ConversationGoal.intent("", "flirt", "", 2).contains("Do celu dąż"))
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
