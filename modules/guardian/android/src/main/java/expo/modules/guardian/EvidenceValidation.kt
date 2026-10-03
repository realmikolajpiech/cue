package expo.modules.guardian

import org.json.JSONArray
import org.json.JSONObject

/** Validates citations, not their meaning. Classification remains the model's decision. */
object EvidenceValidation {
  fun parse(raw: String, messages: List<String>): JSONObject {
    require(raw.length <= 4096)
    val input = JSONObject(raw.trim())
    require(input.keys().asSequence().toSet() == setOf("risk", "category", "signals"))
    require(input.get("risk") is String && input.get("category") is String)
    val risk = input.getString("risk")
    val category = input.getString("category")
    require(risk in Assessment.risks && category in Assessment.categories)
    val values = input.getJSONArray("signals")
    require(values.length() <= 2)
    val codes = mutableListOf<String>()
    repeat(values.length()) { index ->
      val observation = values.getJSONObject(index)
      require(observation.keys().asSequence().toSet() == setOf("signal", "evidence"))
      require(observation.get("signal") is String && observation.get("evidence") is String)
      val signal = observation.getString("signal")
      val evidence = observation.getString("evidence")
      require(signal in Assessment.signals && evidence.isNotBlank() && evidence.length <= 100)
      require(messages.any { it.contains(evidence) }) { "model_evidence_not_in_input" }
      require(signal !in codes)
      codes.add(signal)
    }
    require(risk != "high" || codes.isNotEmpty()) { "model_missing_evidence" }
    // Private citations die here: only enum codes can leave the native layer.
    return JSONObject().put("risk", risk).put("category", category).put("signals", JSONArray(codes))
  }
}
