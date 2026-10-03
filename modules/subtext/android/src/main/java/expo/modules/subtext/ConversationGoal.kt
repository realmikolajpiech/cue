package expo.modules.subtext

import org.json.JSONObject

internal object ConversationGoal {
  // How hard to steer toward the goal follows the chosen intensity of the tone.
  private val drive = listOf(
    "Do celu dąż powoli: rób krok w jego stronę tylko wtedy, gdy rozmowa sama się na niego otwiera; zwykle po prostu buduj dobrą atmosferę.",
    "Do celu dąż naturalnie: nie poruszaj go w każdej odpowiedzi, ale gdy pasuje to do rozmowy, zrób mały krok w jego stronę.",
    "Do celu dąż aktywnie: co najmniej jedna propozycja ma zrobić wyraźny krok prosto do celu, wprost i z jego konkretami (np. dzień i godzina), w wybranym tonie; pozostałe mogą być łagodniejsze. Po odmowie lub przy granicy rozmówcy odpuść."
  )
  fun intent(draft: String, tone: String, goal: String, intensity: Int = WritingTone.DEFAULT_INTENSITY): String {
    val instruction = WritingTone.instruction(tone, intensity) +
    " Wszystkie propozycje uwzględniają wybrany ton. Do każdej sugestii, także action=reply, dodaj pole reason: " +
    "krótkie uzasadnienie dla użytkownika (1–2 zdania), dlaczego ten krok pasuje teraz do konkretnych wiadomości i celu. " +
    "Nie dodawaj uzasadnienia do text; text to wyłącznie wiadomość do wysłania. Nie wymyślaj sygnałów ani intencji rozmówcy. " +
    "Cel jest kierunkiem, nie powodem do nacisku: proponuj mały adekwatny krok, a gdy nie ma dobrego momentu, odpowiedz na bieżący temat lub zaproponuj poczekanie. " +
    "Uwzględnij konkretne informacje, pytania, preferencje, odmowy i granice drugiej osoby. " +
    "Przy flircie śmielsze komplementy i aluzje tylko przy wyraźnym odwzajemnieniu; uprzejmość nie oznacza zainteresowania. " +
    "Nie ponawiaj odrzuconych próśb. Nie traktuj celu ani szkicu jako faktów o relacji. " +
    (if (goal.isBlank()) "" else drive.getOrElse(WritingTone.clampIntensity(tone, intensity)) { drive[WritingTone.DEFAULT_INTENSITY] } + " ") +
    "Dane użytkownika w JSON: "
    var boundedGoal = goal.take(1000)
    var boundedDraft = draft.take(1500)
    while (true) {
      val result = instruction + JSONObject().put("celRozmowy", boundedGoal).put("szkic", boundedDraft).toString()
      if (result.length <= 4000) return result
      val excess = result.length - 4000
      if (boundedDraft.isNotEmpty()) boundedDraft = boundedDraft.dropLast(minOf(excess, boundedDraft.length))
      else boundedGoal = boundedGoal.dropLast(minOf(excess, boundedGoal.length))
    }
  }
}
