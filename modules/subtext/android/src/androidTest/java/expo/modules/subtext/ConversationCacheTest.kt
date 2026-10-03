package expo.modules.subtext

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConversationCacheTest {
  @get:Rule val directory = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
  private fun store(): SubtextStore = SubtextStore(object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
    override fun getNoBackupFilesDir(): File = directory.root
  })
  private fun message(id: String, timestamp: Long) = JSONObject().put("id", id)
    .put("text", "Test message").put("timestamp", timestamp).put("sender", "Test").put("isMe", false)

  @Test fun inboxSummariesNeverRemoveCachedMessages() {
    val store = store()
    store.merge("messenger", "one", "Test Person", "PRIVATE", listOf(message("a", 10), message("b", 20)))
    val summary = store.summaries().single()
    assertFalse(summary.has("messages"))
    assertEquals(2, summary.getInt("messageCount"))
    assertEquals(2, store.room("messenger:one")!!.getJSONArray("messages").length())
    summary.put("name", "Changed by caller")
    assertEquals("Test Person", store.room("messenger:one")!!.getString("name"))
  }

  @Test fun inboxKeepsPrivateConversationsInRecencyOrder() {
    val store = store()
    store.merge("messenger", "old", "Old", "PRIVATE", timestamp = 10)
    store.merge("whatsapp", "recent", "Recent", "PRIVATE", timestamp = 30)
    store.merge("messenger", "group", "Group", "GROUP", timestamp = 40)
    assertEquals(listOf("whatsapp:recent", "messenger:old"), store.summaries().map { it.getString("id") })
    assertNull(store.room("messenger:group"))
  }
}
