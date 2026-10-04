package expo.modules.subtext

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonMemoryStoreTest {
  @Test fun resetClearsOnlySelectedPersonAndPreventsHistoryReimportAfterRestart() {
    val target = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(target.cacheDir, "reset-test-${UUID.randomUUID()}").apply { mkdirs() }
    val isolated = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = directory }
    fun message(id: String, time: Long) = JSONObject().put("id", id).put("text", "spoko $id")
      .put("timestamp", time).put("isMe", true)
    try {
      val store = SubtextStore(isolated)
      val old = message("old", 1000)
      store.merge("messenger", "person", "Osoba", "PRIVATE", listOf(old))
      store.merge("whatsapp", "other", "Inna", "PRIVATE", listOf(old))
      store.profile("messenger:person", JSONObject().put("summary", "old context"))
      store.resetConversation("messenger:person", 2000)
      val restored = SubtextStore(isolated)
      assertEquals(0, restored.room("messenger:person")!!.getJSONArray("messages").length())
      assertTrue(restored.room("messenger:person")!!.isNull("profile"))
      assertEquals("", restored.room("messenger:person")!!.getString("snippet"))
      assertFalse(restored.memory("messenger:person").has("sampleCount"))
      assertFalse(restored.memory("messenger:person").has("seen"))
      assertEquals(1, restored.memory("whatsapp:other").getInt("sampleCount"))
      restored.merge("messenger", "person", "Osoba", "PRIVATE", listOf(old, message("boundary", 2000), message("new", 2001)))
      restored.flush()
      val afterSync = SubtextStore(isolated)
      assertEquals(1, afterSync.room("messenger:person")!!.getJSONArray("messages").length())
      assertEquals("new", afterSync.room("messenger:person")!!.getJSONArray("messages").getJSONObject(0).getString("id"))
      assertEquals(1, afterSync.memory("messenger:person").getInt("sampleCount"))
    } finally { directory.deleteRecursively() }
  }
  @Test fun remindersAndManualDecisionsSurviveRestartAndRemainInAiMemory() {
    val target = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(target.cacheDir, "reminder-test-${UUID.randomUUID()}").apply { mkdirs() }
    val isolated = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = directory }
    val source = JSONObject().put("id", "meeting").put("text", "Spotkajmy się w poniedziałek o 18")
      .put("timestamp", 1000).put("isMe", false)
    try {
      val store = SubtextStore(isolated)
      store.merge("messenger", "person", "Osoba", "PRIVATE", listOf(source))
      store.flush()
      val change = JSONObject().put("replaceId", "").put("text", "Spotkanie w poniedziałek")
        .put("kind", "meeting").put("owner", "both").put("status", "open")
        .put("dueDate", "2026-10-05T18:00:00+02:00").put("evidenceIds", JSONArray().put("meeting"))
      store.updateContext("messenger:person", JSONArray(), JSONArray().put(source), 1, JSONArray().put(change))
      val restored = SubtextStore(isolated)
      val reminders = restored.memory("messenger:person").getJSONArray("reminders")
      val id = reminders.getJSONObject(0).getString("id")
      assertEquals(1, PersonMemory.input(restored.memory("messenger:person")).getJSONArray("reminders").length())
      restored.editReminder("messenger:person", id, JSONObject().put("status", "done"))
      val afterEdit = SubtextStore(isolated)
      assertEquals("done", ConversationReminders.overview(afterEdit.memory("messenger:person")).getJSONObject(0).getString("effectiveStatus"))
      afterEdit.editReminder("messenger:person", id, JSONObject().put("delete", true))
      val deleted = SubtextStore(isolated)
      deleted.updateContext("messenger:person", JSONArray(), JSONArray().put(source), 1, JSONArray().put(change))
      assertEquals(0, deleted.memory("messenger:person").getJSONArray("reminders").length())
    } finally { directory.deleteRecursively() }
  }
  @Test fun memorySurvivesRestartHistoryTrimmingAndRoomEvictionButClearsWithAccount() {
    val target = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(target.cacheDir, "memory-test-${UUID.randomUUID()}").apply { mkdirs() }
    val isolated = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = directory }
    fun message(id: Int) = JSONObject().put("id", "m$id").put("text", "dobra spoko $id")
      .put("timestamp", id).put("isMe", true)
    try {
      val store = SubtextStore(isolated)
      store.merge("messenger", "person", "Osoba", "PRIVATE", (1..240).map(::message))
      store.merge("whatsapp", "person", "Osoba", "PRIVATE", listOf(message(1)))
      store.flush()
      assertEquals(200, store.room("messenger:person")!!.getJSONArray("messages").length())
      assertEquals(240, store.memory("messenger:person").getInt("sampleCount"))
      val restored = SubtextStore(isolated)
      restored.merge("messenger", "person", "Osoba", "PRIVATE", (1..240).map(::message))
      restored.flush()
      assertEquals(240, restored.memory("messenger:person").getInt("sampleCount"))
      assertEquals(1, restored.memory("whatsapp:person").getInt("sampleCount"))
      // Evict this inactive room from the 150-room cache, then discover it again.
      for (i in 1..151) restored.merge("messenger", "other$i", "Inna osoba", "PRIVATE", timestamp = 1000L + i)
      assertNull(restored.room("messenger:person"))
      restored.merge("messenger", "person", "Osoba", "PRIVATE", listOf(message(240), message(241)))
      restored.flush()
      assertEquals(241, restored.memory("messenger:person").getInt("sampleCount"))
      restored.clear("messenger")
      val cleared = SubtextStore(isolated)
      assertEquals(0, cleared.memory("messenger:person").length())
      assertEquals(1, cleared.memory("whatsapp:person").getInt("sampleCount"))
      cleared.clear()
      assertEquals(0, SubtextStore(isolated).memory("whatsapp:person").length())
    } finally { directory.deleteRecursively() }
  }
}
