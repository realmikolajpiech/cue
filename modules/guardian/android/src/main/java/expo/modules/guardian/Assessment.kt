package expo.modules.guardian

import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

object Assessment {
  const val analysisVersion = "guardian-pl-v3-evidence"
  val risks = setOf("low", "medium", "high", "uncertain")
  val categories = setOf("family_impersonation", "credential_theft", "payment_fraud", "suspicious_link", "manipulation", "unknown")
  val signals = linkedMapOf(
    "identity_change" to "Zmiana deklarowanej tożsamości lub numeru",
    "urgency" to "Presja czasu",
    "money_request" to "Prośba o pieniądze",
    "credential_request" to "Prośba o hasło lub kod dostępu",
    "suspicious_link" to "Podejrzany link",
    "secrecy" to "Prośba o zachowanie tajemnicy",
    "authority_claim" to "Powoływanie się na autorytet",
    "emotional_pressure" to "Wywieranie presji emocjonalnej",
  )
  private val descriptions = mapOf(
    "family_impersonation" to "Wzorzec może wskazywać na podszywanie się pod bliską osobę.",
    "credential_theft" to "Wzorzec może wskazywać na próbę wyłudzenia danych dostępu.",
    "payment_fraud" to "Wzorzec może wskazywać na wyłudzenie płatności.",
    "suspicious_link" to "Wzorzec może wskazywać na niebezpieczny odnośnik.",
    "manipulation" to "W kontekście rozmowy występują możliwe oznaki manipulacji.",
    "unknown" to "Nie znaleziono jednoznacznego wzorca oszustwa. To nie jest gwarancja bezpieczeństwa.",
  )
  private val actions = mapOf(
    "family_impersonation" to "Skontaktuj się z tą osobą przez wcześniej znany numer. Nie wykonuj przelewu przed potwierdzeniem.",
    "credential_theft" to "Nie udostępniaj hasła ani kodu. Sprawdź sprawę bezpośrednio w oficjalnej aplikacji usługi.",
    "payment_fraud" to "Zweryfikuj odbiorcę i powód płatności niezależnym kanałem przed przelewem.",
    "suspicious_link" to "Nie otwieraj linku. Otwórz oficjalną aplikację lub samodzielnie wpisz znany adres.",
    "manipulation" to "Zatrzymaj się i sprawdź prośbę niezależnym kanałem. Nie podejmuj decyzji pod presją.",
    "unknown" to "Zachowaj ostrożność i samodzielnie zweryfikuj nietypowe prośby.",
  )
  /** Reject unexpected fields; free-form model text and citations never cross the bridge. */
  fun parse(raw: String): JSONObject {
    require(raw.length <= 4096)
    val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val input = JSONObject(clean)
    require(input.keys().asSequence().toSet() == setOf("risk", "category", "signals"))
    require(input.get("risk") is String && input.get("category") is String)
    val risk = input.getString("risk")
    val category = input.getString("category")
    require(risk in risks && category in categories)
    val values = input.getJSONArray("signals")
    require(values.length() <= signals.size)
    val unique = (0 until values.length()).map {
      require(values.get(it) is String)
      values.getString(it).also { v -> require(v in signals) }
    }.distinct()
    return JSONObject().put("risk", risk).put("category", category).put("signals", JSONArray(unique))
  }
  private val supportedDescriptions = mapOf(
    "identity_change" to "Nadawca deklaruje zmianę numeru lub tożsamości.",
    "urgency" to "W tekście pojawia się wyraźne ponaglenie.",
    "money_request" to "Wiadomość zawiera prośbę o pieniądze.",
    "credential_request" to "Wiadomość prosi o przekazanie kodu lub hasła.",
    "suspicious_link" to "Wiadomość zawiera link wymagający ostrożności.",
    "secrecy" to "Nadawca wyraźnie prosi, aby nie mówić o tym innym.",
    "authority_claim" to "Nadawca powołuje się na instytucję lub jej pracownika.",
    "emotional_pressure" to "Tekst zawiera bezpośrednią presję emocjonalną.",
  )
  fun result(parsed: JSONObject, source: String): JSONObject {
    val risk = parsed.getString("risk")
    val category = parsed.getString("category")
    val values = parsed.getJSONArray("signals")
    val evidence = (0 until values.length()).mapNotNull { supportedDescriptions[values.getString(it)] }.take(2).joinToString(" ")
    val explanation = when {
      risk == "uncertain" && evidence.isNotEmpty() -> "$evidence To nie wystarcza, aby uznać wiadomość za oszustwo."
      risk == "uncertain" -> "Brak wystarczających przesłanek do oceny ryzyka. Zweryfikuj sytuację niezależnym kanałem."
      risk == "low" -> "Nie znaleziono wyraźnych oznak oszustwa. To nie gwarantuje bezpieczeństwa."
      evidence.isNotEmpty() -> evidence
      else -> descriptions.getValue(category)
    }
    return JSONObject()
      .put("analysisVersion", analysisVersion)
      .put("schemaVersion", 1).put("id", UUID.randomUUID().toString())
      .put("createdAt", System.currentTimeMillis()).put("sourceApp", source)
      .put("risk", risk).put("category", category).put("signals", parsed.getJSONArray("signals"))
      .put("explanation", explanation)
      .put("recommendedAction", actions.getValue(category)).put("analysisSource", "on_device").put("reviewStatus", "new")
  }
  fun uncertain(source: String) = result(JSONObject().put("risk", "uncertain").put("category", "unknown").put("signals", JSONArray()), source)
}
