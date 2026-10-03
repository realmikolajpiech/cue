package expo.modules.guardian

import android.app.Notification
import androidx.core.app.NotificationCompat
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

object NotificationNormalizer {
  val allowedApps = mapOf(
    "com.whatsapp" to "WhatsApp", "com.facebook.orca" to "Messenger", "com.beeper.android" to "Beeper",
    "com.google.android.apps.messaging" to "SMS", "com.samsung.android.messaging" to "SMS",
  )
  data class Input(val key: String, val source: String, val messages: List<PrivateMessage>)
  private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
  fun key(sbn: StatusBarNotification): String {
    // Conversation shortcut is preferred. Missing shortcut falls back to notification identity,
    // never to contact display names or a package-wide shared buffer.
    val identity = sbn.notification.shortcutId?.takeIf { it.isNotBlank() } ?: sbn.key
    return hash("${sbn.packageName}:$identity")
  }
  fun normalize(sbn: StatusBarNotification): Input? {
    val source = allowedApps[sbn.packageName] ?: return null
    val n = sbn.notification
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
    val now = SystemClock.elapsedRealtime()
    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
    val messages = if (style != null) {
      style.messages.takeLast(5).mapNotNull { message ->
        val text = message.text?.toString()?.take(1500)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val age = (System.currentTimeMillis() - message.timestamp).coerceAtLeast(0)
        val sender = message.person?.key ?: message.person?.name?.toString() ?: ""
        PrivateMessage(hash("${message.timestamp}:$sender"), text, now - age)
      }
    } else {
      val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.take(1500)
      if (text.isNullOrBlank()) emptyList() else listOf(PrivateMessage(hash("${sbn.key}:${hash(text)}"), text, now))
    }
    if (messages.isEmpty()) return null
    return Input(key(sbn), source, messages)
  }
}
