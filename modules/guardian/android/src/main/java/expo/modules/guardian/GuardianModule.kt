package expo.modules.guardian

import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.ComponentName
import android.provider.Settings
import androidx.core.app.ActivityCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.functions.Coroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuardianModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)
  private val guardianRuntime get() = GuardianRuntime.get(context)
  private val changed: (Boolean) -> Unit = { resultsChanged -> sendEvent("onChanged", mapOf("resultsChanged" to resultsChanged)) }
  override fun definition() = ModuleDefinition {
    Name("Guardian")
    Events("onChanged")
    OnStartObserving { guardianRuntime.observers.add(changed) }
    OnStopObserving { guardianRuntime.observers.remove(changed) }
    OnDestroy { guardianRuntime.observers.remove(changed) }
    AsyncFunction("getStatus") { guardianRuntime.status() }
    AsyncFunction("getResults") { guardianRuntime.store.serialized() }
    AsyncFunction("getNotifications") { guardianRuntime.notifications() }
    AsyncFunction("setMonitoring") Coroutine { enabled: Boolean -> guardianRuntime.setEnabled(enabled); guardianRuntime.status() }
    AsyncFunction("clearHistory") Coroutine { -> guardianRuntime.clearHistory() }
    AsyncFunction("markReviewed") { id: String -> guardianRuntime.store.review(id); guardianRuntime.notifyChanged(resultsChanged = true) }
    AsyncFunction("importModel") Coroutine { uri: String -> guardianRuntime.importModel(uri) }
    AsyncFunction("downloadModel") { guardianRuntime.downloadModel(); guardianRuntime.status() }
    AsyncFunction("checkMessage") Coroutine { message: String -> guardianRuntime.checkMessage(message) }
    AsyncFunction("runBenchmark") Coroutine { -> guardianRuntime.benchmark() }
    Function("cancelBenchmark") { guardianRuntime.cancelBenchmark() }
    AsyncFunction("openWarningChannelSettings") {
      GuardianWarnings.ensureChannel(context)
      context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, GuardianWarnings.HIGH_RISK_CHANNEL)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    AsyncFunction("openNotificationSettings") {
      val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, GuardianNotificationService::class.java).flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try { context.startActivity(detail) }
      catch (_: android.content.ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    }
    Function("requestWarningPermission") {
      if (Build.VERSION.SDK_INT >= 33) appContext.currentActivity?.let {
        ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7302)
      }
    }
  }
}
