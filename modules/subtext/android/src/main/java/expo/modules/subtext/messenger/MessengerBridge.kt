// Adapted from realmikolajpiech/arie, commit ff217daadf321eca73e5245d6a85e96b059f8e8d.
package expo.modules.subtext.messenger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import expo.modules.subtext.device.ConversationKind
import expo.modules.subtext.device.MessageSendPhase
import expo.modules.subtext.device.MessageSendResult
import expo.modules.subtext.device.conversationMatchScore
import expo.modules.subtext.device.isNumericIdentifier
import fi.mirrormsg.fbmessagebridge.Fbmessagebridge
import fi.mirrormsg.instagrambridge.Instagrambridge
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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

enum class MessengerPhase {
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    SESSION_EXPIRED,
    REAUTH_REQUIRED,
}

data class MessengerState(
    val phase: MessengerPhase = MessengerPhase.NOT_CONFIGURED,
    val detail: String = "Not configured",
)

data class MessengerConversation(
    val id: String,
    val name: String,
    val snippet: String,
    val timestamp: Long,
    val kind: ConversationKind = ConversationKind.UNKNOWN,
    val contactId: String? = null,
    val participantIds: Set<String> = emptySet(),
    val participantNames: List<String> = emptyList(),
    val e2eeRecipientId: String? = null,
)

data class MessengerConversationMatch(val conversation: MessengerConversation, val score: Int)

data class MessengerMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMe: Boolean,
)

enum class MetaBridgeService(
    val platform: String,
    val displayName: String,
    val errorPrefix: String,
    val sessionPreferences: String,
    val keyAlias: String,
) {
    MESSENGER("facebook", "Messenger", "MESSENGER", "messenger_bridge_secure", "arie_messenger_session"),
    INSTAGRAM("instagram", "Instagram", "INSTAGRAM", "instagram_bridge_secure", "omni_instagram_session"),
}

open class MessengerRepository(
    private val context: Context,
    private val service: MetaBridgeService = MetaBridgeService.MESSENGER,
    private val onIncomingMessage: (MessengerMessage) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStore = MessengerSessionStore(context, service.sessionPreferences, service.keyAlias)
    private val mutableState = MutableStateFlow(initialState())
    private val mutableConversations = MutableStateFlow<List<MessengerConversation>>(emptyList())
    private val mutableMessages = MutableStateFlow<Map<String, List<MessengerMessage>>>(emptyMap())
    private val bridgeLock = Any()
    private val generation = AtomicLong(0)
    private val contactNames = ConcurrentHashMap<String, String>()
    private val contactByConversation = ConcurrentHashMap<String, String>()
    private val e2eeByConversation = ConcurrentHashMap<String, String>()
    private val conversationKinds = ConcurrentHashMap<String, ConversationKind>()
    private val pendingMessages = ConcurrentHashMap<String, List<MessengerMessage>>()
    private val pendingSends = ConcurrentHashMap<String, CompletableDeferred<MessageSendResult>>()
    private val completedSends = ConcurrentHashMap<String, MessageSendResult>()

    val state = mutableState.asStateFlow()
    val conversations = mutableConversations.asStateFlow()
    val messages = mutableMessages.asStateFlow()

    @Volatile private var bridge: NativeMessagingBridge? = null
    @Volatile private var connectionJob: Job? = null
    @Volatile private var connectionTimeoutJob: Job? = null
    @Volatile private var sessionAwaitingConfirmation: Pair<Long, String>? = null

    fun hasSession(): Boolean = sessionStore.hasSession()

    fun restoreIfPossible() {
        val saved = sessionStore.load()
        if (saved == null) mutableState.value = MessengerState()
        else if (mutableState.value.phase !in setOf(MessengerPhase.CONNECTED, MessengerPhase.CONNECTING)) connect(saved)
    }

    suspend fun login(cookiesJson: String, timeoutMs: Long = CONNECTION_TIMEOUT_MS): Result<Unit> = withContext(Dispatchers.IO) {
        val started = runCatching {
            require(cookiesJson.isNotBlank()) { "${service.displayName} cookies are empty" }
            val token = generation.incrementAndGet()
            val candidate = newBridge("", token)
            candidate.setCookies(cookiesJson)
            sessionAwaitingConfirmation = token to cookiesJson
            installAndConnect(candidate, token)
        }
        started.exceptionOrNull()?.let { return@withContext Result.failure(it) }
        val terminal = withTimeoutOrNull(timeoutMs) { state.first { it.phase != MessengerPhase.CONNECTING } }
        when (terminal?.phase) {
            MessengerPhase.CONNECTED -> Result.success(Unit)
            null -> {
                sessionAwaitingConfirmation = null
                stopCurrentBridge()
                mutableState.value = MessengerState(MessengerPhase.DISCONNECTED, "${service.displayName} connection timed out")
                Result.failure(MessengerConnectionException(code("CONNECT_TIMEOUT"), "${service.displayName} connection timed out"))
            }
            MessengerPhase.SESSION_EXPIRED, MessengerPhase.REAUTH_REQUIRED ->
                Result.failure(MessengerConnectionException(code("REAUTH_REQUIRED"), terminal.detail))
            else -> Result.failure(MessengerConnectionException(code("CONNECT_FAILED"), terminal.detail))
        }
    }

    suspend fun reconnect(timeoutMs: Long = CONNECTION_TIMEOUT_MS): Result<Unit> {
        val saved = sessionStore.load()
            ?: return Result.failure(MessengerConnectionException(code("NOT_CONFIGURED"), "${service.displayName} is not configured"))
        if (mutableState.value.phase == MessengerPhase.CONNECTED) return Result.success(Unit)
        val token = generation.incrementAndGet()
        stopCurrentBridge()
        delay(RECONNECT_SETTLE_MS)
        val candidate = runCatching { newBridge(saved, token) }.getOrElse {
            markConnectionFailure(it)
            return Result.failure(it)
        }
        installAndConnect(candidate, token)
        val terminal = withTimeoutOrNull(timeoutMs) { state.first { it.phase != MessengerPhase.CONNECTING } }
        return when (terminal?.phase) {
            MessengerPhase.CONNECTED -> Result.success(Unit)
            null -> {
                if (generation.get() == token) mutableState.value = MessengerState(MessengerPhase.DISCONNECTED, "${service.displayName} connection timed out")
                Result.failure(MessengerConnectionException(code("CONNECT_TIMEOUT"), "${service.displayName} connection timed out"))
            }
            MessengerPhase.SESSION_EXPIRED, MessengerPhase.REAUTH_REQUIRED ->
                Result.failure(MessengerConnectionException(code("REAUTH_REQUIRED"), terminal.detail))
            else -> Result.failure(MessengerConnectionException(code("CONNECT_FAILED"), terminal?.detail ?: "${service.displayName} connection failed"))
        }
    }

    fun reconnectInBackground() {
        if (mutableState.value.phase in setOf(MessengerPhase.CONNECTING, MessengerPhase.CONNECTED)) return
        scope.launch { reconnect() }
    }

    fun logout() {
        generation.incrementAndGet()
        connectionJob?.cancel()
        connectionTimeoutJob?.cancel()
        connectionJob = null
        connectionTimeoutJob = null
        sessionAwaitingConfirmation = null
        stopCurrentBridge()
        sessionStore.clear()
        contactNames.clear()
        contactByConversation.clear()
        e2eeByConversation.clear()
        conversationKinds.clear()
        pendingMessages.clear()
        mutableConversations.value = emptyList()
        mutableMessages.value = emptyMap()
        mutableState.value = MessengerState()
    }

    suspend fun refreshConversations(): List<MessengerConversation> = withContext(Dispatchers.IO) {
        val active = bridge ?: return@withContext activeConversations()
        runCatching { active.listConversations(Int.MAX_VALUE.toLong()) }
            .onSuccess(::mergeListedConversations)
            .onFailure { markOperationalFailure(it, "Conversation refresh failed") }
        activeConversations()
    }

    suspend fun profilePictureUrl(conversationId: String, contactId: String?): String = withContext(Dispatchers.IO) {
        val active = checkNotNull(bridge) { "Messenger not connected" }
        active.profilePictureUrl(conversationId, contactId.orEmpty())
    }

    fun searchConversations(query: String): List<MessengerConversationMatch> {
        if (query.isBlank()) return emptyList()
        return activeConversations().mapNotNull { conversation ->
            val names = buildList {
                add(conversation.name)
                addAll(conversation.participantNames)
                conversation.contactId?.let(contactNames::get)?.let(::add)
            }.distinct()
            val score = names.maxOfOrNull { conversationMatchScore(query, it) } ?: 0
            score.takeIf { it > 0 }?.let { MessengerConversationMatch(conversation, it) }
        }.sortedWith(compareByDescending<MessengerConversationMatch> { it.score }.thenBy { it.conversation.name })
    }

    suspend fun readMessages(conversationId: String, limit: Int): List<MessengerMessage> = withContext(Dispatchers.IO) {
        restoreIfPossible()
        val connected = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            state.first { it.phase != MessengerPhase.CONNECTING }
        }
        check(connected?.phase == MessengerPhase.CONNECTED) { "Messenger nie jest połączony. Połącz konto ponownie w ustawieniach." }
        // Persisted inbox rows survive a process restart; bridge metadata does not.
        // Reload it before checking the private-chat classification.
        if (conversationKinds[conversationId] == null) refreshConversations()
        check(conversationKinds[conversationId] != ConversationKind.GROUP) { "Obsługiwane są tylko rozmowy prywatne." }
        val active = checkNotNull(bridge) { "Trwa łączenie z Messengerem." }
        runCatching { active.fetchMessages(conversationId, limit.coerceIn(1, 100).toLong(), "") }
            .onSuccess(::mergeFetchedMessages)
            .onFailure { markOperationalFailure(it, "Message fetch failed") }
            .getOrThrow()
        // The bridge may return an empty table before the socket delivers history.
        if (mutableMessages.value[conversationId].isNullOrEmpty()) {
            withTimeoutOrNull(FETCH_EVENT_TIMEOUT_MS) {
                messages.first { !it[conversationId].isNullOrEmpty() }
            }
        }
        mutableMessages.value[conversationId].orEmpty().takeLast(limit.coerceIn(1, 100))
    }

    fun isEncrypted(conversationId: String): Boolean = e2eeByConversation.containsKey(conversationId)

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        clientTransactionId: String = UUID.randomUUID().toString(),
        timeoutMs: Long = SEND_TIMEOUT_MS,
    ): MessageSendResult {
        completedSends[clientTransactionId]?.let { return it }
        if (conversationId.isBlank()) return sendFailure(conversationId, clientTransactionId, code("CONVERSATION_ID_REQUIRED"), "conversationId is required")
        if (text.isBlank()) return sendFailure(conversationId, clientTransactionId, "BAD_ARGUMENT", "Message text is required")
        if (mutableState.value.phase != MessengerPhase.CONNECTED) return sendFailure(
            conversationId,
            clientTransactionId,
            if (mutableState.value.phase in setOf(MessengerPhase.SESSION_EXPIRED, MessengerPhase.REAUTH_REQUIRED)) code("SESSION_EXPIRED") else code("NOT_CONNECTED"),
            mutableState.value.detail,
        )
        if (mutableConversations.value.none { it.id == conversationId }) return sendFailure(
            conversationId, clientTransactionId, code("CONVERSATION_NOT_FOUND"), "No matching ${service.displayName} conversation is known",
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
            errorCode = code("SEND_UNCONFIRMED"),
            errorMessage = "${service.displayName} did not confirm the message before timeout",
        )
    }

    private fun connect(session: String) {
        if (mutableState.value.phase == MessengerPhase.CONNECTING) return
        scope.launch {
            val token = generation.incrementAndGet()
            runCatching { newBridge(session, token) }
                .onSuccess { installAndConnect(it, token) }
                .onFailure(::markConnectionFailure)
        }
    }

    private fun installAndConnect(candidate: NativeMessagingBridge, token: Long) {
        connectionJob?.cancel()
        connectionTimeoutJob?.cancel()
        synchronized(bridgeLock) {
            runCatching { bridge?.disconnect() }
            bridge = candidate
        }
        mutableState.value = MessengerState(MessengerPhase.CONNECTING, "Connecting to ${service.displayName}")
        connectionJob = scope.launch {
            runCatching {
                if (service == MetaBridgeService.MESSENGER) {
                    candidate.setE2EEStorePath(File(context.noBackupFilesDir, "messenger-e2ee.db").absolutePath)
                }
                candidate.connect()
            }.onFailure {
                if (bridge === candidate && generation.get() == token) markConnectionFailure(it)
            }
        }
        armConnectionTimeout(candidate, token)
    }

    private fun armConnectionTimeout(candidate: NativeMessagingBridge, token: Long) {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (bridge === candidate && generation.get() == token && mutableState.value.phase == MessengerPhase.CONNECTING) {
                mutableState.value = MessengerState(MessengerPhase.DISCONNECTED, "${service.displayName} connection timed out")
            }
        }
    }

    private fun handleEvent(token: Long, type: String, payload: String) {
        Log.d(TAG, "${service.displayName} bridge event: $type")
        when (type) {
            "READY" -> {
                connectionTimeoutJob?.cancel()
                sessionAwaitingConfirmation?.takeIf { it.first == token }?.let { (_, session) ->
                    sessionStore.save(session)
                    sessionAwaitingConfirmation = null
                }
                runCatching { bridge?.exportSession() }.getOrNull()?.takeIf(String::isNotBlank)?.let(sessionStore::save)
                mutableState.value = MessengerState(MessengerPhase.CONNECTED, "Connected on this device")
            }
            "CONTACT" -> parseContact(payload)?.let { (id, name) ->
                contactNames[id] = name
                refreshResolvedNames(id)
            }
            "THREAD_MAPPING" -> parseThreadMapping(payload)?.let { mapping ->
                mapping.contactId?.let { contactByConversation[mapping.conversationId] = it }
                mapping.e2eeRecipientId?.let { e2eeByConversation[mapping.conversationId] = it }
                if (mapping.kind != ConversationKind.UNKNOWN) conversationKinds[mapping.conversationId] = mapping.kind
                if (mapping.kind == ConversationKind.GROUP) {
                    pendingMessages.remove(mapping.conversationId)
                    mutableMessages.update { it - mapping.conversationId }
                    mutableConversations.update { rooms -> rooms.map {
                        if (it.id == mapping.conversationId) it.copy(kind = ConversationKind.GROUP) else it
                    } }
                }
                replayPendingMessages(mapping.conversationId)
                refreshResolvedNames(mapping.contactId)
            }
            "FBE2EE" -> parseMessengerEncryptedConversation(payload)?.let { raw ->
                val id = canonicalConversationId(raw.id)
                val existing = mutableConversations.value.firstOrNull { it.id == id }
                upsertConversation(existing?.copy(kind = ConversationKind.PRIVATE) ?: raw.copy(id = id))
            }
            "CONVERSATION" -> parseMessengerConversation(payload)?.let(::upsertConversation)
            "MESSAGE" -> parseMessengerMessage(payload)?.let { message ->
                val routed = message.copy(conversationId = canonicalConversationId(message.conversationId))
                if (upsertMessage(routed) && !routed.isMe && routed.text.isNotBlank()) onIncomingMessage(routed)
            }
            "LOGGED_OUT" -> {
                connectionTimeoutJob?.cancel()
                mutableState.value = MessengerState(MessengerPhase.SESSION_EXPIRED, "${service.displayName} session expired; sign in again")
            }
            "USER_ALERT" -> when {
                payload.contains("RECONNECTED") -> {
                    connectionTimeoutJob?.cancel()
                    mutableState.value = MessengerState(MessengerPhase.CONNECTED, "Connected on this device")
                }
                payload.contains("SOCKET_ERROR") -> {
                    mutableState.value = MessengerState(MessengerPhase.CONNECTING, "Reconnecting to ${service.displayName}")
                    bridge?.let { armConnectionTimeout(it, token) }
                }
            }
        }
    }

    private fun performSend(conversationId: String, text: String, transactionId: String): MessageSendResult {
        val active = bridge ?: return sendFailure(conversationId, transactionId, code("NOT_CONNECTED"), "${service.displayName} is not connected")
        return runCatching { active.sendMessageIdempotent(conversationId, text, transactionId) }.fold(
            onSuccess = { response ->
                val json = runCatching { JSONObject(response) }.getOrNull()
                val messageId = json?.optString("messageId").orEmpty()
                val confirmedConversationId = json?.optString("conversationId").orEmpty().ifBlank { conversationId }
                if (messageId.isBlank()) sendFailure(conversationId, transactionId, code("SEND_UNCONFIRMED"), "${service.displayName} did not return a server message ID")
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
                val auth = looksLikeAuthFailure(error.message.orEmpty())
                if (auth) mutableState.value = MessengerState(MessengerPhase.REAUTH_REQUIRED, "${service.displayName} requires sign-in again")
                sendFailure(conversationId, transactionId, if (auth) code("SESSION_EXPIRED") else code("SEND_REJECTED"), error.message ?: "${service.displayName} rejected the message")
            },
        )
    }

    private fun sendFailure(conversationId: String, transactionId: String, code: String, message: String) = MessageSendResult(
        success = false,
        confirmed = false,
        phase = MessageSendPhase.REJECTED,
        conversationId = conversationId.takeIf(String::isNotBlank),
        recipientName = mutableConversations.value.firstOrNull { it.id == conversationId }?.name,
        clientTransactionId = transactionId,
        errorCode = code,
        errorMessage = message,
    )

    private fun stopCurrentBridge() {
        synchronized(bridgeLock) {
            runCatching { bridge?.disconnect() }
            bridge = null
        }
    }

    private fun markConnectionFailure(error: Throwable) {
        val auth = looksLikeAuthFailure(error.message.orEmpty())
        mutableState.value = MessengerState(
            if (auth) MessengerPhase.REAUTH_REQUIRED else MessengerPhase.DISCONNECTED,
            if (auth) "${service.displayName} requires sign-in again" else error.message ?: "${service.displayName} connection failed",
        )
    }

    private fun markOperationalFailure(error: Throwable, fallback: String) {
        if (looksLikeAuthFailure(error.message.orEmpty())) mutableState.value = MessengerState(MessengerPhase.SESSION_EXPIRED, "${service.displayName} session expired; sign in again")
        else Log.w(TAG, fallback, error)
    }

    private fun activeConversations(): List<MessengerConversation> {
        val cutoff = System.currentTimeMillis() - CONVERSATION_MAX_AGE_MS
        return mutableConversations.value.filter { it.timestamp <= 0L || it.timestamp >= cutoff }
    }

    private fun upsertConversation(raw: MessengerConversation) {
        raw.contactId?.let { contactByConversation[raw.id] = it }
        raw.e2eeRecipientId?.let { e2eeByConversation[raw.id] = it }
        if (raw.kind != ConversationKind.UNKNOWN) conversationKinds[raw.id] = raw.kind
        if (conversationKinds[raw.id] == ConversationKind.GROUP) {
            mutableMessages.update { it - raw.id }
            pendingMessages.remove(raw.id)
        }
        raw.participantIds.zip(raw.participantNames).forEach { (id, name) ->
            if (name.isNotBlank() && !isNumericIdentifier(name)) contactNames[id] = name
        }
        mutableConversations.update { current ->
            val previous = current.firstOrNull { it.id == raw.id }
            val merged = raw.copy(
                name = resolveConversationName(raw, previous),
                kind = conversationKinds[raw.id] ?: raw.kind,
                contactId = raw.contactId ?: contactByConversation[raw.id] ?: previous?.contactId,
                e2eeRecipientId = raw.e2eeRecipientId ?: e2eeByConversation[raw.id] ?: previous?.e2eeRecipientId,
                participantIds = raw.participantIds.ifEmpty { previous?.participantIds.orEmpty() },
                participantNames = raw.participantNames.ifEmpty { previous?.participantNames.orEmpty() },
            )
            (current.filterNot { it.id == raw.id } + merged)
                .filter { it.timestamp <= 0L || it.timestamp >= System.currentTimeMillis() - CONVERSATION_MAX_AGE_MS }
                .sortedWith(compareByDescending<MessengerConversation> { it.timestamp > 0L }.thenByDescending { it.timestamp })
        }
        replayPendingMessages(raw.id)
    }

    private fun canonicalConversationId(id: String): String = resolveMessengerConversationId(
        id, conversationKinds, contactByConversation, e2eeByConversation,
    )

    private fun replayPendingMessages(id: String) {
        if (conversationKinds[id] != ConversationKind.PRIVATE) return
        val aliases = pendingMessages.keys.filter { canonicalConversationId(it) == id }
        aliases.forEach { alias ->
            pendingMessages.remove(alias)?.forEach { upsertMessage(it.copy(conversationId = id)) }
        }
    }

    private fun resolveConversationName(raw: MessengerConversation, previous: MessengerConversation?): String {
        if (raw.name.isNotBlank() && !isNumericIdentifier(raw.name)) return raw.name
        val contactId = raw.contactId ?: contactByConversation[raw.id] ?: previous?.contactId
        contactId?.let(contactNames::get)?.takeIf { it.isNotBlank() && !isNumericIdentifier(it) }?.let { return it }
        raw.participantNames.filterNot(::isNumericIdentifier).filter(String::isNotBlank).joinToString(", ").takeIf(String::isNotBlank)?.let { return it }
        previous?.name?.takeIf { it.isNotBlank() && !isNumericIdentifier(it) }?.let { return it }
        return when (conversationKinds[raw.id] ?: raw.kind) {
            ConversationKind.GROUP -> "Unnamed group"
            ConversationKind.PRIVATE -> "Unnamed contact"
            ConversationKind.UNKNOWN -> "Unnamed conversation"
        }
    }

    private fun refreshResolvedNames(contactId: String?) {
        mutableConversations.update { current ->
            current.map { conversation ->
                if (contactId == null || conversation.contactId == contactId || contactByConversation[conversation.id] == contactId) {
                    conversation.copy(name = resolveConversationName(conversation.copy(name = ""), conversation))
                } else conversation
            }
        }
    }

    private fun upsertMessage(raw: MessengerMessage): Boolean {
        val item = raw.copy(conversationId = canonicalConversationId(raw.conversationId))
        val kind = conversationKinds[item.conversationId] ?: ConversationKind.UNKNOWN
        if (kind == ConversationKind.GROUP) return false
        if (kind == ConversationKind.UNKNOWN) {
            // Metadata can arrive after a message. Keep it bounded and in memory
            // until the thread is confirmed private; never export unknown text.
            if (pendingMessages.size < 150 || pendingMessages.containsKey(item.conversationId)) {
                pendingMessages.compute(item.conversationId) { _, previous ->
                    (previous.orEmpty().filterNot { it.id == item.id } + item).takeLast(MAX_CACHED_MESSAGES)
                }
            }
            return false
        }
        if (item.senderName.isNotBlank() && !isNumericIdentifier(item.senderName)) contactNames[item.senderId] = item.senderName
        mutableMessages.update { current ->
            val thread = (current[item.conversationId].orEmpty().filterNot { it.id == item.id } + item)
                .sortedBy(MessengerMessage::timestamp).takeLast(MAX_CACHED_MESSAGES)
            current + (item.conversationId to thread)
        }
        val existing = mutableConversations.value.firstOrNull { it.id == item.conversationId }
        upsertConversation(
            MessengerConversation(
                id = item.conversationId,
                name = existing?.name.orEmpty(),
                snippet = item.text,
                timestamp = item.timestamp,
                kind = existing?.kind ?: conversationKinds[item.conversationId] ?: ConversationKind.UNKNOWN,
                contactId = existing?.contactId ?: contactByConversation[item.conversationId],
                participantIds = existing?.participantIds.orEmpty(),
                participantNames = existing?.participantNames.orEmpty(),
                e2eeRecipientId = existing?.e2eeRecipientId ?: e2eeByConversation[item.conversationId],
            ),
        )
        return true
    }

    private fun mergeListedConversations(raw: String?) {
        val array = runCatching { JSONObject(raw.orEmpty()).optJSONArray("conversations") }.getOrNull() ?: return
        for (index in 0 until array.length()) parseMessengerConversation(array.optJSONObject(index)?.toString().orEmpty())?.let(::upsertConversation)
    }

    private fun mergeFetchedMessages(raw: String?) {
        val array = runCatching { JSONObject(raw.orEmpty()).optJSONArray("messages") }.getOrNull() ?: return
        for (index in 0 until array.length()) parseMessengerMessage(array.optJSONObject(index)?.toString().orEmpty())?.let(::upsertMessage)
    }

    private fun newBridge(session: String, token: Long): NativeMessagingBridge {
        val eventHandler: (String?, String?) -> Unit = { eventType, payload ->
            if (generation.get() == token) handleEvent(token, eventType.orEmpty(), payload.orEmpty())
        }
        return when (service) {
            MetaBridgeService.MESSENGER -> MessengerNativeBridge(
                Fbmessagebridge.newBridge(session, object : fi.mirrormsg.fbmessagebridge.EventSink {
                    override fun onEvent(eventType: String?, jsonPayload: String?) = eventHandler(eventType, jsonPayload)
                }),
            )
            MetaBridgeService.INSTAGRAM -> InstagramNativeBridge(
                Instagrambridge.newBridge(session, object : fi.mirrormsg.instagrambridge.EventSink {
                    override fun onEvent(eventType: String?, jsonPayload: String?) = eventHandler(eventType, jsonPayload)
                }),
            )
        }
    }

    private fun code(suffix: String) = "${service.errorPrefix}_$suffix"

    private fun initialState() = if (sessionStore.hasSession()) MessengerState(MessengerPhase.DISCONNECTED, "Configured, not connected") else MessengerState()

    private companion object {
        const val TAG = "ArieMessenger"
        const val RECONNECT_SETTLE_MS = 2_000L
        const val CONNECTION_TIMEOUT_MS = 75_000L
        const val SEND_TIMEOUT_MS = 45_000L
        const val FETCH_EVENT_TIMEOUT_MS = 10_000L
        const val MAX_CACHED_MESSAGES = 200
        const val CONVERSATION_MAX_AGE_MS = 365L * 24L * 60L * 60L * 1_000L
    }
}

private interface NativeMessagingBridge {
    fun setCookies(raw: String)
    fun profilePictureUrl(threadId: String, contactId: String): String = ""
    fun setE2EEStorePath(path: String) = Unit
    fun connect()
    fun disconnect()
    fun exportSession(): String
    fun listConversations(cutoff: Long): String
    fun fetchMessages(threadId: String, count: Long, cursor: String): String
    fun sendMessageIdempotent(threadId: String, text: String, transactionId: String): String
}

private class MessengerNativeBridge(private val delegate: fi.mirrormsg.fbmessagebridge.Bridge) : NativeMessagingBridge {
    override fun profilePictureUrl(threadId: String, contactId: String): String = delegate.getProfilePictureURL(threadId, contactId)
    override fun setCookies(raw: String) = delegate.setCookies(raw)
    override fun setE2EEStorePath(path: String) = delegate.setE2EEStorePath(path)
    override fun connect() = delegate.connect()
    override fun disconnect() = delegate.disconnect()
    override fun exportSession(): String = delegate.exportSession()
    override fun listConversations(cutoff: Long): String = delegate.listConversations(cutoff)
    override fun fetchMessages(threadId: String, count: Long, cursor: String): String = delegate.fetchMessages(threadId, count, cursor)
    override fun sendMessageIdempotent(threadId: String, text: String, transactionId: String): String =
        delegate.sendMessageIdempotent(threadId, text, transactionId)
}

private class InstagramNativeBridge(private val delegate: fi.mirrormsg.instagrambridge.Bridge) : NativeMessagingBridge {
    override fun setCookies(raw: String) = delegate.setCookies(raw)
    override fun connect() = delegate.connect()
    override fun disconnect() = delegate.disconnect()
    override fun exportSession(): String = delegate.exportSession()
    override fun listConversations(cutoff: Long): String = delegate.listConversations(cutoff)
    override fun fetchMessages(threadId: String, count: Long, cursor: String): String = delegate.fetchMessages(threadId, count, cursor)
    override fun sendMessageIdempotent(threadId: String, text: String, transactionId: String): String =
        delegate.sendMessageIdempotent(threadId, text, transactionId)
}

internal fun parseMessengerConversation(payload: String): MessengerConversation? = runCatching {
    val json = JSONObject(payload)
    val id = json.opt("threadKey")?.toString().orEmpty()
    if (id.isBlank()) return null
    val isGroup = if (json.has("isGroup")) json.optBoolean("isGroup") else null
    val hasPrivateRecipient = json.optString("e2eeRecipientId").endsWith("@msgr") ||
        json.optString("contactId").let { it.isNotBlank() && it != "null" }
    MessengerConversation(
        id = id,
        name = json.optString("threadName").takeUnless(::isNumericIdentifier).orEmpty(),
        snippet = json.optString("snippet"),
        timestamp = json.optLong("timestamp"),
        kind = when {
            isGroup == true -> ConversationKind.GROUP
            isGroup == false || hasPrivateRecipient -> ConversationKind.PRIVATE
            else -> ConversationKind.UNKNOWN
        },
        contactId = json.opt("contactId")?.toString()?.takeIf(String::isNotBlank),
        participantIds = json.optJSONArray("participantIds").stringList().toSet(),
        participantNames = json.optJSONArray("participantNames").stringList().filterNot(::isNumericIdentifier),
        e2eeRecipientId = json.optString("e2eeRecipientId").takeIf(String::isNotBlank),
    )
}.getOrNull()

internal fun parseMessengerMessage(payload: String): MessengerMessage? = runCatching {
    val json = JSONObject(payload)
    val conversationId = json.opt("threadKey")?.toString().orEmpty()
    if (conversationId.isBlank()) return null
    MessengerMessage(
        id = json.optString("messageId").ifBlank { "${json.optLong("timestamp")}:${json.optString("senderId")}" },
        conversationId = conversationId,
        senderId = json.opt("senderId")?.toString().orEmpty(),
        senderName = json.optString("senderName").takeUnless(::isNumericIdentifier).orEmpty(),
        text = json.optString("text"),
        timestamp = json.optLong("timestamp"),
        isMe = json.optBoolean("isMe"),
    )
}.getOrNull()

private data class ThreadMapping(val conversationId: String, val contactId: String?, val e2eeRecipientId: String?, val kind: ConversationKind)

private fun parseContact(payload: String): Pair<String, String>? = runCatching {
    val json = JSONObject(payload)
    val id = json.opt("contactId")?.toString().orEmpty()
    val name = json.optString("name")
    if (id.isBlank() || name.isBlank() || isNumericIdentifier(name)) null else id to name
}.getOrNull()

private fun parseThreadMapping(payload: String): ThreadMapping? = runCatching {
    val json = JSONObject(payload)
    val id = json.opt("threadKey")?.toString().orEmpty()
    if (id.isBlank()) return null
    ThreadMapping(
        conversationId = id,
        contactId = json.opt("contactId")?.toString()?.takeIf(String::isNotBlank),
        e2eeRecipientId = json.optString("e2eeRecipientId").takeIf(String::isNotBlank),
        kind = when { json.optBoolean("isGroup") -> ConversationKind.GROUP; json.has("isGroup") -> ConversationKind.PRIVATE; else -> ConversationKind.UNKNOWN },
    )
}.getOrNull()

private fun JSONArray?.stringList(): List<String> = if (this == null) emptyList() else buildList {
    for (index in 0 until length()) opt(index)?.toString()?.takeIf(String::isNotBlank)?.let(::add)
}

private fun looksLikeAuthFailure(message: String): Boolean {
    val normalized = message.lowercase()
    return listOf("logged out", "unauthorized", "forbidden", "invalid cookie", "expired", "checkpoint", "challenge").any(normalized::contains)
}

private class MessengerConnectionException(val code: String, message: String) : IllegalStateException(message)

private class MessengerSessionStore(context: Context, preferencesName: String, private val keyAlias: String) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun hasSession(): Boolean = preferences.contains(CIPHERTEXT) && preferences.contains(IV)

    fun save(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        preferences.edit()
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CIPHERTEXT, Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? = runCatching {
        val iv = Base64.decode(preferences.getString(IV, null), Base64.NO_WRAP)
        val ciphertext = Base64.decode(preferences.getString(CIPHERTEXT, null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext))
    }.getOrElse { clear(); null }

    fun clear() { preferences.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = "iv"
        const val CIPHERTEXT = "ciphertext"
    }
}

// E2EE deliveries use the recipient's ID; inbox rows use the thread key.
internal fun resolveMessengerConversationId(
    id: String,
    kinds: Map<String, ConversationKind>,
    contacts: Map<String, String>,
    recipients: Map<String, String>,
): String {
    val normalized = if (id.endsWith("@msgr")) id.substringBefore('@').substringBefore(':') else id
    if (kinds[normalized] == ConversationKind.GROUP) return normalized
    return kinds.keys.sorted().firstOrNull { thread ->
        thread != normalized &&
        kinds[thread] == ConversationKind.PRIVATE &&
            (contacts[thread] == normalized || recipients[thread]?.substringBefore('@')?.substringBefore(':') == normalized)
    } ?: normalized
}

internal fun parseMessengerEncryptedConversation(payload: String): MessengerConversation? = runCatching {
    val json = JSONObject(payload)
    val chat = json.optString("chat")
    if (json.optString("step") != "MESSAGE" || !chat.endsWith("@msgr")) return null
    val id = chat.substringBefore('@').substringBefore(':')
    if (id.isBlank()) return null
    MessengerConversation(id, "", "", json.optLong("ts") * 1000, ConversationKind.PRIVATE,
        contactId = id, e2eeRecipientId = "$id@msgr")
}.getOrNull()
