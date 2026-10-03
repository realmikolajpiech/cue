package expo.modules.subtext

/** Text-only context: media markers never claim that the image was inspected. */
internal object MessageMedia {
  const val PHOTO = "[Zdjęcie]"
  fun text(body: String, photo: Boolean): String {
    val normalized = body.trim()
    val placeholder = normalized.lowercase() in setOf("[image]", "[photo]", "[zdjęcie]")
    if (!photo && !placeholder) return body
    val caption = if (placeholder) "" else normalized
    return PHOTO + if (caption.isBlank()) "" else "\n$caption"
  }
  fun preview(text: String, isMe: Boolean): String = if (text.startsWith(PHOTO)) {
    val caption = text.removePrefix(PHOTO).trim()
    (if (isMe) "Ty: wysłano zdjęcie" else "Wysłano zdjęcie") + if (caption.isBlank()) "" else ": $caption"
  } else text
}
