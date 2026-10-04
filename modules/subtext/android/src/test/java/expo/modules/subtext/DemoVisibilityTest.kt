package expo.modules.subtext

import org.junit.Assert.*
import org.junit.Test

class DemoVisibilityTest {
  @Test fun onlyTheTwoFullNamesAreVisible() {
    listOf("Marcel Chudyba", "Mikołaj Piech", "MIKOLAJ PIECH", " Marcel   Chudyba ").forEach { assertTrue(it, DemoVisibility.allows(it)) }
    listOf("Marta", "Miki", "Jakub Łabno", "Marcel Chudyba Nowak", "Mikołaj Piechowski", "Anna Marcel Chudyba").forEach { assertFalse(it, DemoVisibility.allows(it)) }
  }
}
