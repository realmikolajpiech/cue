package expo.modules.guardian

/** Short-lived selectable notification text. Never written to disk. */
class NotificationHistory(private val clock: () -> Long) {
  data class Entry(val id: String, val source: String, val text: String, val createdAt: Long, val receivedAt: Long)
  private val entries = linkedMapOf<String, Entry>()
  @Synchronized fun add(key: String, source: String, messages: List<PrivateMessage>, wallTime: Long) {
    prune()
    val now = clock()
    messages.filter { now - it.receivedAt in 0 until TTL }.forEach { message ->
      val id = "$key:${message.identity}"
      entries[id] = Entry(id, source, message.text.take(1500), wallTime - (now - message.receivedAt), message.receivedAt)
    }
    while (entries.size > 100) entries.remove(entries.keys.first())
  }
  @Synchronized fun list(): List<Entry> { prune(); return entries.values.sortedByDescending { it.createdAt } }
  @Synchronized fun clear() { entries.clear() }
  @Synchronized fun prune() { val now = clock(); entries.entries.removeAll { now - it.value.receivedAt >= TTL } }
  companion object { private const val TTL = 15 * 60 * 1000L }
}
