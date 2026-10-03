package expo.modules.subtext

import expo.modules.subtext.messenger.parseMessengerMessage
import expo.modules.subtext.whatsapp.parseWhatsAppMessage
import org.junit.Assert.*
import org.junit.Test

class MessageMediaTest {
  @Test fun messengerPhotoWithoutCaptionStaysInHistory() {
    val item = requireNotNull(parseMessengerMessage("""{"threadKey":"thread","messageId":"photo","text":"","imageMime":"image/jpeg","isMe":true}"""))
    assertEquals("[Zdjęcie]", item.text)
    assertEquals("Ty: wysłano zdjęcie", MessageMedia.preview(item.text, item.isMe))
  }
  @Test fun whatsappPhotoPreservesCaption() {
    val item = requireNotNull(parseWhatsAppMessage("""{"conversationID":"chat","messageID":"photo","timestamp":"1791057155000000","messageInfo":[{"messageContent":{"content":"Co myślisz?"},"mediaContent":{"format":"IMAGE","mimeType":"image/jpeg"}}]}"""))
    assertEquals("[Zdjęcie]\nCo myślisz?", item.text)
    assertEquals("Wysłano zdjęcie: Co myślisz?", MessageMedia.preview(item.text, false))
  }
  @Test fun doesNotDuplicateUpstreamPlaceholder() {
    assertEquals("[Zdjęcie]", MessageMedia.text("[Image]", true))
  }
  @Test fun plainTextAndSharedLinksAreNotPhotos() {
    assertEquals("Lubię zdjęcia", MessageMedia.text("Lubię zdjęcia", false))
    assertEquals("[Link]\nhttps://example.com", MessageMedia.text("[Link]\nhttps://example.com", false))
  }
}
