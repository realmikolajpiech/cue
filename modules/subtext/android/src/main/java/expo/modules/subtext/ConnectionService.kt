package expo.modules.subtext

import android.app.*
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class ConnectionService : Service() {
  private var job: Job? = null
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onCreate() {
    super.onCreate()
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("subtext_connections", "Połączenia Cue", NotificationManager.IMPORTANCE_LOW))
    val open = packageManager.getLaunchIntentForPackage(packageName)!!
    val pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    startForeground(8173, Notification.Builder(this, "subtext_connections").setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle("Cue · komunikatory").setContentText("Połączenia działają na tym telefonie")
      .setContentIntent(pending).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
  }
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val runtime = SubtextRuntime.get(this)
    if (!runtime.prefs.getBoolean("background", false)) { stopSelf(); return START_NOT_STICKY }
    if (job == null) job = runtime.scope.launch {
      while (isActive) { runCatching { runtime.restore() }; delay(60000) }
    }
    return START_STICKY
  }
  override fun onDestroy() { job?.cancel(); job = null; super.onDestroy() }
}
