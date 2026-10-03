package expo.modules.guardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

object GuardianWarnings {
  fun post(context: Context, result: JSONObject): Boolean {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("guardian_risk", "Ostrzeżenia Guardian", NotificationManager.IMPORTANCE_HIGH))
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val id = result.getString("id")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("guardian://alert/$id")).setPackage(context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val icon = context.applicationInfo.icon
    val notification = NotificationCompat.Builder(context, "guardian_risk")
      .setSmallIcon(icon).setContentTitle("Guardian: wysokie ryzyko")
      .setContentText("Zatrzymaj się i sprawdź prośbę. Dotknij, aby zobaczyć przesłanki.")
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending).build()
    return try { manager.notify(id.hashCode(), notification); true } catch (_: SecurityException) { false }
  }
}
