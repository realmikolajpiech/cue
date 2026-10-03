package expo.modules.guardian

/** Full text and hashed identities stay in RAM. Warning previews are bounded separately. */
data class PrivateMessage(val identity: String, val text: String, val receivedAt: Long, val sender: String = "")

class ConversationBuffer(
  private val clock: () -> Long,
  private val ttl: Long = 15 * 60 * 1000L,
  private val maxConversations: Int = 32,
  private val maxMessages: Int = 5,
  private val maxChars: Int = 1500,
) {
  private val conversations = linkedMapOf<String, MutableList<PrivateMessage>>()

  @Synchronized fun append(key: String, messages: List<PrivateMessage>): List<PrivateMessage>? {
    prune()
    if (messages.isEmpty()) return null
    val current = conversations.remove(key) ?: mutableListOf()
    val previous = current.toList()
    var changed = false
    messages.forEach { incoming ->
      if (incoming.receivedAt <= clock() - ttl || incoming.text.isBlank()) return@forEach
      val clean = incoming.copy(text = incoming.text.take(maxChars))
      val i = current.indexOfFirst { it.identity == clean.identity }
      if (i < 0) { current.add(clean); changed = true }
      else if (current[i].text != clean.text) { current[i] = clean; changed = true }
    }
    current.sortBy { it.receivedAt }
    while (current.size > maxMessages) current.removeAt(0)
    if (current.isNotEmpty()) conversations[key] = current
    while (conversations.size > maxConversations) conversations.remove(conversations.keys.first())
    // A replay can reintroduce older messages that are immediately evicted. Analyze only
    // when the final retained context changes; new retained messages are never delayed.
    return if (changed && current != previous) current.toList() else null
  }
  @Synchronized fun prune() {
    val cutoff = clock() - ttl
    conversations.values.forEach { list -> list.removeAll { it.receivedAt <= cutoff } }
    conversations.entries.removeAll { it.value.isEmpty() }
  }
  @Synchronized fun remove(key: String) { conversations.remove(key) }
  @Synchronized fun clear() { conversations.clear() }
  @Synchronized fun size(): Int { prune(); return conversations.size }
}
