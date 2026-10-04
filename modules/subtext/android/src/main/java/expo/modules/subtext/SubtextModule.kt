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
    AsyncFunction("setDemoStage") Coroutine { stage: Int -> subtextRuntime.demoStage(stage) }
    AsyncFunction("setConversationAI") { id: String, enabled: Boolean -> subtextRuntime.setConversationAI(id, enabled) }
    AsyncFunction("conversationWritingStyle") { id: String -> subtextRuntime.writingStyle(id) }
    AsyncFunction("conversationReminders") { id: String ->
      requireNotNull(subtextRuntime.store.room(id)) { "Nie znaleziono rozmowy." }
      ConversationReminders.overview(subtextRuntime.store.memory(id)).toString()
    }
    AsyncFunction("refreshConversationReminders") Coroutine { id: String -> subtextRuntime.refreshReminders(id) }
    AsyncFunction("editConversationReminder") { id: String, reminderId: String, patch: String ->
      requireNotNull(subtextRuntime.store.room(id)) { "Nie znaleziono rozmowy." }
      subtextRuntime.store.editReminder(id, reminderId, org.json.JSONObject(patch))
      subtextRuntime.changed()
    }
    AsyncFunction("conversationMemory") { id: String ->
      requireNotNull(subtextRuntime.store.room(id)) { "Nie znaleziono rozmowy." }
      val memory = subtextRuntime.store.memory(id)
      org.json.JSONObject().put("conversationId", id).put("storedMemory", memory)
        .put("aiMemory", PersonMemory.input(memory)).toString()
    }
    AsyncFunction("previewConversationWritingStyle") Coroutine { id: String -> subtextRuntime.previewWritingStyle(id) }
    AsyncFunction("setWritingTone") { id: String?, tone: String -> subtextRuntime.setWritingTone(id, tone) }
    AsyncFunction("writingStyle") { subtextRuntime.writingStyle() }
    AsyncFunction("previewWritingStyle") Coroutine { -> subtextRuntime.previewWritingStyle() }
    AsyncFunction("conversations") {
      JSONArray(subtextRuntime.store.summaries()).toString()
    }
    AsyncFunction("refresh") Coroutine { -> subtextRuntime.startConnections(); subtextRuntime.refresh() }
    AsyncFunction("conversationImage") Coroutine { id: String, messageId: String ->
      android.net.Uri.fromFile(subtextRuntime.conversationImage(id, messageId)).toString()
    }
    AsyncFunction("conversation") { id: String -> subtextRuntime.cachedConversation(id) }
    AsyncFunction("syncConversation") Coroutine { id: String -> subtextRuntime.read(id) }
    AsyncFunction("analyze") Coroutine { id: String, draft: String -> subtextRuntime.analyze(id, draft) }
    AsyncFunction("setCloudEnabled") { enabled: Boolean -> subtextRuntime.cloud(enabled) }
    AsyncFunction("clearHistory") { subtextRuntime.clear() }
    AsyncFunction("disconnect") { network: String -> subtextRuntime.disconnect(network) }
    AsyncFunction("connectMessenger") {
      context.startActivity(Intent(context, SubtextLoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    AsyncFunction("connectInstagram") {
      context.startActivity(Intent(context, SubtextLoginActivity::class.java)
        .putExtra("network", "instagram").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
