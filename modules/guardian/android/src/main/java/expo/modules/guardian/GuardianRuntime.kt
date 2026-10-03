package expo.modules.guardian

import android.content.Context
import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/** Process singleton: listener can run without a JS runtime. Text is retrieved only by the notification picker. */
class GuardianRuntime private constructor(val context: Context) {
  companion object {
    @Volatile private var instance: GuardianRuntime? = null
    fun get(context: Context): GuardianRuntime = instance ?: synchronized(this) {
      instance ?: GuardianRuntime(context.applicationContext).also { instance = it }
    }
  }
  private val modelLifecycle = Mutex()
  private val model by lazy { GemmaModel.load(context) }
  val inference = GuardianInference()
  val store = ResultStore(context)
  private val preferences = context.getSharedPreferences("guardian", Context.MODE_PRIVATE)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val buffer = ConversationBuffer(SystemClock::elapsedRealtime)
  private val notificationHistory = NotificationHistory(SystemClock::elapsedRealtime)
  private val generation = AtomicLong(0)
  private data class Work(val key: String, val source: String, val messages: List<PrivateMessage>, val epoch: Long, val received: Long)
  private val queue = Channel<Work>(16, BufferOverflow.DROP_OLDEST)
  val observers = CopyOnWriteArraySet<() -> Unit>()
  @Volatile var connected = false
  @Volatile var processing = false
  @Volatile var lastError: String? = null
  val modelFile get() = File(context.noBackupFilesDir, "guardian-model.litertlm")
  val enabled get() = preferences.getBoolean("monitoring", false)
  private val gate = Any()
  private val removals = linkedMapOf<String, Long>()
  private var currentJob: Job? = null
  @Volatile private var benchmarkJob: Job? = null
  @Volatile var benchmarkProgress = 0; private set
  private val alertTimes = mutableMapOf<String, Long>()
  private val sourceTimes = mutableMapOf<String, Long>()
  init {
    scope.launch { initializeModel(); notifyChanged() }
    scope.launch {
      while (isActive) {
        delay(30_000)
        buffer.prune()
        notificationHistory.prune()
        if (!permissionGranted() && connected) disconnect()
      }
    }
    scope.launch {
      for (work in queue) {
        if (!eligibleWork(work)) continue
        val fresh = work.messages.filter { SystemClock.elapsedRealtime() - it.receivedAt < 15 * 60 * 1000L }
        if (fresh.isEmpty()) continue
        processing = true; notifyChanged()
        val child = scope.launch {
          val result = try {
            Assessment.result(inference.analyze(fresh.map { it.text }), work.source)
          } catch (e: CancellationException) { throw e }
          catch (_: Exception) { lastError = "analysis_failed"; Assessment.uncertain(work.source) }
          synchronized(gate) {
            if (eligibleWork(work)) {
              store.add(result)
              warn(result, work.key)
            }
          }
        }
        currentJob = child
        child.join(); currentJob = null
        processing = false; notifyChanged()
      }
    }
  }
  private fun eligible(epoch: Long) = generation.get() == epoch && enabled && connected && permissionGranted() && inference.state == "ready"
  private fun eligibleWork(work: Work) = synchronized(gate) {
    eligible(work.epoch) && work.received > (removals[work.key] ?: -1L)
  }
  fun notifyChanged() { observers.forEach { runCatching { it() } } }
  fun permissionGranted(): Boolean {
    val expected = ComponentName(context, GuardianNotificationService::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
      ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
  }
  fun connect(onReady: () -> Unit = {}) {
    connected = true; notifyChanged()
    if (enabled) scope.launch {
      initializeModel(); notifyChanged()
      if (enabled && connected && permissionGranted() && inference.state == "ready") runCatching(onReady)
    }
  }
  fun disconnect() { connected = false; stopPending(); notifyChanged(); scope.launch { modelLifecycle.withLock { inference.close() }; notifyChanged() } }
  private fun stopPending() {
    synchronized(gate) { generation.incrementAndGet(); buffer.clear(); notificationHistory.clear(); removals.clear(); while (queue.tryReceive().isSuccess) {} }
    currentJob?.cancel(); benchmarkJob?.cancel(); inference.cancel()
  }
  private suspend fun initializeModel() = modelLifecycle.withLock { initializeVerifiedModel() }
  private suspend fun initializeVerifiedModel() {
    if (!modelFile.exists()) return
    if (inference.state == "ready") return
    if (!model.verify(modelFile)) {
      lastError = "model_checksum_mismatch"
      return
    }
    inference.initialize(modelFile)
    if (inference.state == "ready") {
      check(preferences.edit().putString("modelSha256", model.sha256).putString("modelId", model.id).commit())
      lastError = null
    }
  }
  suspend fun setEnabled(value: Boolean) = modelLifecycle.withLock {
    if (value) {
      initializeVerifiedModel()
      check(inference.state == "ready") { "model_not_ready" }
    }
    synchronized(gate) { check(preferences.edit().putBoolean("monitoring", value).commit()) }
    if (!value) { stopPending(); inference.close() }
    notifyChanged()
  }
  fun accept(input: NotificationNormalizer.Input) {
    synchronized(gate) {
      val epoch = generation.get()
      if (!eligible(epoch)) return
      notificationHistory.add(input.key, input.source, input.messages, System.currentTimeMillis())
      buffer.append(input.key, input.messages)?.let { queue.trySend(Work(input.key, input.source, it, epoch, SystemClock.elapsedRealtime())) }
    }
    notifyChanged()
  }
  fun notifications(): List<Map<String, Any>> = synchronized(gate) {
    if (!enabled || !permissionGranted()) { notificationHistory.clear(); return@synchronized emptyList() }
    notificationHistory.list().map { mapOf("id" to it.id, "sourceApp" to it.source, "text" to it.text, "createdAt" to it.createdAt) }
  }
  fun remove(key: String) {
    synchronized(gate) {
      buffer.remove(key)
      removals[key] = SystemClock.elapsedRealtime()
      while (removals.size > 64) removals.remove(removals.keys.first())
    }
  }
  suspend fun clearHistory() {
    stopPending()
    synchronized(gate) { store.clear(); alertTimes.clear(); sourceTimes.clear() }
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    notifyChanged()
  }
  suspend fun importModel(uri: String): String = withContext(Dispatchers.IO) {
    modelLifecycle.withLock {
      require(Uri.parse(uri).scheme in setOf("content", "file"))
      check(preferences.edit().putBoolean("monitoring", false).commit())
      stopPending(); inference.close(); notifyChanged()
      val temp = File(context.noBackupFilesDir, "guardian-model.part")
      try {
        check(context.noBackupFilesDir.usableSpace >= model.sizeBytes + 64L * 1024 * 1024) { "model_insufficient_space" }
        context.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
          requireNotNull(input) { "model_file_unreadable" }
          model.copyVerified(input, temp) { ensureActive() }
        }
        // The previous installed file survives invalid, incomplete and cancelled imports.
        check(temp.renameTo(modelFile)) { "model_install_failed" }
        check(preferences.edit().putString("modelSha256", model.sha256).putString("modelId", model.id).commit())
        inference.initialize(modelFile)
        check(inference.state == "ready") { "model_initialization_failed" }
        lastError = null; notifyChanged()
        model.sha256
      } catch (e: CancellationException) {
        temp.delete(); notifyChanged(); throw e
      } catch (e: Exception) {
        temp.delete()
        lastError = e.message?.takeIf { it in setOf("model_insufficient_space", "model_file_unreadable", "model_size_mismatch", "model_checksum_mismatch", "model_install_failed", "model_initialization_failed") } ?: "model_import_failed"
        notifyChanged()
        throw IllegalStateException(lastError)
      }
    }
  }
  /** User-entered text is transient; neither input nor result is persisted. */
  suspend fun checkMessage(message: String): String = withContext(Dispatchers.IO) {
    require(message.isNotBlank() && message.length <= 1500) { "manual_input_invalid" }
    modelLifecycle.withLock {
      check(benchmarkJob == null) { "benchmark_already_running" }
      initializeVerifiedModel()
      check(inference.state == "ready") { "model_not_ready" }
      try {
        Assessment.result(inference.analyze(listOf(message)), "Manual").toString()
      } catch (e: CancellationException) { throw e }
      catch (_: Exception) { throw IllegalStateException("manual_analysis_failed") }
    }
  }
  suspend fun benchmark(): String = withContext(Dispatchers.IO) {
    check(benchmarkJob == null) { "benchmark_already_running" }
    setEnabled(false)
    initializeModel()
    check(inference.state == "ready") { "model_not_ready" }
    benchmarkJob = coroutineContext[Job]
    benchmarkProgress = 0
    try { Benchmark.run(this@GuardianRuntime) { benchmarkProgress = it; notifyChanged() }.toString() }
    finally { benchmarkJob = null; notifyChanged() }
  }
  fun cancelBenchmark() { benchmarkJob?.cancel(); inference.cancel() }
  fun status(): Map<String, Any?> = mapOf(
    "benchmarkRunning" to (benchmarkJob != null), "benchmarkProgress" to benchmarkProgress,
    "available" to true, "notificationAccess" to permissionGranted(), "listenerConnected" to connected,
    "monitoringEnabled" to enabled, "modelState" to inference.state, "backend" to inference.backend,
    "processing" to processing, "error" to (lastError ?: inference.error),
    "active" to (enabled && connected && permissionGranted() && inference.state == "ready"),
    "modelInstalled" to (modelFile.isFile && preferences.getString("modelSha256", null) == model.sha256),
    "modelSha256" to preferences.getString("modelSha256", null), "initializationMs" to inference.initializationMs,
    "promptVersion" to inference.promptVersion, "runtimeVersion" to "0.15.0",
    "notificationPermission" to NotificationManagerCompat.from(context).areNotificationsEnabled(),
  )
  private fun warn(result: JSONObject, key: String) {
    if (result.getString("risk") != "high") return
    val now = SystemClock.elapsedRealtime()
    alertTimes.entries.removeAll { now - it.value > 15 * 60 * 1000L }
    val fingerprint = "$key:${result.getString("category")}:${result.getJSONArray("signals")}" // already hashed key
    if (now - (alertTimes[fingerprint] ?: -300_000L) < 300_000L) return
    val source = result.getString("sourceApp")
    if (now - (sourceTimes[source] ?: -60_000L) < 60_000L) return
    if (GuardianWarnings.post(context, result)) { alertTimes[fingerprint] = now; sourceTimes[source] = now }
  }
}
