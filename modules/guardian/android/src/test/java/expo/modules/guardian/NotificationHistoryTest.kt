package expo.modules.guardian

import org.junit.Assert.*
import org.junit.Test

class NotificationHistoryTest {
  @Test fun deduplicatesUpdatesAndExpiresWithoutExtendingLifetime() {
    var now = 1000L
    val history = NotificationHistory { now }
    history.add("chat", "SMS", listOf(PrivateMessage("one", "First", now)), 10_000)
    now += 100
    history.add("chat", "SMS", listOf(PrivateMessage("one", "Updated", 1000)), 10_100)
    assertEquals(1, history.list().size)
    assertEquals("Updated", history.list().single().text)
    assertEquals(10_000L, history.list().single().createdAt)
    now = 901_000
    assertTrue(history.list().isEmpty())
  }
  @Test fun boundsHistoryAndClearsSensitiveText() {
    val history = NotificationHistory { 1000L }
    repeat(105) { history.add("chat", "SMS", listOf(PrivateMessage("$it", "Text", 1000)), 10_000) }
    assertEquals(100, history.list().size)
    history.clear()
    assertTrue(history.list().isEmpty())
  }
  @Test fun excludesOldMessages() {
    val history = NotificationHistory { 1_000_000L }
    history.add("chat", "SMS", listOf(PrivateMessage("one", "Old", 1000)), 10_000)
    assertTrue(history.list().isEmpty())
  }
}
