package expo.modules.guardian

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName

class GuardianNotificationService : NotificationListenerService() {
  private val runtime get() = GuardianRuntime.get(this)
  override fun onListenerConnected() {
    runtime.connect {
      // After process restart, don't lose a recent notification that is still visible.
      // Fetch only after the engine is ready; the normal buffer deduplicates updates.
      val visible = activeNotifications ?: emptyArray()
      val saved = runtime.store.list().associateBy { it.getString("id").hashCode() }
      visible.filter { it.packageName == packageName && it.notification.channelId in setOf("guardian_risk", GuardianWarnings.HIGH_RISK_CHANNEL) }.forEach { warning ->
        saved[warning.id]?.let { GuardianWarnings.post(this, it) }
      }
      val cutoff = System.currentTimeMillis() - 15 * 60 * 1000L
      visible.filter { it.postTime > cutoff }.forEach(::onNotificationPosted)
    }
  }
  override fun onListenerDisconnected() {
    runtime.disconnect()
    if (runtime.permissionGranted()) requestRebind(ComponentName(this, GuardianNotificationService::class.java))
  }
  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (!runtime.enabled || sbn.packageName == packageName) return
    // Never log parsing exceptions: extras and exception messages may contain private data.
    runCatching { NotificationNormalizer.normalize(sbn)?.let(runtime::accept) }
  }
  override fun onNotificationRemoved(sbn: StatusBarNotification) {
    if (sbn.packageName in NotificationNormalizer.allowedApps) runtime.remove(NotificationNormalizer.key(sbn))
  }
  override fun onDestroy() { runtime.disconnect(); super.onDestroy() }
}
