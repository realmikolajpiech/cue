package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConversationRemindersTest {
  private val messages = JSONArray().put(JSONObject().put("id", "proof").put("timestamp", 1000))
  private fun change(kind: String = "meeting", date: String = "2026-10-05T18:00:00+02:00", status: String = "open",
    replace: String = "", text: String = "Spotkanie w poniedziałek") = JSONObject().put("replaceId", replace)
    .put("text", text).put("kind", kind).put("owner", "both").put("status", status).put("dueDate", date)
    .put("evidenceIds", JSONArray().put("proof"))
  private fun add(memory: JSONObject, item: JSONObject = change()) = ConversationReminders.apply(memory,
    JSONArray().put(item), messages, memory.optJSONArray("reminders") ?: JSONArray(), 100)

  @Test fun preservesEntriesAndUpdatesTheSameMeetingWithoutDuplicates() {
    val memory = JSONObject(); add(memory)
    val snapshot = JSONArray(memory.getJSONArray("reminders").toString())
    val id = snapshot.getJSONObject(0).getString("id")
    add(memory) // Replay the same proposal, including its unchanged date.
    assertEquals(1, memory.getJSONArray("reminders").length())
    add(memory, change(date = "2026-10-06", replace = id))
    assertEquals(id, memory.getJSONArray("reminders").getJSONObject(0).getString("id"))
    assertEquals("2026-10-06", memory.getJSONArray("reminders").getJSONObject(0).getString("dueDate"))
    ConversationReminders.apply(memory, JSONArray(), messages, snapshot)
    assertEquals(1, memory.getJSONArray("reminders").length())
  }

  @Test fun timePassingNeverMarksAMeetingOrPromiseAsDone() {
    val memory = JSONObject(); add(memory)
    val before = Instant.parse("2026-10-05T15:00:00Z").toEpochMilli()
    val after = Instant.parse("2026-10-05T17:00:00Z").toEpochMilli()
    assertEquals("upcoming", ConversationReminders.overview(memory, before).getJSONObject(0).getString("effectiveStatus"))
    assertEquals("past", ConversationReminders.overview(memory, after).getJSONObject(0).getString("effectiveStatus"))
    assertEquals("open", memory.getJSONArray("reminders").getJSONObject(0).getString("status"))
    val promise = JSONObject(); add(promise, change(kind = "commitment"))
    assertEquals("overdue", ConversationReminders.overview(promise, after).getJSONObject(0).getString("effectiveStatus"))
    val temporary = JSONObject(); add(temporary, change(kind = "important"))
    assertEquals("expired", ConversationReminders.overview(temporary, after).getJSONObject(0).getString("effectiveStatus"))
  }

  @Test fun dateOnlyDeadlineCoversTheWholeLocalDayAndDstTransition() {
    val zone = ZoneId.of("Europe/Warsaw")
    assertEquals(Instant.parse("2026-10-25T22:59:59.999Z").toEpochMilli(), ConversationReminders.dueAt("2026-10-25", zone))
    assertEquals(Instant.parse("2026-03-29T21:59:59.999Z").toEpochMilli(), ConversationReminders.dueAt("2026-03-29", zone))
    assertNull(ConversationReminders.dueAt(""))
    assertTrue(runCatching { ConversationReminders.dueAt("2026-02-30") }.isFailure)
    assertTrue(runCatching { ConversationReminders.dueAt("2026-10-05T18:00:00") }.isFailure)
  }

  @Test fun undatedDebtsInBothDirectionsStayOpenAndSeparateUntilSettled() {
    val memory = JSONObject()
    add(memory, change(kind = "commitment", date = "", text = "Masz oddać rozmówcy 20 zł").put("owner", "me"))
    add(memory, change(kind = "commitment", date = "", text = "Rozmówca ma oddać Ci 50 zł").put("owner", "other"))
    val reopened = JSONObject(memory.toString())
    val entries = ConversationReminders.overview(reopened, Instant.parse("2036-10-03T12:00:00Z").toEpochMilli())
    assertEquals(2, entries.length())
    val own = (0 until entries.length()).map { entries.getJSONObject(it) }.first { it.getString("owner") == "me" }
    val other = (0 until entries.length()).map { entries.getJSONObject(it) }.first { it.getString("owner") == "other" }
    assertEquals("open", own.getString("effectiveStatus"))
    assertEquals("open", other.getString("effectiveStatus"))
    add(reopened, change(kind = "commitment", date = "", text = "Masz oddać rozmówcy jeszcze 10 zł", replace = own.getString("id")).put("owner", "me"))
    assertEquals(2, reopened.getJSONArray("reminders").length())
    add(reopened, change(kind = "commitment", date = "", text = "Dług 20 zł spłacony", status = "done", replace = own.getString("id")).put("owner", "me"))
    val remaining = ConversationReminders.overview(reopened, Long.MAX_VALUE)
    assertEquals("other", remaining.getJSONObject(0).getString("owner"))
    assertEquals("open", remaining.getJSONObject(0).getString("effectiveStatus"))
    assertEquals("done", remaining.getJSONObject(1).getString("effectiveStatus"))
  }

  @Test fun rejectsUnsupportedEvidenceAndMalformedDatesAndOwners() {
    val memory = JSONObject()
    add(memory, change().put("evidenceIds", JSONArray().put("invented")))
    add(memory, change(date = "jutro"))
    add(memory, change().put("owner", "unknown"))
    add(memory, change(replace = "invented"))
    assertEquals(0, memory.getJSONArray("reminders").length())
  }

  @Test fun proposalsStayTentativeAndOnlyExplicitEvidenceClosesOrCancelsThem() {
    val memory = JSONObject(); add(memory, change(status = "tentative"))
    assertEquals("tentative", ConversationReminders.overview(memory, 1).getJSONObject(0).getString("effectiveStatus"))
    val id = memory.getJSONArray("reminders").getJSONObject(0).getString("id")
    add(memory, change(replace = id, status = "done"))
    assertEquals("done", ConversationReminders.overview(memory, Long.MAX_VALUE).getJSONObject(0).getString("effectiveStatus"))
    add(memory, change(replace = id, status = "cancelled"))
    assertEquals("cancelled", ConversationReminders.overview(memory, Long.MAX_VALUE).getJSONObject(0).getString("effectiveStatus"))
  }

  @Test fun manualChangesSurviveInFlightAnalysisAndReplayedOldMessages() {
    val memory = JSONObject(); add(memory)
    val snapshot = JSONArray(memory.getJSONArray("reminders").toString())
    val id = snapshot.getJSONObject(0).getString("id")
    ConversationReminders.edit(memory, id, JSONObject().put("status", "done"), 2000)
    ConversationReminders.apply(memory, JSONArray().put(change(replace = id)), messages, snapshot, 3000)
    assertEquals("done", memory.getJSONArray("reminders").getJSONObject(0).getString("status"))
    add(memory, change(replace = id))
    assertEquals("done", memory.getJSONArray("reminders").getJSONObject(0).getString("status"))
    val newer = JSONArray().put(JSONObject().put("id", "proof").put("timestamp", 4000))
    ConversationReminders.apply(memory, JSONArray().put(change(replace = id)), newer, memory.getJSONArray("reminders"), 5000)
    assertEquals("open", memory.getJSONArray("reminders").getJSONObject(0).getString("status"))
  }

  @Test fun deletedEntriesCannotBeReintroducedByReplayAndOtherChatsStayIndependent() {
    val first = JSONObject(); val second = JSONObject(); add(first); add(second)
    val id = first.getJSONArray("reminders").getJSONObject(0).getString("id")
    ConversationReminders.edit(first, id, JSONObject().put("delete", true))
    add(first)
    assertEquals(0, first.getJSONArray("reminders").length())
    assertEquals(1, second.getJSONArray("reminders").length())
  }
}
