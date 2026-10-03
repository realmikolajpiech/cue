package expo.modules.guardian

import org.junit.Assert.*
import org.junit.Test

class ConversationBufferTest {
  private var now = 0L
  private fun message(id: String, text: String = "Wiadomość", time: Long = now) = PrivateMessage(id, text, time)
  @Test fun `expires without new messages and clears on pause`() {
    val buffer = ConversationBuffer({ now }, ttl = 100)
    buffer.append("one", listOf(message("a")))
    now = 100
    assertEquals(0, buffer.size())
    buffer.append("one", listOf(message("b")))
    buffer.clear(); assertEquals(0, buffer.size())
  }
  @Test fun `keeps only five recent messages and bounds text`() {
    val buffer = ConversationBuffer({ now }, maxChars = 10)
    val output = buffer.append("one", (1..8).map { message("$it", "a".repeat(30)) })!!
    assertEquals(5, output.size)
    assertEquals("4", output.first().identity)
    assertEquals(10, output.last().text.length)
  }
  @Test fun `does not mix conversations and evicts least recently touched`() {
    val buffer = ConversationBuffer({ now }, maxConversations = 2)
    buffer.append("a", listOf(message("1"))); buffer.append("b", listOf(message("2")))
    buffer.append("c", listOf(message("3")))
    assertEquals(2, buffer.size())
    assertNotNull(buffer.append("a", listOf(message("1"))))
  }
  @Test fun `updates identity without producing duplicate messages`() {
    val buffer = ConversationBuffer({ now })
    buffer.append("a", listOf(message("1")))
    assertNull(buffer.append("a", listOf(message("1"))))
    val output = buffer.append("a", listOf(message("1", "Zmiana")))!!
    assertEquals(1, output.size); assertEquals("Zmiana", output.single().text)
  }
  @Test fun `rejects stale messages and supports removal`() {
    val buffer = ConversationBuffer({ now }, ttl = 100)
    now = 101
    assertNull(buffer.append("a", listOf(message("1", time = 0))))
    buffer.append("a", listOf(message("2"))); buffer.remove("a")
    assertEquals(0, buffer.size())
  }
  @Test fun `replayed older messages do not reanalyze an unchanged retained context`() {
    now = 100
    val buffer = ConversationBuffer({ now }, maxMessages = 2)
    val retained = listOf(message("b", "Druga", 20), message("c", "Trzecia", 30))
    assertEquals(retained, buffer.append("a", retained))
    repeat(10) {
      assertNull(buffer.append("a", listOf(message("old", "Pierwsza", 10)) + retained))
    }
    // A change to an actual retained message still triggers immediate analysis.
    val changed = retained.last().copy(text = "Zmiana treści")
    assertEquals(listOf(retained.first(), changed), buffer.append("a", listOf(changed)))
  }
  @Test fun `new retained messages are analyzed even when their text repeats`() {
    val buffer = ConversationBuffer({ now }, maxMessages = 2)
    buffer.append("a", listOf(message("one", "Ten sam tekst", 0)))
    assertNotNull(buffer.append("a", listOf(message("two", "Ten sam tekst", 1))))
  }
}
