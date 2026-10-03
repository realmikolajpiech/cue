// Adapted from realmikolajpiech/arie, commit ff217daadf321eca73e5245d6a85e96b059f8e8d.
package expo.modules.subtext.whatsapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import expo.modules.subtext.device.ConversationKind
import expo.modules.subtext.device.MessageSendPhase
import expo.modules.subtext.device.MessageSendResult
import expo.modules.subtext.device.conversationMatchScore
import expo.modules.subtext.device.isNumericIdentifier
import fi.mirrormsg.whatsappbridge.Bridge
import fi.mirrormsg.whatsappbridge.EventSink
import fi.mirrormsg.whatsappbridge.Whatsappbridge
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class WhatsAppPhase {
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    SESSION_EXPIRED,
    REAUTH_REQUIRED,
}

data class WhatsAppState(
    val phase: WhatsAppPhase = WhatsAppPhase.NOT_CONFIGURED,
    val detail: String = "Not configured",
    val pairingCode: String? = null,
    val qrCode: String? = null,
)

data class WhatsAppConversation(
    val id: String,
    val name: String,
    val snippet: String,
    val timestamp: Long,
    val kind: ConversationKind,
    val contactId: String?,
    val participantIds: Set<String>,
    val participantNames: List<String>,
)

data class WhatsAppConversationMatch(val conversation: WhatsAppConversation, val score: Int)

data class WhatsAppMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMe: Boolean,
)

class WhatsAppRepository(
    context: Context,
    private val onIncomingMessage: (WhatsAppMessage) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = WhatsAppSessionMarker(appContext)
    private val storeFile = File(appContext.noBackupFilesDir, STORE_NAME)
    private val mutableState = MutableStateFlow(initialState())
    private val mutableConversations = MutableStateFlow<List<WhatsAppConversation>>(emptyList())
    private val mutableMessages = MutableStateFlow<Map<String, List<WhatsAppMessage>>>(emptyMap())
    private val pairingReady = MutableStateFlow(false)
    private val generation = AtomicLong(0)
    private val bridgeLock = Any()
    private val contactNames = ConcurrentHashMap<String, String>()
    private val contactByConversation = ConcurrentHashMap<String, String>()
    private val conversationKinds = ConcurrentHashMap<String, ConversationKind>()
    private val pendingSends = ConcurrentHashMap<String, CompletableDeferred<MessageSendResult>>()
    private val completedSends = ConcurrentHashMap<String, MessageSendResult>()
    @Volatile private var deviceContacts: List<DeviceContactName> = emptyList()

    val state = mutableState.asStateFlow()
    val conversations = mutableConversations.asStateFlow()
    val messages = mutableMessages.asStateFlow()

    @Volatile private var bridge: Bridge? = null
    @Volatile private var connectionJob: Job? = null
    @Volatile private var timeoutJob: Job? = null

    fun hasSession(): Boolean = session.isPaired()

    fun restoreIfPossible() {
        if (!hasSession()) return
        if (mutableState.value.phase !in setOf(WhatsAppPhase.CONNECTING, WhatsAppPhase.CONNECTED)) {
            scope.launch { reconnect() }
        }
    }

    suspend fun startPairing(replaceExpiredSession: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            generation.incrementAndGet()
            stopCurrentBridge()
            if (replaceExpiredSession) clearSessionFiles()
            val token = generation.incrementAndGet()
            val candidate = Whatsappbridge.newBridge(storeFile.absolutePath, sink(token))
            pairingReady.value = false
            installAndConnect(candidate, token, pairing = true)
        }
    }

    suspend fun requestPairCode(phone: String): Result<String> = withContext(Dispatchers.IO) {
        val normalized = normalizeInternationalPhoneNumber(phone)
        if (normalized.length < 7 || normalized.length > 15 || normalized.startsWith('0')) {
            return@withContext Result.failure(IllegalArgumentException("Use the full international phone number, including country code (e.g. +48 123 456 789)"))
        }
        val active = bridge ?: return@withContext Result.failure(WhatsAppConnectionException("WHATSAPP_NOT_CONNECTING", "Start WhatsApp pairing first"))
        if (withTimeoutOrNull(PAIR_READY_TIMEOUT_MS) { pairingReady.first { it } } == null) {
            return@withContext Result.failure(WhatsAppConnectionException("WHATSAPP_PAIR_TIMEOUT", "WhatsApp pairing did not become ready"))
        }
        runCatching { active.requestPairCode(normalized) }.onSuccess { code ->
            mutableState.value = WhatsAppState(WhatsAppPhase.CONNECTING, "Enter this code in WhatsApp on your phone", code)
        }
    }

    suspend fun reconnect(timeoutMs: Long = CONNECTION_TIMEOUT_MS): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasSession()) return@withContext Result.failure(WhatsAppConnectionException("WHATSAPP_NOT_CONFIGURED", "WhatsApp is not configured"))
        if (mutableState.value.phase == WhatsAppPhase.CONNECTED) return@withContext Result.success(Unit)
        val token = generation.incrementAndGet()
        stopCurrentBridge()
        delay(RECONNECT_SETTLE_MS)
        val candidate = runCatching { Whatsappbridge.newBridge(storeFile.absolutePath, sink(token)) }.getOrElse {
            markConnectionFailure(it)
            return@withContext Result.failure(it)
        }
        if (!candidate.isPaired) {
            session.clear()
            candidate.close()
            mutableState.value = WhatsAppState(WhatsAppPhase.SESSION_EXPIRED, "WhatsApp session expired; pair again")
            return@withContext Result.failure(WhatsAppConnectionException("WHATSAPP_SESSION_EXPIRED", "WhatsApp session expired; pair again"))
        }
        installAndConnect(candidate, token, pairing = false)
        val terminal = withTimeoutOrNull(timeoutMs) { state.first { it.phase != WhatsAppPhase.CONNECTING } }
        when (terminal?.phase) {
            WhatsAppPhase.CONNECTED -> Result.success(Unit)
            null -> {
                if (generation.get() == token) mutableState.value = WhatsAppState(WhatsAppPhase.DISCONNECTED, "WhatsApp connection timed out")
                Result.failure(WhatsAppConnectionException("WHATSAPP_CONNECT_TIMEOUT", "WhatsApp connection timed out"))
            }
            WhatsAppPhase.SESSION_EXPIRED, WhatsAppPhase.REAUTH_REQUIRED ->
                Result.failure(WhatsAppConnectionException("WHATSAPP_REAUTH_REQUIRED", terminal.detail))
            else -> Result.failure(WhatsAppConnectionException("WHATSAPP_CONNECT_FAILED", terminal?.detail ?: "WhatsApp connection failed"))
        }
    }

    fun reconnectInBackground() {
        if (mutableState.value.phase in setOf(WhatsAppPhase.CONNECTING, WhatsAppPhase.CONNECTED)) return
        scope.launch { reconnect() }
    }

    fun logout() {
        generation.incrementAndGet()
        connectionJob?.cancel()
        timeoutJob?.cancel()
        stopCurrentBridge()
        clearSessionFiles()
        contactNames.clear()
        contactByConversation.clear()
        conversationKinds.clear()
        mutableConversations.value = emptyList()
        mutableMessages.value = emptyMap()
        pairingReady.value = false
        mutableState.value = WhatsAppState()
    }

    suspend fun refreshConversations(): List<WhatsAppConversation> = withContext(Dispatchers.IO) {
        val active = bridge ?: return@withContext activeConversations()
        runCatching {
            active.consolidateLidChats()
            active.ensureGroupNames()
            active.listConversations(0)
        }.onSuccess(::mergeListedConversations).onFailure { markOperationalFailure(it, "Conversation refresh failed") }
        activeConversations()
    }

    fun searchConversations(query: String): List<WhatsAppConversationMatch> {
        if (query.isBlank()) return emptyList()
        return activeConversations().mapNotNull { conversation ->
            val candidates = buildList {
                add(conversation.name)
                addAll(conversation.participantNames)
                conversation.contactId?.let(contactNames::get)?.let(::add)
            }.distinct()
            val score = candidates.maxOfOrNull { conversationMatchScore(query, it) } ?: 0
            score.takeIf { it > 0 }?.let { WhatsAppConversationMatch(conversation, score) }
        }.sortedWith(compareByDescending<WhatsAppConversationMatch> { it.score }.thenBy { it.conversation.name })
    }

    suspend fun readMessages(conversationId: String, limit: Int): List<WhatsAppMessage> = withContext(Dispatchers.IO) {
        val active = bridge ?: return@withContext emptyList()
        runCatching { active.fetchMessages(conversationId, limit.coerceIn(1, 100).toLong()) }
            .onSuccess(::mergeFetchedMessages)
            .onFailure { markOperationalFailure(it, "Message fetch failed") }
        mutableMessages.value[conversationId].orEmpty().takeLast(limit.coerceIn(1, 100))
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        clientTransactionId: String = UUID.randomUUID().toString(),
        timeoutMs: Long = SEND_TIMEOUT_MS,
    ): MessageSendResult {
        completedSends[clientTransactionId]?.let { return it }
        if (conversationId.isBlank()) return failure(conversationId, clientTransactionId, "WHATSAPP_CONVERSATION_ID_REQUIRED", "conversationId is required")
        if (text.isBlank()) return failure(conversationId, clientTransactionId, "BAD_ARGUMENT", "Message text is required")
        if (mutableState.value.phase != WhatsAppPhase.CONNECTED) return failure(
            conversationId,
            clientTransactionId,
            if (mutableState.value.phase in setOf(WhatsAppPhase.SESSION_EXPIRED, WhatsAppPhase.REAUTH_REQUIRED)) "WHATSAPP_SESSION_EXPIRED" else "WHATSAPP_NOT_CONNECTED",
            mutableState.value.detail,
        )
        if (mutableConversations.value.none { it.id == conversationId }) return failure(
            conversationId, clientTransactionId, "WHATSAPP_CONVERSATION_NOT_FOUND", "No matching WhatsApp conversation is known",
        )
        val pending = pendingSends.getOrPut(clientTransactionId) {
            CompletableDeferred<MessageSendResult>().also { deferred ->
                scope.launch {
                    val result = performSend(conversationId, text.trim(), clientTransactionId)
                    if (result.confirmed) completedSends[clientTransactionId] = result
                    deferred.complete(result)
                    pendingSends.remove(clientTransactionId, deferred)
                }
            }
        }
        return withTimeoutOrNull(timeoutMs) { pending.await() } ?: MessageSendResult(
            success = false,
            confirmed = false,
            phase = MessageSendPhase.TIMED_OUT,
            conversationId = conversationId,
            recipientName = mutableConversations.value.firstOrNull { it.id == conversationId }?.name,
            clientTransactionId = clientTransactionId,
            errorCode = "WHATSAPP_SEND_UNCONFIRMED",
            errorMessage = "WhatsApp did not confirm the message before timeout",
        )
    }

    private fun installAndConnect(candidate: Bridge, token: Long, pairing: Boolean) {
        connectionJob?.cancel()
        timeoutJob?.cancel()
        synchronized(bridgeLock) {
            runCatching { bridge?.close() }
            bridge = candidate
        }
        mutableState.value = WhatsAppState(
            WhatsAppPhase.CONNECTING,
            if (pairing) "Preparing WhatsApp pairing" else "Connecting to WhatsApp",
        )
        connectionJob = scope.launch {
            runCatching { candidate.connect() }.onFailure {
                if (bridge === candidate && generation.get() == token) markConnectionFailure(it)
            }
        }
        armConnectionTimeout(candidate, token)
    }

    private fun armConnectionTimeout(candidate: Bridge, token: Long) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (bridge === candidate && generation.get() == token && mutableState.value.phase == WhatsAppPhase.CONNECTING) {
                mutableState.value = WhatsAppState(WhatsAppPhase.DISCONNECTED, "WhatsApp connection or pairing timed out")
            }
        }
    }

    private fun sink(token: Long) = object : EventSink {
        override fun onEvent(eventType: String?, jsonPayload: String?) {
            if (generation.get() == token) handleEvent(token, eventType.orEmpty(), jsonPayload.orEmpty())
        }
    }

    private fun handleEvent(token: Long, type: String, payload: String) {
        Log.d(TAG, "WhatsApp bridge event: $type")
        when (type) {
            "CONNECTED" -> {
                val active = bridge
                if (active?.isLoggedIn == true && active.isPaired) {
                    timeoutJob?.cancel()
                    session.markPaired()
                    mutableState.value = WhatsAppState(WhatsAppPhase.CONNECTED, "Connected on this device")
                    scope.launch { refreshConversations() }
                }
            }
            "QR" -> {
                pairingReady.value = true
                val rawQr = runCatching { JSONObject(payload).optString("code") }.getOrNull()?.takeIf(String::isNotBlank)
                val qr = rawQr ?: mutableState.value.qrCode
                mutableState.value = WhatsAppState(
                    WhatsAppPhase.CONNECTING,
                    "Zeskanuj kod QR lub połącz numerem telefonu",
                    mutableState.value.pairingCode,
                    qrCode = qr,
                )
            }
            "PAIRED" -> {
                session.markPaired()
                mutableState.value = WhatsAppState(
                    WhatsAppPhase.CONNECTING,
                    "WhatsApp sparowany; trwa łączenie…",
                    mutableState.value.pairingCode,
                    qrCode = null,
                )
                bridge?.let { armConnectionTimeout(it, token) }
                scope.launch {
                    delay(350)
                    reconnect().onFailure {
                        Log.w(TAG, "Post-pair reconnect failed", it)
                    }
                }
            }
            "CHATS_UPDATED" -> scope.launch { refreshConversations() }
            "MESSAGE" -> parseWhatsAppMessage(payload)?.let { message ->
                upsertMessage(message)
                if (!message.isMe && message.text.isNotBlank()) onIncomingMessage(message)
            }
            "DISCONNECTED" -> {
                if (mutableState.value.phase == WhatsAppPhase.CONNECTED) {
                    mutableState.value = WhatsAppState(WhatsAppPhase.DISCONNECTED, "WhatsApp disconnected")
                }
            }
            "LOGGED_OUT" -> {
                timeoutJob?.cancel()
                mutableState.value = WhatsAppState(WhatsAppPhase.SESSION_EXPIRED, "WhatsApp session expired; pair again")
            }
            "REAUTH_REQUIRED" -> {
                timeoutJob?.cancel()
                mutableState.value = WhatsAppState(WhatsAppPhase.REAUTH_REQUIRED, "WhatsApp requires pairing again")
            }
            "CONNECT_ERROR", "PAIR_ERROR" -> {
                timeoutJob?.cancel()
                val reason = runCatching { JSONObject(payload).optInt("reason") }.getOrDefault(0)
                mutableState.value = if (reason == 401 || reason == 403) {
                    WhatsAppState(WhatsAppPhase.REAUTH_REQUIRED, "WhatsApp requires pairing again")
                } else WhatsAppState(WhatsAppPhase.DISCONNECTED, "WhatsApp connection failed")
            }
            "PAIR_TIMEOUT" -> mutableState.value = WhatsAppState(WhatsAppPhase.DISCONNECTED, "WhatsApp pairing timed out")
        }
    }

    private fun performSend(conversationId: String, text: String, transactionId: String): MessageSendResult {
        val active = bridge ?: return failure(conversationId, transactionId, "WHATSAPP_NOT_CONNECTED", "WhatsApp is not connected")
        return runCatching { active.sendMessageIdempotent(conversationId, text, transactionId) }.fold(
            onSuccess = { response ->
                val json = runCatching { JSONObject(response) }.getOrNull()
                val messageId = json?.optString("messageId").orEmpty()
                val confirmedConversationId = json?.optString("conversationId").orEmpty().ifBlank { conversationId }
                if (messageId.isBlank()) failure(conversationId, transactionId, "WHATSAPP_SEND_UNCONFIRMED", "WhatsApp did not return a server-confirmed message ID")
                else MessageSendResult(
                    success = true,
                    confirmed = true,
                    phase = MessageSendPhase.CONFIRMED,
                    messageId = messageId,
                    conversationId = confirmedConversationId,
                    recipientName = mutableConversations.value.firstOrNull { it.id == conversationId }?.name,
                    clientTransactionId = transactionId,
                )
            },
            onFailure = { error ->
                val expired = looksLikeExpiredSession(error.message.orEmpty())
                if (expired) mutableState.value = WhatsAppState(WhatsAppPhase.SESSION_EXPIRED, "WhatsApp session expired; pair again")
                failure(conversationId, transactionId, if (expired) "WHATSAPP_SESSION_EXPIRED" else "WHATSAPP_SEND_REJECTED", error.message ?: "WhatsApp rejected the message")
            },
        )
    }

    private fun failure(conversationId: String, transactionId: String, code: String, detail: String) = MessageSendResult(
        success = false,
        confirmed = false,
        phase = MessageSendPhase.REJECTED,
        conversationId = conversationId.takeIf(String::isNotBlank),
        recipientName = mutableConversations.value.firstOrNull { it.id == conversationId }?.name,
        clientTransactionId = transactionId,
        errorCode = code,
        errorMessage = detail,
    )

    private fun mergeListedConversations(payload: String) {
        deviceContacts = loadDeviceContactNames()
        val items = JSONObject(payload).optJSONArray("conversations") ?: JSONArray()
        for (index in 0 until items.length()) parseWhatsAppConversation(items.optJSONObject(index)?.toString().orEmpty())?.let(::upsertConversation)
    }

    private fun mergeFetchedMessages(payload: String) {
        val items = JSONObject(payload).optJSONArray("messages") ?: JSONArray()
        for (index in 0 until items.length()) parseWhatsAppMessage(items.optJSONObject(index)?.toString().orEmpty())?.let(::upsertMessage)
    }

    private fun upsertConversation(raw: WhatsAppConversation) {
        raw.contactId?.let { contactByConversation[raw.id] = it }
        conversationKinds[raw.id] = raw.kind
        if (raw.kind == ConversationKind.PRIVATE) {
            raw.contactId?.let { id ->
                raw.participantNames.firstOrNull { it.isNotBlank() && !isNumericIdentifier(it) }?.let { contactNames[id] = it }
            }
        }
        mutableConversations.update { current ->
            val previous = current.firstOrNull { it.id == raw.id }
            val merged = raw.copy(
                name = resolveName(raw, previous),
                contactId = raw.contactId ?: contactByConversation[raw.id] ?: previous?.contactId,
                participantIds = raw.participantIds.ifEmpty { previous?.participantIds.orEmpty() },
                participantNames = raw.participantNames.ifEmpty { previous?.participantNames.orEmpty() },
            )
            (current.filterNot { it.id == raw.id } + merged)
                .filter { it.timestamp <= 0L || it.timestamp >= System.currentTimeMillis() - ONE_YEAR_MS }
                .sortedWith(compareByDescending<WhatsAppConversation> { it.timestamp > 0L }.thenByDescending { it.timestamp })
        }
    }

    private fun resolveName(raw: WhatsAppConversation, previous: WhatsAppConversation?): String {
        if (raw.name.isNotBlank() && !isNumericIdentifier(raw.name)) return raw.name
        raw.contactId?.let(contactNames::get)?.let { return it }
        if (raw.kind == ConversationKind.PRIVATE) {
            resolveDeviceContactName(raw.contactId, raw.participantIds, deviceContacts)?.let { return it }
        }
        raw.participantNames.firstOrNull { it.isNotBlank() && !isNumericIdentifier(it) }?.let { return it }
        if (previous != null && previous.name.isNotBlank() && !isNumericIdentifier(previous.name)) return previous.name
        return if (raw.kind == ConversationKind.GROUP) "Unnamed group" else "Unnamed contact"
    }

    private fun loadDeviceContactNames(): List<DeviceContactName> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val result = mutableListOf<DeviceContactName>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER, ContactsContract.CommonDataKinds.Phone.NUMBER)
        runCatching {
            appContext.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val normalizedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = if (normalizedIndex >= 0) cursor.getString(normalizedIndex).orEmpty() else ""
                    val fallbackNumber = cursor.getString(numberIndex).orEmpty()
                    if (name.isNotBlank()) result += DeviceContactName(number.ifBlank { fallbackNumber }, name)
                }
            }
        }.onFailure { Log.w(TAG, "Could not resolve WhatsApp names from device contacts", it) }
        return result
    }

    private fun upsertMessage(item: WhatsAppMessage) {
        if (item.senderId.isNotBlank() && item.senderName.isNotBlank() && !isNumericIdentifier(item.senderName)) contactNames[item.senderId] = item.senderName
        mutableMessages.update { current ->
            val merged = (current[item.conversationId].orEmpty().filterNot { it.id == item.id } + item)
                .sortedBy(WhatsAppMessage::timestamp).takeLast(MAX_CACHED_MESSAGES)
            current + (item.conversationId to merged)
        }
        val existing = mutableConversations.value.firstOrNull { it.id == item.conversationId }
        if (existing != null) {
            upsertConversation(existing.copy(snippet = item.text, timestamp = item.timestamp))
        }
    }

    private fun activeConversations(): List<WhatsAppConversation> {
        val cutoff = System.currentTimeMillis() - ONE_YEAR_MS
        return mutableConversations.value.filter { it.timestamp <= 0L || it.timestamp >= cutoff }
    }

    private fun stopCurrentBridge() {
        synchronized(bridgeLock) {
            runCatching { bridge?.close() }
            bridge = null
        }
    }

    private fun clearSessionFiles() {
        session.clear()
        val parent = storeFile.parentFile ?: return
        val prefix = storeFile.name
        parent.listFiles()?.filter { it.name == prefix || it.name.startsWith("$prefix-") || it.name.startsWith("$prefix.") }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun markConnectionFailure(error: Throwable) {
        val expired = looksLikeExpiredSession(error.message.orEmpty())
        mutableState.value = WhatsAppState(
            if (expired) WhatsAppPhase.REAUTH_REQUIRED else WhatsAppPhase.DISCONNECTED,
            if (expired) "WhatsApp requires pairing again" else error.message ?: "WhatsApp connection failed",
        )
    }

    private fun markOperationalFailure(error: Throwable, fallback: String) {
        if (looksLikeExpiredSession(error.message.orEmpty())) mutableState.value = WhatsAppState(WhatsAppPhase.SESSION_EXPIRED, "WhatsApp session expired; pair again")
        else Log.w(TAG, fallback, error)
    }

    private fun initialState() = if (session.isPaired()) WhatsAppState(WhatsAppPhase.DISCONNECTED, "Configured, not connected") else WhatsAppState()

    private companion object {
        const val TAG = "OmniWhatsApp"
        const val STORE_NAME = "whatsapp-session.db"
        const val CONNECTION_TIMEOUT_MS = 60_000L
        const val PAIR_READY_TIMEOUT_MS = 20_000L
        const val SEND_TIMEOUT_MS = 50_000L
        const val RECONNECT_SETTLE_MS = 250L
        const val ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1_000L
        const val MAX_CACHED_MESSAGES = 500
    }
}

internal fun parseWhatsAppConversation(payload: String): WhatsAppConversation? = runCatching {
    val json = JSONObject(payload)
    val id = json.optString("conversationID").ifBlank { return null }
    val participants = json.optJSONArray("participants") ?: JSONArray()
    val participantIds = linkedSetOf<String>()
    val participantNames = mutableListOf<String>()
    for (index in 0 until participants.length()) {
        val participant = participants.optJSONObject(index) ?: continue
        if (participant.optBoolean("isMe")) continue
        participant.optJSONObject("ID")?.optString("number")?.takeIf(String::isNotBlank)?.let(participantIds::add)
        participant.optString("fullName").ifBlank { participant.optString("firstName") }
            .takeIf { it.isNotBlank() && !isNumericIdentifier(it) }?.let(participantNames::add)
    }
    val isGroup = json.optBoolean("isGroup") || id.endsWith("@g.us")
    WhatsAppConversation(
        id = id,
        name = json.optString("name"),
        snippet = json.optJSONObject("latestMessage")?.optString("displayContent").orEmpty(),
        timestamp = microsToMillis(json.optString("timestamp")),
        kind = if (isGroup) ConversationKind.GROUP else ConversationKind.PRIVATE,
        contactId = json.optString("contactId").takeIf(String::isNotBlank),
        participantIds = participantIds,
        participantNames = participantNames.distinct(),
    )
}.getOrNull()

internal fun parseWhatsAppMessage(payload: String): WhatsAppMessage? = runCatching {
    val json = JSONObject(payload)
    val conversationId = json.optString("conversationID").ifBlank { return null }
    val sender = json.optJSONObject("senderParticipant") ?: JSONObject()
    val contents = json.optJSONArray("messageInfo") ?: JSONArray()
    val text = buildList {
        for (index in 0 until contents.length()) {
            contents.optJSONObject(index)?.optJSONObject("messageContent")?.optString("content")
                ?.takeIf(String::isNotBlank)?.let(::add)
        }
    }.joinToString("\n")
    WhatsAppMessage(
        id = json.optString("messageID"),
        conversationId = conversationId,
        senderId = sender.optJSONObject("ID")?.optString("number").orEmpty(),
        senderName = sender.optString("fullName").ifBlank { sender.optString("firstName") },
        text = text,
        timestamp = microsToMillis(json.optString("timestamp")),
        isMe = json.optBoolean("isMe") || json.optBoolean("fromMe") || sender.optBoolean("isMe"),
    )
}.getOrNull()

private fun microsToMillis(value: String): Long {
    val parsed = value.toLongOrNull() ?: return 0L
    return when {
        parsed > 10_000_000_000_000L -> parsed / 1_000L
        parsed > 10_000_000_000L -> parsed
        parsed > 0L -> parsed * 1_000L
        else -> 0L
    }
}

internal data class DeviceContactName(val number: String, val name: String)

internal fun resolveDeviceContactName(
    contactId: String?,
    participantIds: Set<String>,
    contacts: List<DeviceContactName>,
): String? {
    val candidates = buildSet {
        contactId?.let(::add)
        addAll(participantIds)
    }.map(::normalizePhoneDigits).filter { it.length >= MIN_PHONE_MATCH_DIGITS }
    if (candidates.isEmpty()) return null

    val matchingNames = contacts.mapNotNull { contact ->
        val saved = normalizePhoneDigits(contact.number)
        if (saved.length < MIN_PHONE_MATCH_DIGITS || contact.name.isBlank()) return@mapNotNull null
        contact.name.takeIf {
            candidates.any { whatsapp ->
                whatsapp == saved || (whatsapp.endsWith(saved) || saved.endsWith(whatsapp)) && minOf(whatsapp.length, saved.length) >= MIN_PHONE_MATCH_DIGITS
            }
        }
    }.distinct()
    return matchingNames.singleOrNull()
}

internal fun normalizeInternationalPhoneNumber(phone: String, defaultCountryCode: String = "48"): String {
    var trimmed = phone.trim()
    if (trimmed.startsWith("+")) {
        trimmed = trimmed.substring(1).trim()
    } else if (trimmed.startsWith("00")) {
        trimmed = trimmed.substring(2).trim()
    }
    val digits = trimmed.filter(Char::isDigit)
    return if (digits.length == 9 && !digits.startsWith('0')) {
        defaultCountryCode + digits
    } else {
        digits
    }
}

private fun normalizePhoneDigits(value: String): String = value.filter(Char::isDigit).trimStart('0')

private const val MIN_PHONE_MATCH_DIGITS = 7

private fun looksLikeExpiredSession(message: String): Boolean {
    val normalized = message.lowercase()
    return listOf("logged out", "not logged in", "401", "main device gone", "stream replaced", "device removed").any(normalized::contains)
}

private class WhatsAppConnectionException(val code: String, message: String) : IllegalStateException(message)

private class WhatsAppSessionMarker(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    fun isPaired(): Boolean = preferences.getBoolean(PAIRED, false)
    fun markPaired() { preferences.edit().putBoolean(PAIRED, true).apply() }
    fun clear() { preferences.edit().clear().apply() }

    private companion object {
        const val PREFS_NAME = "whatsapp_bridge_state"
        const val PAIRED = "paired"
    }
}
