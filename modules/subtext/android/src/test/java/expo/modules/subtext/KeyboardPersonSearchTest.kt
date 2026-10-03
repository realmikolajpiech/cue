package expo.modules.subtext

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class KeyboardPersonSearchTest {
  private val people = listOf("Mikołaj Kołodziej", "Anna Piech", "Kamil Książek").map { JSONObject().put("name", it) }
  @Test fun searchMatchesPolishNamesWithoutAccentsAndInAnyWordOrder() {
    assertEquals("Mikołaj Kołodziej", KeyboardPersonSearch.filter(people, "KOLODZIEJ mik").single().getString("name"))
    assertEquals("Kamil Książek", KeyboardPersonSearch.filter(people, "ksia").single().getString("name"))
  }
  @Test fun emptySearchPreservesRecentOrderAndUnmatchedSearchIsEmpty() {
    assertEquals(people, KeyboardPersonSearch.filter(people, "  "))
    assertTrue(KeyboardPersonSearch.filter(people, "nieistniejaca").isEmpty())
  }
}
