package expo.modules.guardian

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NotificationContextTest {
  private fun result(risk: String) = JSONObject().put("risk", risk)
  @Test fun `only elevated risk retains notification previews`() {
    val messages = listOf(PrivateMessage("hashed-private-key", "Testowa wiadomość", 10, "Testowy nadawca"))
    listOf("low", "uncertain").forEach { risk ->
      assertFalse(NotificationContext.attach(result(risk), "Tytuł", 100, messages).has("notificationContext"))
    }
    listOf("medium", "high").forEach { risk ->
      val context = NotificationContext.attach(result(risk), "Tytuł", 100, messages).getJSONObject("notificationContext")
      assertEquals("Tytuł", context.getString("title"))
      assertEquals(100L, context.getLong("receivedAt"))
      val message = context.getJSONArray("messages").getJSONObject(0)
      assertEquals("Testowy nadawca", message.getString("sender"))
      assertEquals("Testowa wiadomość", message.getString("text"))
      assertFalse(context.toString().contains("hashed-private-key"))
    }
  }
  @Test fun `previews are bounded and preserve the analyzed conversation order`() {
    val messages = (1..7).map { PrivateMessage("id-$it", "$it".repeat(1500), it.toLong(), "s".repeat(200)) }
    val context = NotificationContext.attach(result("high"), "t".repeat(200), 100, messages).getJSONObject("notificationContext")
    assertEquals(120, context.getString("title").length)
    val previews = context.getJSONArray("messages")
    assertEquals(5, previews.length())
    assertTrue(previews.getJSONObject(0).getString("text").startsWith("3"))
    assertTrue(previews.getJSONObject(4).getString("text").startsWith("7"))
    assertEquals(301, previews.getJSONObject(4).getString("text").length)
    assertEquals(120, previews.getJSONObject(4).getString("sender").length)
    assertFalse(NotificationContext.attach(result("high"), "", 100, emptyList()).has("notificationContext"))
  }
}
