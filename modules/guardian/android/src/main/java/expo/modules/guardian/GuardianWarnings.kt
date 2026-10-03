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

  fun post(context: Context, result: JSONObject): Boolean {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(NotificationChannel("guardian_risk", "Ostrzeżenia Guardian", NotificationManager.IMPORTANCE_HIGH))
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val id = result.getString("id")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("guardian://alert/$id")).setPackage(context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val copy = copy(result)
    val source = result.getString("sourceApp")
    val expanded = if (copy.evidence.isEmpty()) copy.action
      else "${copy.action}\n\nZauważone sygnały: ${copy.evidence}"
    val icon = context.applicationInfo.icon
    val notification = NotificationCompat.Builder(context, "guardian_risk")
      .setSmallIcon(icon).setContentTitle(copy.title)
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
