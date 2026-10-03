package expo.modules.subtext

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import expo.modules.subtext.device.ConversationKind
import expo.modules.subtext.messenger.MessengerRepository
import expo.modules.subtext.whatsapp.WhatsAppRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

@OptIn(FlowPreview::class)
class SubtextRuntime private constructor(private val context: Context) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  val messenger = MessengerRepository(context)
  val whatsapp = WhatsAppRepository(context)
  val store = SubtextStore(context)
  private val gateway = SupabaseGateway(context)
  val prefs = context.getSharedPreferences("subtext", Context.MODE_PRIVATE)
  val observers = CopyOnWriteArraySet<() -> Unit>()
  private val analysisMutex = Mutex()
  private val generation = AtomicLong()
  @Volatile var analyzing: String? = null
    private set
  init {
    // Retire the old device-side DeepSeek credential and any development import.
    SecureValue(context).set("")
    File(context.filesDir, "subtext-key.import").delete()
    scope.launch { messenger.state.collect { changed() } }
    scope.launch { whatsapp.state.collect { changed() } }
    scope.launch { messenger.conversations.debounce(500).collect { rooms ->
      rooms.filter { it.kind == ConversationKind.GROUP }.forEach { store.merge("messenger", it.id, it.name, it.kind.name) }
      rooms.filter { it.kind == ConversationKind.PRIVATE }.take(150).forEach { store.merge("messenger", it.id, it.name, it.kind.name, timestamp = it.timestamp) }; store.flush(); changed()
    } }
    scope.launch { whatsapp.conversations.debounce(500).collect { rooms ->
      rooms.filter { it.kind == ConversationKind.GROUP }.forEach { store.merge("whatsapp", it.id, it.name, it.kind.name) }
      rooms.filter { it.kind == ConversationKind.PRIVATE }.take(150).forEach { store.merge("whatsapp", it.id, it.name, it.kind.name, timestamp = it.timestamp) }; store.flush(); changed()
    } }
    scope.launch { messenger.messages.debounce(500).collect { chats ->
      chats.forEach { (id, messages) ->
        val room = messenger.conversations.value.find { it.id == id }
        if (room?.kind != ConversationKind.PRIVATE) return@forEach
        store.merge("messenger", id, room.name, room.kind.name, messages.map {
          message(it.id, it.senderName, it.text, it.timestamp, it.isMe)
        })
      }; store.flush(); changed()
    } }
    scope.launch { whatsapp.messages.debounce(500).collect { chats ->
      chats.forEach { (id, messages) ->
        val room = whatsapp.conversations.value.find { it.id == id }
        if (room?.kind != ConversationKind.PRIVATE) return@forEach
        store.merge("whatsapp", id, room.name, room.kind.name, messages.map {
          message(it.id, it.senderName, it.text, it.timestamp, it.isMe)
        })
      }; store.flush(); changed()
    } }
  }
  fun changed() { observers.forEach { runCatching { it() } } }
  fun restore() { messenger.restoreIfPossible(); whatsapp.restoreIfPossible() }
  fun startConnections() {
    prefs.edit().putBoolean("background", true).apply()
    ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
  }
  fun status(): String = JSONObject().put("available", true).put("model", DeepSeek.MODEL)
    .put("hasApiKey", true).put("cloudEnabled", prefs.getBoolean("cloud", false))
    .put("backgroundEnabled", prefs.getBoolean("background", false)).put("analyzing", analyzing ?: JSONObject.NULL)
    .put("messenger", JSONObject().put("phase", messenger.state.value.phase.name).put("detail", messenger.state.value.detail))
    .put("whatsapp", JSONObject().put("phase", whatsapp.state.value.phase.name).put("detail", whatsapp.state.value.detail)
      .put("pairingCode", whatsapp.state.value.pairingCode ?: JSONObject.NULL)).toString()
  suspend fun refresh() { restore(); messenger.refreshConversations(); whatsapp.refreshConversations() }
  suspend fun read(id: String): String {
    val room = requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." }
    check(room.optString("kind") == "PRIVATE") { "Obsługiwane są tylko rozmowy prywatne." }
    if (room.optBoolean("demo")) return room.toString()
    val remote = room.getString("remoteId"); val network = room.getString("network")
    val messages = if (network == "messenger") messenger.readMessages(remote, 100).map { message(it.id, it.senderName, it.text, it.timestamp, it.isMe) }
      else whatsapp.readMessages(remote, 100).map { message(it.id, it.senderName, it.text, it.timestamp, it.isMe) }
    store.merge(network, remote, room.getString("name"), room.getString("kind"), messages)
    store.flush()
    val result = requireNotNull(store.room(id))
    if (result.getJSONArray("messages").length() == 0) {
      result.put("historyNotice", if (network == "messenger" && messenger.isEncrypted(remote))
        "Messenger nie udostępnił historii tego szyfrowanego czatu. Cue może zapisywać nowe wiadomości odebrane po połączeniu konta."
      else "Komunikator nie udostępnił jeszcze wiadomości. Spróbuj odświeżyć po synchronizacji.")
    }
    return result.toString()
  }
  suspend fun analyze(id: String, draft: String): String = analysisMutex.withLock {
    check(prefs.getBoolean("cloud", false)) { "Włącz analizę DeepSeek w ustawieniach. Wybrana rozmowa zostanie wysłana do API." }
    val token = generation.get()
    analyzing = id; changed()
    try {
      val fetched = JSONObject(read(id))
      val room = requireNotNull(store.room(id)) { "Rozmowa została usunięta." }
      val all = room.getJSONArray("messages")
      val recent = JSONArray((maxOf(0, all.length() - 80) until all.length()).map { all.getJSONObject(it) })
      check(recent.length() > 0) { fetched.optString("historyNotice", "Wiadomości nie zostały jeszcze zsynchronizowane.") }
<<<<<<< HEAD
      val profile = DeepSeek.analyze(gateway, recent, draft)
=======
      val profile = DeepSeek.analyze(key, recent, draft, all, DeepSeek.generalWritingHistory(store.rooms()))
>>>>>>> 19c9d6f79feed493ef64882bb902c4e9e6544f65
      check(token == generation.get() && prefs.getBoolean("cloud", false)) { "Analiza anulowana po zmianie ustawień." }
      store.profile(id, profile)
      profile.toString()
    } finally { analyzing = null; changed() }
  }
  fun cloud(enabled: Boolean) { generation.incrementAndGet(); prefs.edit().putBoolean("cloud", enabled).apply(); changed() }
  fun loadDemo(): String {
    val now = System.currentTimeMillis()
    val sample = listOf(
      message("demo-1", "Marta", "W piątek przygotuję pierwszą wersję prezentacji. Ty możesz sprawdzić dane?", now - 172800000, false),
      message("demo-2", "Ty", "Jasne, sprawdzę dane do poniedziałku rano.", now - 172790000, true),
      message("demo-3", "Marta", "Super. Wolę krótkie podsumowanie w punktach, wystarczy tutaj.", now - 172780000, false),
      message("demo-4", "Ty", "Dane sprawdzone. Dwie liczby w tabeli wymagają poprawki, zaraz podeślę szczegóły.", now - 3600000, true),
      message("demo-5", "Marta", "Dzięki! Mam dziś sporo spotkań. Możesz też przygotować całą prezentację na jutro?", now - 60000, false)
    )
    store.merge("messenger", "subtext-demo", "Marta · przykład", "PRIVATE", sample)
    store.markDemo("messenger:subtext-demo"); changed()
    return "messenger:subtext-demo"
  }
  fun clear() { generation.incrementAndGet(); store.clear(); changed() }
  fun disconnect(network: String) {
    generation.incrementAndGet()
    if (network == "messenger") messenger.logout() else if (network == "whatsapp") whatsapp.logout() else error("Nieznany komunikator.")
    store.clear(network)
    if (!messenger.hasSession() && !whatsapp.hasSession()) {
      prefs.edit().putBoolean("background", false).apply()
      context.stopService(Intent(context, ConnectionService::class.java))
    }
    changed()
  }
  companion object {
    @Volatile private var instance: SubtextRuntime? = null
    fun get(context: Context): SubtextRuntime = instance ?: synchronized(this) {
      instance ?: SubtextRuntime(context.applicationContext).also { instance = it }
    }
    private fun message(id: String, sender: String, text: String, timestamp: Long, me: Boolean) = JSONObject()
      .put("id", id).put("sender", sender.take(160)).put("text", text.take(4000)).put("timestamp", timestamp).put("isMe", me)
  }
}
