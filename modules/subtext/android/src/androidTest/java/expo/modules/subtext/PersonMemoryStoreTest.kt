package expo.modules.subtext

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonMemoryStoreTest {
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
