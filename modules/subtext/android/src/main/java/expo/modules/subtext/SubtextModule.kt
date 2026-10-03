package expo.modules.subtext

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.functions.Coroutine
import org.json.JSONArray
import expo.modules.subtext.messenger.SubtextLoginActivity

class SubtextModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)
  private val subtextRuntime get() = SubtextRuntime.get(context)
  private val changed: () -> Unit = { sendEvent("onChanged", emptyMap<String, Any>()) }
  override fun definition() = ModuleDefinition {
    Name("Subtext")
    Events("onChanged")
    OnActivityEntersForeground {
      if (subtextRuntime.prefs.getBoolean("background", false)) runCatching { subtextRuntime.startConnections() }
    }
    OnStartObserving { subtextRuntime.observers.add(changed) }
    OnStopObserving { subtextRuntime.observers.remove(changed) }
    OnDestroy { subtextRuntime.observers.remove(changed) }
    AsyncFunction("status") { subtextRuntime.status() }
    AsyncFunction("loadDemo") { subtextRuntime.loadDemo() }
    AsyncFunction("conversations") {
      JSONArray(subtextRuntime.store.rooms().map { it.apply { put("messageCount", getJSONArray("messages").length()); remove("messages") } }).toString()
    }
    AsyncFunction("refresh") Coroutine { -> subtextRuntime.startConnections(); subtextRuntime.refresh() }
    AsyncFunction("conversation") Coroutine { id: String -> subtextRuntime.read(id) }
    AsyncFunction("analyze") Coroutine { id: String, draft: String -> subtextRuntime.analyze(id, draft) }
    AsyncFunction("setApiKey") { key: String -> subtextRuntime.secrets.set(key.trim()); subtextRuntime.changed() }
    AsyncFunction("setCloudEnabled") { enabled: Boolean -> subtextRuntime.cloud(enabled) }
    AsyncFunction("clearHistory") { subtextRuntime.clear() }
    AsyncFunction("disconnect") { network: String -> subtextRuntime.disconnect(network) }
    AsyncFunction("connectMessenger") {
      context.startActivity(Intent(context, SubtextLoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    AsyncFunction("pairWhatsApp") Coroutine { phone: String ->
      subtextRuntime.startConnections()
      subtextRuntime.whatsapp.startPairing(replaceExpiredSession = true).getOrThrow()
      subtextRuntime.whatsapp.requestPairCode(phone).getOrThrow()
    }
    AsyncFunction("openKeyboardSettings") { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Function("selectKeyboard") { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
  }
}
