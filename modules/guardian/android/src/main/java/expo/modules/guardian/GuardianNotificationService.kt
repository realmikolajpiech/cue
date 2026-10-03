package expo.modules.guardian

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName

class GuardianNotificationService : NotificationListenerService() {
  private val runtime get() = GuardianRuntime.get(this)
  override fun onListenerConnected() { runtime.connect() }
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
