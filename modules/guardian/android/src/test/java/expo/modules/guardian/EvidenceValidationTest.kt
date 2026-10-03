package expo.modules.guardian

import org.junit.Assert.*
import org.junit.Test

class EvidenceValidationTest {
  @Test fun validCitationsDoNotEscapeNativeLayerOrRewriteTheModelsVerdict() {
    val quote = "przelej mi 2000"
    val parsed = EvidenceValidation.parse("""{"risk":"medium","category":"unknown","signals":[{"signal":"money_request","evidence":"$quote"}]}""", listOf(quote))
    assertEquals("medium", parsed.getString("risk"))
    assertEquals("money_request", parsed.getJSONArray("signals").getString(0))
    assertFalse(parsed.toString().contains(quote))
    assertFalse(Assessment.result(parsed, "Beeper").toString().contains(quote))
  }
  @Test fun inventedQuoteIsRejectedRatherThanTreatedAsEvidence() {
    val raw = """{"risk":"high","category":"payment_fraud","signals":[{"signal":"urgency","evidence":"pilnie"}]}"""
    assertTrue(runCatching { EvidenceValidation.parse(raw, listOf("siema co tam", "przelej mi 2000")) }.isFailure)
  }
  @Test fun emptyEvidenceAllowsLowOrUncertainButNotUnsupportedHigh() {
    assertEquals("low", EvidenceValidation.parse("""{"risk":"low","category":"unknown","signals":[]}""", listOf("Dziękuję")).getString("risk"))
    assertTrue(runCatching { EvidenceValidation.parse("""{"risk":"high","category":"payment_fraud","signals":[]}""", listOf("Dziękuję")) }.isFailure)
  }
}
