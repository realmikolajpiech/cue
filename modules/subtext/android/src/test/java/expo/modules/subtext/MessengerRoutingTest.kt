package expo.modules.subtext

import expo.modules.subtext.device.ConversationKind
import expo.modules.subtext.messenger.parseMessengerConversation
import expo.modules.subtext.messenger.parseMessengerEncryptedConversation
import expo.modules.subtext.messenger.resolveMessengerConversationId
import org.junit.Assert.*
import org.junit.Test

class MessengerRoutingTest {
  @Test fun routesEncryptedRecipientToInboxThread() {
    val kinds = mapOf("thread" to ConversationKind.PRIVATE, "42" to ConversationKind.PRIVATE)
    assertEquals("thread", resolveMessengerConversationId("42", kinds, mapOf("thread" to "42"), emptyMap()))
    assertEquals("thread", resolveMessengerConversationId("42", kinds, emptyMap(), mapOf("thread" to "42@msgr")))
  }

  @Test fun routesJidAndDeviceQualifiedRecipientToInboxThread() {
    val kinds = mapOf("thread" to ConversationKind.PRIVATE)
    assertEquals("thread", resolveMessengerConversationId("42@msgr", kinds, mapOf("thread" to "42"), emptyMap()))
    assertEquals("thread", resolveMessengerConversationId("42:7@msgr", kinds, emptyMap(), mapOf("thread" to "42@msgr")))
  }

  @Test fun neverRoutesGroupMessagesIntoPrivateThread() {
    val kinds = mapOf("42" to ConversationKind.GROUP, "thread" to ConversationKind.PRIVATE)
    assertEquals("42", resolveMessengerConversationId("42", kinds, mapOf("thread" to "42"), emptyMap()))
    assertEquals("missing", resolveMessengerConversationId("missing", kinds, emptyMap(), emptyMap()))
  }

  @Test fun privateRecipientMetadataWorksWithoutGroupFlag() {
    assertEquals(ConversationKind.PRIVATE, parseMessengerConversation("""{"threadKey":"thread","e2eeRecipientId":"42@msgr"}""")?.kind)
    assertEquals(ConversationKind.UNKNOWN, parseMessengerConversation("""{"threadKey":"thread"}""")?.kind)
    assertEquals(ConversationKind.GROUP, parseMessengerConversation("""{"threadKey":"thread","isGroup":true,"contactId":"42"}""")?.kind)
  }

  @Test fun encryptedDeliveryIdentifiesPrivateChatBeforeMessageEvent() {
    val room = parseMessengerEncryptedConversation("""{"step":"MESSAGE","chat":"42@msgr","ts":100}""")
    assertEquals("42", room?.id)
    assertEquals(ConversationKind.PRIVATE, room?.kind)
    assertEquals(100_000L, room?.timestamp)
    assertNull(parseMessengerEncryptedConversation("""{"step":"MESSAGE","chat":"42@g.us"}"""))
    assertNull(parseMessengerEncryptedConversation("""{"step":"event","chat":"42@msgr"}"""))
  }
}
