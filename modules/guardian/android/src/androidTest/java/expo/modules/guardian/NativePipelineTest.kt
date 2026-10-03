package expo.modules.guardian

import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
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
class NativePipelineTest {
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private fun notification(text: String, key: String = "conversation-a", summary: Boolean = false): StatusBarNotification {
    val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Local user").build())
      .addMessage(text, System.currentTimeMillis(), Person.Builder().setKey("synthetic-person").setName("Synthetic sender").build())
    val n = NotificationCompat.Builder(context, "synthetic")
      .setSmallIcon(android.R.drawable.ic_dialog_info).setShortcutId(key).setStyle(style)
      .setGroup("group").setGroupSummary(summary).build()
    return StatusBarNotification("com.whatsapp", "com.whatsapp", 1, key, 0, 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())
  }
  @Test fun normalizesMessagingStyleWithoutExportingIdentity() {
    val input = NotificationNormalizer.normalize(notification("Syntetyczna wiadomość"))!!
    assertEquals("WhatsApp", input.source)
    assertEquals("Syntetyczna wiadomość", input.messages.single().text)
    assertEquals("Synthetic sender", input.messages.single().sender)
    assertEquals(64, input.key.length)
    assertFalse(input.key.contains("conversation"))
    assertFalse(input.messages.single().identity.contains("synthetic-person"))
    assertNull(NotificationNormalizer.normalize(notification("Syntetyczna wiadomość", summary = true)))
    assertNotEquals(input.key, NotificationNormalizer.normalize(notification("Test", key = "conversation-b"))!!.key)
  }
  @Test fun sameMessageUpdateIsDeduplicatedAndConversationsRemainSeparate() {
    val now = android.os.SystemClock.elapsedRealtime()
    val buffer = ConversationBuffer({ now })
    val sbn = notification("Test")
    val first = NotificationNormalizer.normalize(sbn)!!
    val duplicate = NotificationNormalizer.normalize(sbn)!!
    assertNotNull(buffer.append(first.key, first.messages))
    assertNull(buffer.append(duplicate.key, duplicate.messages))
    val other = NotificationNormalizer.normalize(notification("Test B", "conversation-b"))!!
    assertEquals("Test B", buffer.append(other.key, other.messages)!!.single().text)
    assertEquals(2, buffer.size())
  }
  @Test fun resultStorePersistsPrunesReviewsAndDeletesWithoutRestoration() {
    val directory = File(context.cacheDir, "store-test-${UUID.randomUUID()}").also { it.mkdirs() }
    val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
    var now = System.currentTimeMillis()
    try {
      val store = ResultStore(isolated, { now })
      val result = Assessment.uncertain("SMS")
      store.add(result)
      val restarted = ResultStore(isolated, { now })
      assertEquals(1, restarted.list().size)
      restarted.review(result.getString("id"))
      assertEquals("reviewed", restarted.list().single().getString("reviewStatus"))
      now += 8L * 24 * 60 * 60 * 1000
      assertTrue(restarted.list().isEmpty())
      now = System.currentTimeMillis()
      repeat(205) { restarted.add(Assessment.uncertain("SMS")) }
      assertEquals(200, restarted.list().size)
      restarted.clear()
      assertTrue(ResultStore(isolated, { now }).list().isEmpty())
      File(directory, "guardian-results-v1.json").writeText(JSONObject().put("schemaVersion", 2).put("results", JSONArray()).toString())
      assertTrue(restarted.list().isEmpty())
    } finally { directory.deleteRecursively() }
  }
  @Test fun bridgeResultHasOnlySanitizedWhitelistedFields() {
    val result = Assessment.result(Assessment.parse("""{"risk":"high","category":"credential_theft","signals":["credential_request"]}"""), "SMS")
    assertEquals(setOf("analysisVersion", "schemaVersion", "id", "createdAt", "sourceApp", "risk", "category", "signals", "explanation", "recommendedAction", "analysisSource", "reviewStatus"), result.keys().asSequence().toSet())
  }
  @Test fun warningPreviewSurvivesRestartAndReviewInTheLocalHistory() {
    val directory = File(context.cacheDir, "preview-test-${UUID.randomUUID()}").also { it.mkdirs() }
    val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
    try {
      val input = NotificationNormalizer.normalize(notification("Syntetyczna prośba o kod"))!!
      val result = Assessment.result(Assessment.parse("""{"risk":"high","category":"credential_theft","signals":["credential_request"]}"""), input.source)
      NotificationContext.attach(result, input.title, input.postedAt, input.messages)
      ResultStore(isolated).add(result)
      val restarted = ResultStore(isolated)
      restarted.review(result.getString("id"))
      val saved = restarted.list().single()
      assertEquals("reviewed", saved.getString("reviewStatus"))
      val preview = saved.getJSONObject("notificationContext").getJSONArray("messages").getJSONObject(0)
      assertEquals("Synthetic sender", preview.getString("sender"))
      assertEquals("Syntetyczna prośba o kod", preview.getString("text"))
      restarted.clear()
      assertTrue(ResultStore(isolated).list().isEmpty())
    } finally { directory.deleteRecursively() }
  }
  @Test fun unchangedHistoryReadsDoNotRewriteDiskAndSnapshotsCannotMutateTheStore() {
    val directory = File(context.cacheDir, "store-read-test-${UUID.randomUUID()}").also { it.mkdirs() }
    val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
    try {
      val store = ResultStore(isolated)
      val result = Assessment.uncertain("SMS")
      store.add(result)
      val file = File(directory, "guardian-results-v1.json")
      val sentinel = 1_600_000_000_000L
      assertTrue(file.setLastModified(sentinel))
      val expected = store.serialized()
      repeat(50) { assertEquals(expected, store.serialized()); assertEquals(sentinel, file.lastModified()) }
      result.put("risk", "high")
      store.list().single().put("risk", "high")
      assertEquals("uncertain", store.list().single().getString("risk"))
      store.review(result.getString("id"))
      val reviewed = store.serialized()
      assertEquals("reviewed", JSONObject(reviewed.single()).getString("reviewStatus"))
      assertTrue(file.setLastModified(sentinel))
      store.review(result.getString("id"))
      store.review("missing")
      assertEquals(sentinel, file.lastModified())
      assertEquals(reviewed, ResultStore(isolated).serialized())
      file.delete()
      assertTrue(store.serialized().isEmpty())
      assertFalse(file.exists())
    } finally { directory.deleteRecursively() }
  }
  @Test fun cachedHistoryStillExpiresAtTheRetentionBoundary() {
    val directory = File(context.cacheDir, "store-ttl-test-${UUID.randomUUID()}").also { it.mkdirs() }
    val isolated = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = directory }
    val retention = 7L * 24 * 60 * 60 * 1000
    var now = System.currentTimeMillis()
    try {
      val store = ResultStore(isolated, { now })
      val result = Assessment.uncertain("SMS").put("createdAt", now)
      store.add(result)
      now += retention - 1
      assertEquals(1, store.serialized().size)
      now++
      assertTrue(store.serialized().isEmpty())
      now -= retention
      assertTrue(ResultStore(isolated, { now }).serialized().isEmpty())
    } finally { directory.deleteRecursively() }
  }
}
