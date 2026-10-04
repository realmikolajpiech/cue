package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject

internal object ConversationGoal {
  // Must match the draft limit of the deepseek-analyze edge function.
  const val LIMIT = 6000
  // How hard to steer toward the goal follows the chosen intensity of the tone.
  private val drive = listOf(
    "Do celu dąż powoli: rób krok w jego stronę tylko wtedy, gdy rozmowa sama się na niego otwiera; zwykle po prostu buduj dobrą atmosferę.",
    "Do celu dąż naturalnie: nie poruszaj go w każdej odpowiedzi, ale gdy pasuje to do rozmowy, zrób mały krok w jego stronę.",
    "Do celu dąż aktywnie: co najmniej jedna propozycja ma zrobić wyraźny krok prosto do celu, wprost i z jego konkretami (np. dzień i godzina), w wybranym tonie; pozostałe mogą być łagodniejsze. Po odmowie lub przy granicy rozmówcy odpuść."
  )
  // Reply types follow the situation instead of a fixed Naturalnie/Krótko/Stanowczo set.
  private const val TYPES = "Gdy warto odpisać, zwróć 3 sugestie action=reply o wyraźnie różnych podejściach, dobranych do wybranego tonu, natężenia, celu i bieżącej sytuacji. " +
    "Pole tone to nazwa podejścia, 1–2 słowa, np. przy flircie z celem spotkania: Żartobliwie, Z propozycją, Krótko; przy sporze: Przeprosiny, Wyjaśnienie, Krótko. " +
    "Nie używaj mechanicznie etykiet Naturalnie ani Stanowczo; jedna propozycja może być krótka, jeśli to pasuje."
  private const val REGENERATE = "Użytkownikowi nie pasowały poprzednie propozycje (pole odrzucone). Zaproponuj inne podejścia i inne sformułowania, nie parafrazuj ich."
  // Asked alongside every analysis so goal ideas are ready before the user opens the goal editor.
  const val IDEAS = "Dodatkowo zwróć pole goalIdeas: 3–4 krótkie propozycje celu (2–5 słów, bezokolicznik), " +
    "który właściciel aplikacji (isMe) może chcieć teraz osiągnąć w tej rozmowie, wynikające z bieżących wiadomości, np. Umówić się na piątek, Przeprosić za spóźnienie. " +
    "Bez manipulacji i bez wymyślania faktów; gdy nic nie wynika z rozmowy, daj ogólne, pasujące do relacji. Zwróć goalIdeas zawsze, także gdy memoryOnly=true."
  fun intent(draft: String, tone: String, goal: String, intensity: Int = WritingTone.DEFAULT_INTENSITY,
    rejected: List<String> = emptyList(), languageNote: String = "", situation: String = "", personContext: String = ""): String {
    val instruction = WritingTone.instruction(tone, intensity) +
    " Wszystkie propozycje uwzględniają wybrany ton. Do każdej sugestii, także action=reply, dodaj pole reason: " +
    "krótkie uzasadnienie dla użytkownika (1–2 zdania), dlaczego ten krok pasuje teraz do konkretnych wiadomości i celu. " +
    "Nie dodawaj uzasadnienia do text; text to wyłącznie wiadomość do wysłania. Nie wymyślaj sygnałów ani intencji rozmówcy. " +
    "Cel jest kierunkiem, nie powodem do nacisku: proponuj mały adekwatny krok, a gdy nie ma dobrego momentu, odpowiedz na bieżący temat lub zaproponuj poczekanie. " +
    "Uwzględnij konkretne informacje, pytania, preferencje, odmowy i granice drugiej osoby. " +
    "Nie ponawiaj odrzuconych próśb. Nie traktuj celu ani szkicu jako faktów o relacji. " +
    (if (goal.isBlank()) "" else drive.getOrElse(WritingTone.clampIntensity(tone, intensity)) { drive[WritingTone.DEFAULT_INTENSITY] } + " ") +
    TYPES + (if (situation.isBlank()) "" else " $situation") + (if (rejected.isEmpty()) "" else " $REGENERATE") + " $IDEAS$languageNote Dane użytkownika w JSON: "
    var boundedGoal = goal.take(1000)
    var boundedDraft = draft.take(1500)
    var boundedRejected = rejected.map { it.take(200) }.take(4)
    while (true) {
      val data = JSONObject().put("celRozmowy", boundedGoal).put("szkic", boundedDraft)
      if (personContext.isNotBlank()) data.put("kontekstOsoby", personContext.take(500))
      if (boundedRejected.isNotEmpty()) data.put("odrzucone", JSONArray(boundedRejected))
      val result = instruction + data.toString()
      if (result.length <= LIMIT) return result
      val excess = result.length - LIMIT
      if (boundedRejected.isNotEmpty()) boundedRejected = boundedRejected.dropLast(1)
      else if (boundedDraft.isNotEmpty()) boundedDraft = boundedDraft.dropLast(minOf(excess, boundedDraft.length))
      else boundedGoal = boundedGoal.dropLast(minOf(excess, boundedGoal.length))
    }
  }
}
