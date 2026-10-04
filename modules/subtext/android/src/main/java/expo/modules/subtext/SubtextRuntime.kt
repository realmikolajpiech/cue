package expo.modules.subtext

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import expo.modules.subtext.device.ConversationKind
import expo.modules.subtext.messenger.MetaBridgeService
import expo.modules.subtext.messenger.MessengerRepository
import expo.modules.subtext.whatsapp.WhatsAppRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@OptIn(FlowPreview::class)
class SubtextRuntime private constructor(private val context: Context) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  val messenger = MessengerRepository(context)
  val instagram = MessengerRepository(context, MetaBridgeService.INSTAGRAM)
  val whatsapp = WhatsAppRepository(context)
  val store = SubtextStore(context)
  private val photos = ProfilePhotoCache(context)
  private val gateway = SupabaseGateway(context)
  val prefs = context.getSharedPreferences("subtext", Context.MODE_PRIVATE)
  val observers = CopyOnWriteArraySet<() -> Unit>()
  private val analysisMutex = Mutex()
  private val imageLocks = ConcurrentHashMap<String, Mutex>()
  private val images = ConversationImages(context)
  private val generation = AtomicLong()
  private val memoryRequests = Channel<Unit>(Channel.CONFLATED)
  private val photoLifecycleLock = Any()
  @Volatile var analyzing: String? = null
    private set
  init {
    // Retire the old device-side DeepSeek credential and any development import.
    SecureValue(context).set("")
    File(context.filesDir, "subtext-key.import").delete()
    scope.launch { messenger.state.collect { changed() } }
    scope.launch { instagram.state.collect { changed() } }
    scope.launch { whatsapp.state.collect { changed() } }
    listOf("messenger" to messenger, "instagram" to instagram).forEach { (network, repository) ->
      scope.launch { repository.conversations.debounce(500).collect { rooms ->
        rooms.filter { it.kind == ConversationKind.GROUP }.forEach { store.merge(network, it.id, it.name, it.kind.name) }
        rooms.filter { it.kind == ConversationKind.PRIVATE }.take(150).forEach {
          store.merge(network, it.id, it.name, it.kind.name, timestamp = it.timestamp)
        }; store.flush(); changed()
      } }
      scope.launch { repository.messages.debounce(500).collect { chats ->
        chats.forEach { (id, messages) ->
          val room = repository.conversations.value.find { it.id == id }
          if (room?.kind != ConversationKind.PRIVATE) return@forEach
          store.merge(network, id, room.name, room.kind.name, messages.map {
            message(it.id, it.senderName, it.text, it.timestamp, it.isMe, it.mediaId)
          })
        }; store.flush(); changed(); memoryRequests.trySend(Unit)
      } }
    }
    scope.launch { whatsapp.conversations.debounce(500).collect { rooms ->
      rooms.filter { it.kind == ConversationKind.GROUP }.forEach { store.merge("whatsapp", it.id, it.name, it.kind.name) }
      rooms.filter { it.kind == ConversationKind.PRIVATE }.take(150).forEach { store.merge("whatsapp", it.id, it.name, it.kind.name, timestamp = it.timestamp) }; store.flush(); changed()
    } }
    scope.launch { whatsapp.messages.debounce(500).collect { chats ->
      chats.forEach { (id, messages) ->
        val room = whatsapp.conversations.value.find { it.id == id }
        if (room?.kind != ConversationKind.PRIVATE) return@forEach
        store.merge("whatsapp", id, room.name, room.kind.name, messages.map {
          message(it.id, it.senderName, it.text, it.timestamp, it.isMe, it.mediaId)
        })
      }; store.flush(); changed(); memoryRequests.trySend(Unit)
    } }
    scope.launch {
      memoryRequests.receiveAsFlow().debounce(MemoryAutoUpdate.QUIET_MS).collect {
        if (prefs.getBoolean("cloud", false)) updatePendingMemory()
      }
    }
    memoryRequests.trySend(Unit)
    scope.launch {
      while (isActive) {
        val token = generation.get()
        val rooms = store.summaries().filterNot { it.optBoolean("demo") }
        // Three small thumbnails at a time; photo failures never block message sync.
        rooms.chunked(3).forEach { batch ->
          coroutineScope {
            batch.map { room -> async {
              val network = room.getString("network")
              val connected = when (network) {
                "messenger" -> messenger.state.value.phase.name == "CONNECTED"
                "whatsapp" -> whatsapp.state.value.phase.name == "CONNECTED"
                else -> false // The Instagram bridge currently supplies no profile-photo URL.
              }
              if (!connected || token != generation.get()) return@async
              val id = room.getString("id")
              val remote = room.getString("remoteId")
              runCatching {
                val result = photos.refresh(id) {
                  if (network == "messenger") messenger.profilePictureUrl(remote, messenger.conversations.value.find { it.id == remote }?.contactId)
                  else whatsapp.profilePictureUrl(remote)
                }
                synchronized(photoLifecycleLock) {
                  if (result?.isSuccess == true && token == generation.get()) {
                    store.avatar(id, result.getOrNull())
                    changed()
                  }
                }
              }
            } }.awaitAll()
          }
        }
        photos.prune(store.summaries().map { it.getString("id") }.toSet())
        delay(10_000)
      }
    }
    scope.launch {
      while (isActive) {
        delay(30_000)
        if (prefs.getBoolean("cloud", false)) updatePendingMemory()
      }
    }
  }
  fun changed() { observers.forEach { runCatching { it() } } }
  fun restore() { messenger.restoreIfPossible(); whatsapp.restoreIfPossible(); instagram.restoreIfPossible() }
  fun startConnections() {
    prefs.edit().putBoolean("background", true).apply()
    ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
  }
  fun status(): String = JSONObject().put("available", true).put("model", DeepSeek.MODEL)
    .put("hasApiKey", true).put("cloudEnabled", prefs.getBoolean("cloud", false))
    .put("backgroundEnabled", prefs.getBoolean("background", false)).put("analyzing", analyzing ?: JSONObject.NULL)
    .put("instagram", JSONObject().put("phase", instagram.state.value.phase.name).put("detail", instagram.state.value.detail))
    .put("messenger", JSONObject().put("phase", messenger.state.value.phase.name).put("detail", messenger.state.value.detail))
    .put("whatsapp", JSONObject().put("phase", whatsapp.state.value.phase.name).put("detail", whatsapp.state.value.detail)
      .put("pairingCode", whatsapp.state.value.pairingCode ?: JSONObject.NULL)).toString()
  suspend fun refresh() { restore(); messenger.refreshConversations(); whatsapp.refreshConversations(); instagram.refreshConversations() }
  fun cachedConversation(id: String): String {
    val room = requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." }
    check(room.optString("kind") == "PRIVATE") { "Obsługiwane są tylko rozmowy prywatne." }
    if (room.getJSONArray("messages").length() == 0 && !room.optBoolean("demo")) {
      room.put("historyNotice", if (room.optString("network") == "messenger" && messenger.isEncrypted(room.getString("remoteId")))
        "Messenger nie udostępnił historii tego szyfrowanego czatu. Cue może zapisywać nowe wiadomości odebrane po połączeniu konta."
      else "Nie ma jeszcze zapisanych wiadomości. Zsynchronizuj rozmowę, aby Cue mógł przygotować podsumowanie i odpowiedzi.")
    }
    return room.toString()
  }
  suspend fun read(id: String): String = withTimeout(20000) {
    val room = requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." }
    check(room.optString("kind") == "PRIVATE") { "Obsługiwane są tylko rozmowy prywatne." }
    if (room.optBoolean("demo")) return@withTimeout room.toString()
    val remote = room.getString("remoteId"); val network = room.getString("network")
    val messages = when (network) {
      "messenger", "instagram" -> (if (network == "messenger") messenger else instagram).readMessages(remote, 100)
        .map { message(it.id, it.senderName, it.text, it.timestamp, it.isMe, it.mediaId) }
      "whatsapp" -> whatsapp.readMessages(remote, 100).map { message(it.id, it.senderName, it.text, it.timestamp, it.isMe, it.mediaId) }
      else -> error("Nieznany komunikator.")
    }
    store.merge(network, remote, room.getString("name"), room.getString("kind"), messages)
    store.flush()
    memoryRequests.trySend(Unit)
    val result = requireNotNull(store.room(id))
    if (result.getJSONArray("messages").length() == 0) {
      result.put("historyNotice", if (network == "messenger" && messenger.isEncrypted(remote))
        "Messenger nie udostępnił historii tego szyfrowanego czatu. Cue może zapisywać nowe wiadomości odebrane po połączeniu konta."
      else "Komunikator nie udostępnił jeszcze wiadomości. Spróbuj odświeżyć po synchronizacji.")
    }
    result.toString()
  }
  suspend fun conversationImage(id: String, messageId: String): File = withTimeout(45_000) {
    imageLocks.getOrPut("$id:$messageId") { Mutex() }.withLock {
    android.util.Log.d("CueImage", "Photo request started")
    val token = generation.get()
    val room = requireNotNull(store.room(id)) { "Rozmowa nie istnieje." }
    check(room.optString("kind") == "PRIVATE") { "Obsługiwane są tylko rozmowy prywatne." }
    val messages = room.getJSONArray("messages")
    val message = (0 until messages.length()).map { messages.getJSONObject(it) }.firstOrNull { it.optString("id") == messageId }
      ?: error("Wiadomość nie istnieje.")
    check(message.optString("text").startsWith(MessageMedia.PHOTO)) { "Ta wiadomość nie zawiera zdjęcia." }
    val network = room.getString("network")
    images.cached(network, id, messageId)?.let { return@withLock it }
    val mediaId = message.optString("mediaId").ifBlank { messageId }
    suspend fun download(): ByteArray {
      android.util.Log.d("CueImage", "Downloading from $network")
      return when (network) {
        "messenger" -> messenger.downloadImage(mediaId)
        "whatsapp" -> whatsapp.downloadImage(mediaId)
        "instagram" -> instagram.downloadImage(mediaId)
        else -> error("Nieznany komunikator.")
      }
    }
    val raw = try { download() }
      catch (error: CancellationException) { throw error }
      catch (error: Exception) {
        // Plain Messenger CDN handles live only in the bridge session. Restore them
        // from history after an app restart before declaring the photo unavailable.
        if (network != "messenger" || error.message?.contains("image unavailable") != true) throw error
        read(id)
        // History attachments arrive through the socket after read() returns
        // when the conversation already has cached messages.
        var restored: ByteArray? = null
        var lastFailure: Exception = error
        for (attempt in 0 until 10) {
          delay(500)
          check(token == generation.get()) { "Pobieranie zdjęcia anulowane." }
          try {
            restored = download()
            break
          } catch (cancelled: CancellationException) { throw cancelled }
          catch (failure: Exception) {
            if (failure.message?.contains("image unavailable") != true) throw failure
            lastFailure = failure
          }
        }
        restored ?: throw lastFailure
      }
    check(token == generation.get() && store.room(id) != null) { "Pobieranie zdjęcia anulowane." }
    android.util.Log.d("CueImage", "Photo bytes received")
    images.save(network, id, messageId, raw).also { android.util.Log.d("CueImage", "Photo cached") }
    }
  }
  private suspend fun analysisImages(id: String, messages: JSONArray): JSONArray {
    val candidates = (maxOf(0, messages.length() - 12) until messages.length()).map { messages.getJSONObject(it) }
      .filter { it.optString("text").startsWith(MessageMedia.PHOTO) }.takeLast(3)
    val output = JSONArray()
    for (message in candidates) {
      try {
        val file = conversationImage(id, message.getString("id"))
        output.put(JSONObject().put("messageId", message.getString("id"))
          .put("dataUrl", "data:image/jpeg;base64," + android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)))
      } catch (error: CancellationException) { throw error }
      catch (_: Exception) { /* The model explicitly sees which photo bodies are unavailable. */ }
    }
    return output
  }
  suspend fun analyze(id: String, draft: String, tone: String? = null, intensity: Int? = null,
    rejected: List<String> = emptyList()): String = analysisMutex.withLock {
    checkAIAllowed(id)
    check(prefs.getBoolean("cloud", false)) { "Włącz analizę DeepSeek w ustawieniach. Wybrana rozmowa zostanie wysłana do API." }
    val token = generation.get()
    analyzing = id; changed()
    try {
      // Existing local messages are already enough to help; avoid another blocking history fetch.
      val cached = JSONObject(cachedConversation(id))
      val fetched = if (cached.getJSONArray("messages").length() > 0 || cached.optBoolean("demo")) cached else JSONObject(read(id))
      val room = requireNotNull(store.room(id)) { "Rozmowa została usunięta." }
      val all = room.getJSONArray("messages")
      val recent = JSONArray((maxOf(0, all.length() - 80) until all.length()).map { all.getJSONObject(it) })
      check(recent.length() > 0) { fetched.optString("historyNotice", "Wiadomości nie zostały jeszcze zsynchronizowane.") }
      val memory = store.memory(id)
      val chosenTone = tone ?: selectedTone(id)
      val intent = ConversationGoal.intent(draft, chosenTone, conversationGoal(id), intensity ?: selectedIntensity(id, chosenTone), rejected, CueLanguage.promptNote(context))
      val profile = DeepSeek.analyze(gateway, recent, intent, memory, images = analysisImages(id, recent))
      check(token == generation.get() && prefs.getBoolean("cloud", false)) { "Analiza anulowana po zmianie ustawień." }
      profile.put("replyDraft", draft)
      saveGoalIdeas(id, profile)
      store.profile(id, profile)
      store.updateContext(id, profile.getJSONArray("memoryUpdates"), recent, memory.optLong("revision"),
        profile.getJSONArray("reminderUpdates"), memory.optJSONArray("reminders") ?: JSONArray())
      profile.toString()
    } finally { analyzing = null; changed() }
  }
  fun cloud(enabled: Boolean) {
    generation.incrementAndGet(); prefs.edit().putBoolean("cloud", enabled).apply(); changed()
    if (enabled) memoryRequests.trySend(Unit)
  }
  suspend fun refreshReminders(id: String): String = analysisMutex.withLock {
    check(prefs.getBoolean("cloud", false)) { "Włącz analizę AI w ustawieniach, aby uzupełnić listę." }
    val token = generation.get()
    val room = JSONObject(cachedConversation(id))
    checkAIAllowed(id)
    val history = room.getJSONArray("messages")
    val recent = JSONArray((maxOf(0, history.length() - 80) until history.length()).map { history.getJSONObject(it) })
    check(recent.length() > 0) { "Zsynchronizuj wiadomości, aby uzupełnić listę." }
    val memory = store.memory(id)
    analyzing = id; changed()
    try {
      val profile = DeepSeek.analyze(gateway, recent, CueLanguage.promptNote(context).trim(), memory, memoryOnly = true)
      check(token == generation.get() && prefs.getBoolean("cloud", false)) { "Aktualizacja anulowana po zmianie ustawień." }
      store.updateContext(id, profile.getJSONArray("memoryUpdates"), recent, memory.optLong("revision"),
        profile.getJSONArray("reminderUpdates"), memory.optJSONArray("reminders") ?: JSONArray())
      ConversationReminders.overview(store.memory(id)).toString()
    } finally { analyzing = null; changed() }
  }
  private suspend fun updatePendingMemory() {
    // Server quota still applies. A daily-limit error pauses retries until the next UTC day.
    val rooms = store.rooms().filterNot { it.optBoolean("demo") }
    for (candidate in rooms) analysisMutex.withLock {
      val now = System.currentTimeMillis()
      if (!prefs.getBoolean("cloud", false) || now < prefs.getLong("memoryQuotaRetryAt", 0)) return@withLock
      val token = generation.get()
      val id = candidate.getString("id")
      val room = store.room(id) ?: return@withLock
      if (room.optBoolean("aiExcluded")) return@withLock
      val memory = store.memory(id)
      if (!MemoryAutoUpdate.eligible(memory, now)) return@withLock
      val all = room.getJSONArray("messages")
      val recent = JSONArray((maxOf(0, all.length() - 80) until all.length()).map { all.getJSONObject(it) })
      if (recent.length() == 0) return@withLock
      analyzing = id; changed()
      try {
        val profile = DeepSeek.analyze(gateway, recent, ConversationGoal.IDEAS + CueLanguage.promptNote(context), memory, memoryOnly = true)
        if (token == generation.get() && prefs.getBoolean("cloud", false)) {
          saveGoalIdeas(id, profile)
          store.updateContext(id, profile.getJSONArray("memoryUpdates"), recent, memory.optLong("revision"),
            profile.getJSONArray("reminderUpdates"), memory.optJSONArray("reminders") ?: JSONArray())
        }
      } catch (error: CancellationException) { throw error }
      catch (error: Exception) {
        if (token == generation.get()) store.contextFailed(id, error.message ?: "Nie udało się uzupełnić kontekstu. Spróbujemy ponownie.")
        if (token == generation.get() && error.message?.contains("limit analiz") == true)
          prefs.edit().putLong("memoryQuotaRetryAt", (now / 86_400_000 + 1) * 86_400_000).apply()
      } finally { analyzing = null; changed() }
    }
  }
  private fun styleRooms(id: String?): List<JSONObject> = if (id == null) store.rooms().filterNot { store.memory(it.getString("id")).optBoolean("aiExcluded") }
    else listOf(requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." })
  // First-time ideas for a conversation that has had no new message since goal ideas existed.
  suspend fun prefetchGoalIdeas(id: String) = analysisMutex.withLock {
    if (goalIdeas(id).isNotEmpty() || !prefs.getBoolean("cloud", false)) return@withLock
    if (System.currentTimeMillis() < prefs.getLong("memoryQuotaRetryAt", 0)) return@withLock
    val token = generation.get()
    val room = store.room(id) ?: return@withLock
    val all = room.getJSONArray("messages")
    val recent = JSONArray((maxOf(0, all.length() - 80) until all.length()).map { all.getJSONObject(it) })
    if (recent.length() == 0) return@withLock
    val memory = store.memory(id)
    analyzing = id; changed()
    try {
      val profile = DeepSeek.analyze(gateway, recent, ConversationGoal.IDEAS + CueLanguage.promptNote(context), memory, memoryOnly = true)
      if (token != generation.get() || !prefs.getBoolean("cloud", false)) return@withLock
      saveGoalIdeas(id, profile)
      if (!room.optBoolean("demo")) store.updateContext(id, profile.getJSONArray("memoryUpdates"), recent, memory.optLong("revision"),
        profile.getJSONArray("reminderUpdates"), memory.optJSONArray("reminders") ?: JSONArray())
    } finally { analyzing = null; changed() }
  }
  fun goalIdeas(id: String): List<String> = runCatching {
    JSONArray(prefs.getString("goal-ideas:$id", "[]")).let { array -> (0 until array.length()).map(array::getString) }
  }.getOrDefault(emptyList())
  private fun saveGoalIdeas(id: String, profile: JSONObject) {
    val ideas = profile.optJSONArray("goalIdeas") ?: return
    if (ideas.length() > 0) prefs.edit().putString("goal-ideas:$id", ideas.toString()).apply()
  }
  fun conversationGoal(id: String): String = prefs.getString("conversation-goal:$id", "") ?: ""
  fun setConversationGoal(id: String, goal: String) {
    requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." }
    require(goal.length <= 1000) { "Cel może mieć maksymalnie 1000 znaków." }
    val editor = prefs.edit()
    if (goal.isBlank()) editor.remove("conversation-goal:$id") else editor.putString("conversation-goal:$id", goal.trim())
    editor.apply(); changed()
  }
  private fun styleToneKey(id: String?) = "writing-style-tone:" + (id ?: "general")
  fun selectedTone(id: String?) = prefs.getString(styleToneKey(id),
    prefs.getString(styleToneKey(null), "natural")) ?: "natural"
  private fun styleIntensityKey(id: String?, tone: String) = "writing-style-intensity:" + (id ?: "general") + ":" + tone
  fun selectedIntensity(id: String?, tone: String) = WritingTone.clampIntensity(tone, prefs.getInt(styleIntensityKey(id, tone),
    prefs.getInt(styleIntensityKey(null, tone), WritingTone.DEFAULT_INTENSITY)))
  fun setWritingIntensity(id: String?, tone: String, intensity: Int) {
    WritingTone.requireValid(tone)
    if (id != null) requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." }
    prefs.edit().putInt(styleIntensityKey(id, tone), WritingTone.clampIntensity(tone, intensity)).apply()
    changed()
  }
  private fun styleCacheKey(id: String?, tone: String) = "writing-style-preview:v4:" + CueLanguage.get(context) + ":" + (id ?: "general") + ":" + tone
  fun setWritingTone(id: String?, tone: String): String {
    WritingTone.requireValid(tone)
    if (id != null) requireNotNull(store.room(id)) { "Nie znaleziono rozmowy." }
    prefs.edit().putString(styleToneKey(id), tone).apply()
    changed()
    return writingStyle(id)
  }
  private fun styleSamples(id: String?): JSONArray = if (id == null) DeepSeek.generalWritingHistory(styleRooms(null))
    else PersonMemory.writingSamples(store.memory(id))
  fun writingStyle(id: String? = null): String {
    val result = if (id == null) writingStyleOverview(styleRooms(id)) else PersonMemory.overview(store.memory(id))
    val samples = styleSamples(id)
    val tone = selectedTone(id)
    result.put("selectedTone", tone)
    val saved = runCatching { JSONObject(prefs.getString(styleCacheKey(id, tone), "") ?: "") }.getOrNull()
    if (saved?.optInt("sampleHash") == samples.toString().hashCode()) {
      result.put("previewExamples", saved.getJSONArray("examples"))
    }
    return result.toString()
  }
  suspend fun previewWritingStyle(id: String? = null): String = analysisMutex.withLock {
    check(prefs.getBoolean("cloud", false)) { "Włącz analizę AI w ustawieniach, aby utworzyć podgląd." }
    if (id != null) checkAIAllowed(id)
    val samples = styleSamples(id)
    check(samples.length() >= 5) { "Potrzebujemy przynajmniej 5 Twoich wiadomości. Zsynchronizuj rozmowy." }
    val token = generation.get()
    val tone = selectedTone(id)
    val scenarios = CueLanguage.previewScenarios(context)
    val examples = JSONArray()
    analyzing = "writing-style"; changed()
    try {
      for ((index, scenario) in scenarios.withIndex()) {
        check(token == generation.get() && prefs.getBoolean("cloud", false)) { "Tworzenie podglądu zostało anulowane." }
        val messages = JSONArray()
        for (i in maxOf(0, samples.length() - 79) until samples.length()) {
          messages.put(message("style-$i", "Ty · próbka stylu", samples.getJSONObject(i).getString("text"), i.toLong(), true))
        }
        messages.put(message("preview-$index", "Rozmówca", scenario.first, 1000L, false))
        val intent = "Podgląd mojego ogólnego stylu pisania. Wcześniejsze wiadomości isMe=true to tylko próbki formy wypowiedzi z rozmów, a nie kontekst ani fakty. " +
          "To ćwiczenie stylu: zwróć wyłącznie propozycje wiadomości action=reply, bez no_reply. Naśladuj ich długość, skróty, wielkość liter, interpunkcję i emoji. Jeśli próbki są pisane małymi literami, zachowaj małe litery; nie poprawiaj ich na formalny język. Nie przenoś nazw, faktów ani tematów z próbek. " +
          WritingTone.instruction(tone) + " " + scenario.second + CueLanguage.promptNote(context)
        val profile = DeepSeek.analyze(gateway, messages, intent, JSONObject())
        examples.put(JSONObject().put("id", "preview-$index").put("incoming", scenario.first)
          .put("reply", profile.getJSONArray("suggestions").getJSONObject(0).getString("text"))
          .put("timestamp", System.currentTimeMillis()))
      }
      check(token == generation.get() && prefs.getBoolean("cloud", false)) { "Tworzenie podglądu zostało anulowane." }
      prefs.edit().putString(styleCacheKey(id, tone), JSONObject().put("sampleHash", samples.toString().hashCode()).put("examples", examples).toString()).apply()
      writingStyle(id)
    } finally { analyzing = null; changed() }
  }
  private fun checkAIAllowed(id: String) {
    check(!store.memory(id).optBoolean("aiExcluded")) { "AI jest wyłączone dla tej rozmowy. Zmień ustawienie w jej profilu." }
  }
  fun setConversationAI(id: String, enabled: Boolean) {
    generation.incrementAndGet()
    store.setAIExcluded(id, !enabled)
    if (enabled) memoryRequests.trySend(Unit)
    changed()
  }
  fun loadDemo(): String {
    if (store.room(CueDemo.ID)?.has("demoStage") != true) store.demoStage(0)
    changed(); return CueDemo.ID
  }
  suspend fun demoStage(stage: Int): String = analysisMutex.withLock {
    store.demoStage(stage).also { changed() }
  }
  private fun clearStylePreviews() {
    val editor = prefs.edit()
    prefs.all.keys.filter { it.startsWith("writing-style-preview") || it.startsWith("writing-style-tone:") || it.startsWith("conversation-goal:") }.forEach { editor.remove(it) }
    editor.apply()
  }
  fun clear() = synchronized(photoLifecycleLock) { generation.incrementAndGet(); clearStylePreviews(); images.clear(); photos.clear(); store.clear(); changed() }
  fun disconnect(network: String) = synchronized(photoLifecycleLock) {
    generation.incrementAndGet()
    clearStylePreviews()
    when (network) {
      "messenger" -> messenger.logout()
      "whatsapp" -> whatsapp.logout()
      "instagram" -> instagram.logout()
      else -> error("Nieznany komunikator.")
    }
    images.clear(network)
    photos.clear(network)
    store.clear(network)
    if (!messenger.hasSession() && !whatsapp.hasSession() && !instagram.hasSession()) {
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
    private fun message(id: String, sender: String, text: String, timestamp: Long, me: Boolean, mediaId: String? = null) = JSONObject()
      .put("id", id).put("sender", sender.take(160)).put("text", text.take(4000)).put("timestamp", timestamp).put("isMe", me).apply { if (!mediaId.isNullOrBlank()) put("mediaId", mediaId) }
  }
}
