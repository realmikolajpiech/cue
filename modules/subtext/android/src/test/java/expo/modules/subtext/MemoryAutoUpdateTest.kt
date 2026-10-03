package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MemoryAutoUpdateTest {
  private fun message(id: String, me: Boolean, timestamp: Long) = JSONObject()
    .put("id", id).put("text", "Oddam ci 20 zł kiedyś").put("isMe", me).put("timestamp", timestamp)

  @Test fun oneMessageInEitherDirectionTriggersAutomaticMemoryAfterShortPause() {
    for (me in listOf(true, false)) {
      val memory = JSONObject()
      PersonMemory.update(memory, listOf(message("one", me, 1000)), 1000)
      assertFalse(MemoryAutoUpdate.eligible(memory, 3999))
      assertTrue(MemoryAutoUpdate.eligible(memory, 4000))
    }
  }

  @Test fun burstWaitsForLastNewMessageButRepeatedSyncDoesNotDelayOrRepeatAnalysis() {
    val memory = JSONObject()
    val first = message("first", false, 1000)
    val second = message("second", true, 2000)
    PersonMemory.update(memory, listOf(first), 1000)
    PersonMemory.update(memory, listOf(first, second), 2000)
    assertFalse(MemoryAutoUpdate.eligible(memory, 4000))
    PersonMemory.update(memory, listOf(first, second), 4900)
    assertTrue(MemoryAutoUpdate.eligible(memory, 5000))
    PersonMemory.apply(memory, JSONArray(), JSONArray().put(first).put(second), 2, 5000)
    assertFalse(MemoryAutoUpdate.eligible(memory, 6000))
    PersonMemory.update(memory, listOf(first, second), 7000)
    assertFalse(MemoryAutoUpdate.eligible(memory, 10000))
  }

  @Test fun messageArrivingDuringAnalysisRemainsPendingForNextUpdate() {
    val memory = JSONObject()
    val first = message("first", false, 1000)
    PersonMemory.update(memory, listOf(first), 1000)
    val analyzedRevision = memory.getLong("revision")
    PersonMemory.update(memory, listOf(first, message("new", true, 5000)), 5000)
    PersonMemory.apply(memory, JSONArray(), JSONArray().put(first), analyzedRevision, 6000)
    assertTrue(MemoryAutoUpdate.eligible(memory, 8000))
  }

  @Test fun failureIsRetriedWithoutLosingPendingMessagesAndSuccessClearsBackoff() {
    val memory = JSONObject().put("revision", 1).put("updatedAt", 1000).put("attemptedAt", 5000)
    assertFalse(MemoryAutoUpdate.eligible(memory, 64999))
    assertTrue(MemoryAutoUpdate.eligible(memory, 65000))
    memory.put("contextUpdatedAt", 65000).put("analyzedRevision", 1)
    assertFalse(MemoryAutoUpdate.eligible(memory, 70000))
    PersonMemory.update(memory, listOf(message("new", true, 66000)), 66000)
    assertTrue(MemoryAutoUpdate.eligible(memory, 69000))
  }
}
