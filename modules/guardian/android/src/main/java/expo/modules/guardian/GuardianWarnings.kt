package expo.modules.guardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

object GuardianWarnings {
  const val HIGH_RISK_CHANNEL = "guardian_high_risk"
  private fun riskColor(risk: String) = Color.parseColor(when (risk) {
    "high" -> "#D92D20"
    "medium" -> "#F79009"
    "low" -> "#EAB308"
    else -> "#667085"
  })
  private fun shield(context: Context, color: Int): Bitmap {
    val icon = requireNotNull(ContextCompat.getDrawable(context, R.drawable.guardian_notification_shield)).mutate()
    icon.setTint(color)
    val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
      icon.setBounds(0, 0, size, size)
      icon.draw(Canvas(it))
    }
  }
  private val titles = mapOf(
    "family_impersonation" to "Możliwe podszywanie się pod bliską osobę",
    "credential_theft" to "Możliwa próba wyłudzenia kodu lub hasła",
    "payment_fraud" to "Podejrzana prośba o pieniądze",
    "suspicious_link" to "Ten link może być niebezpieczny",
    "manipulation" to "Ktoś może wywierać na Ciebie presję",
    "unknown" to "Podejrzana prośba w wiadomości",
  )
  private val signalLabels = mapOf(
    "identity_change" to "Zmiana tożsamości lub numeru",
    "urgency" to "Presja czasu",
    "money_request" to "Prośba o pieniądze",
    "credential_request" to "Prośba o kod lub hasło",
    "suspicious_link" to "Podejrzany link",
    "secrecy" to "Prośba o zachowanie tajemnicy",
    "authority_claim" to "Powoływanie się na autorytet",
    "emotional_pressure" to "Presja emocjonalna",
  )
  private val actions = mapOf(
    "family_impersonation" to "Zadzwoń na wcześniej znany numer i potwierdź tożsamość.",
    "credential_theft" to "Nie podawaj kodu ani hasła. Sprawdź sprawę w oficjalnej aplikacji.",
    "payment_fraud" to "Nie przelewaj pieniędzy przed potwierdzeniem odbiorcy innym kanałem.",
    "suspicious_link" to "Nie otwieraj linku. Wejdź samodzielnie do oficjalnej aplikacji.",
    "manipulation" to "Nie działaj pod presją. Sprawdź prośbę niezależnym kanałem.",
    "unknown" to "Potwierdź prośbę innym kanałem, zanim cokolwiek zrobisz.",
  )
  data class Copy(val title: String, val action: String, val evidence: String)
  /** Copy is built only from validated enums; never quote a message or contact. */
  fun copy(result: JSONObject): Copy {
    val category = result.getString("category")
    val values = result.getJSONArray("signals")
    val evidence = (0 until values.length()).mapNotNull { signalLabels[values.getString(it)] }.distinct().joinToString(" · ")
    val codes = (0 until values.length()).map { values.getString(it) }.toSet()
    val action = when {
      category == "family_impersonation" && "money_request" in codes -> "Nie wysyłaj pieniędzy. Najpierw zadzwoń na wcześniej znany numer."
      category == "family_impersonation" && "credential_request" in codes -> "Nie podawaj kodu ani hasła. Potwierdź tożsamość przez wcześniej znany numer."
      else -> actions[category] ?: actions.getValue("unknown")
    }
    return Copy(titles[category] ?: titles.getValue("unknown"), action, evidence)
  }

  fun ensureChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel(HIGH_RISK_CHANNEL, "Wysokie ryzyko — pilne ostrzeżenia", NotificationManager.IMPORTANCE_HIGH).apply {
      description = "Podejrzenie oszustwa: baner, dźwięk i wibracja. Możesz zmienić zachowanie w ustawieniach Androida."
      enableVibration(true)
      vibrationPattern = longArrayOf(0, 200, 120, 200)
    })
  }

  fun post(context: Context, result: JSONObject): Boolean {
    ensureChannel(context)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val id = result.getString("id")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("guardian://alert/$id")).setPackage(context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val copy = copy(result)
    val source = result.getString("sourceApp")
    val expanded = if (copy.evidence.isEmpty()) copy.action
      else "${copy.action}\n\nZauważone sygnały: ${copy.evidence}"
    val color = riskColor(result.getString("risk"))
    val notification = NotificationCompat.Builder(context, HIGH_RISK_CHANNEL)
      .setSmallIcon(R.drawable.guardian_notification_shield)
      .setLargeIcon(shield(context, color))
      .setColor(color)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setContentTitle(copy.title)
      .setSubText("$source · ocena AI")
      .setContentText(copy.action)
      .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
      .addAction(0, "Zobacz analizę", pending)
      .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
      .setOnlyAlertOnce(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending).build()
    return try { manager.notify(id.hashCode(), notification); true } catch (_: SecurityException) { false }
  }
}
