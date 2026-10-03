package expo.modules.subtext

import org.json.JSONObject

/** Only unseen messages trigger work; retry failures without marking them as analyzed. */
internal object MemoryAutoUpdate {
  const val QUIET_MS = 3_000L
  const val RETRY_MS = 60_000L

  fun eligible(memory: JSONObject, now: Long): Boolean {
    if (memory.optLong("revision") <= memory.optLong("analyzedRevision")) return false
    if (now - memory.optLong("updatedAt") < QUIET_MS) return false
    val failedAt = memory.optLong("attemptedAt")
    return failedAt <= memory.optLong("contextUpdatedAt") || now - failedAt >= RETRY_MS
  }
}
