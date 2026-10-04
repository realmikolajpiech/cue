package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject

internal object ConversationGoal {
  // Must match the draft limit of the deepseek-analyze edge function.
  const val LIMIT = 6000
  val MOMENTS = setOf("good", "wait", "paused", "done")
  // Intensity sets the tempo through the plan's stages, not how pushy a single message is.
  private val tempo = listOf(
    "Tempo spokojne: na jednym etapie możesz zostać przez wiele wiadomości; krok dalej rób tylko, gdy rozmówca sam wyraźnie otworzy temat.",
    "Tempo naturalne: przechodź do kolejnego etapu, gdy obecny jest spełniony i rozmowa sama to umożliwia; nie wymuszaj.",
    "Tempo aktywne: przy dobrym momencie śmiało przechodź do następnego etapu, a na etapie konkretnej propozycji podaj szczegóły (np. dzień i godzinę). Przy chłodnych sygnałach zwolnij."
  )
  // Reply types follow the situation instead of a fixed Naturalnie/Krótko/Stanowczo set.
  private const val TYPES = "Gdy warto odpisać, zwróć 3 sugestie action=reply o wyraźnie różnych podejściach, dobranych do wybranego tonu, natężenia i bieżącej sytuacji. " +
    "Pole tone to nazwa podejścia, 1–2 słowa, np. Żartobliwie, Z propozycją, Krótko, Przeprosiny, Wyjaśnienie. " +
    "Nie używaj mechanicznie etykiet Naturalnie ani Stanowczo; jedna propozycja może być krótka, jeśli to pasuje."
  private const val REGENERATE = "Użytkownikowi nie pasowały poprzednie propozycje (pole odrzucone). Zaproponuj inne podejścia i inne sformułowania, nie parafrazuj ich."
  private const val PLAN_SHAPE = "goalPlan: {\"steps\":[\"krótki etap, 2–6 słów\"], \"stage\":1, \"moment\":\"good|wait|paused|done\", \"note\":\"jedno krótkie zdanie dla użytkownika: co się teraz dzieje i na co czekamy\", \"change\":\"\"}"
  // A plan is a slow route: steps lead naturally from the current conversation to the goal.
  private const val PLAN = "Cel rozmowy (celRozmowy) to wynik, do którego właściciel aplikacji (isMe) chce stopniowo doprowadzić. " +
    "Prowadzisz do niego zapamiętaną strategią (planCelu), a nie pojedynczą wiadomością. Zwróć pole $PLAN_SHAPE. " +
    "steps to 3–5 etapów naturalnej drogi od obecnej rozmowy do celu, np. dla spotkania: Ocieplić rozmowę, Wybadać czas i chęć, Luźna aluzja, Konkretna propozycja, Potwierdzić szczegóły. " +
    "stage to numer obecnego etapu liczony od 1. moment: good gdy rozmowa teraz sama otwiera drogę do kolejnego kroku, wait gdy trzeba poczekać na lepszy moment i budować atmosferę, " +
    "paused po odmowie, granicy, złym czasie lub trudnym temacie u rozmówcy, done gdy cel jest wyraźnie osiągnięty w messages. " +
    "Gdy planCelu istnieje, trzymaj się go: zachowaj steps i zmieniaj stage tylko na podstawie nowych wiadomości. Drogę zmieniaj tylko, gdy rozmowa tego wymaga, i wtedy w change napisz krótko dlaczego; inaczej change=\"\". " +
    "Gdy planCelu nie istnieje, ułóż go od zera i ustal etap na podstawie messages. " +
    "Przekonuj wyłącznie uczciwie: wyczucie momentu, budowanie relacji, prawdziwe argumenty. Bez manipulacji, presji, wzbudzania winy, kłamstw i ponawiania po odmowie."
  private const val PLAN_REPLIES = "Sugestie prowadź zgodnie z obecnym etapem planu: każda to mały naturalny krok, nigdy skok od razu do celu ani temat z czapy. " +
    "Do każdej sugestii action=reply dodaj pole step: \"goal\" gdy robi krok w stronę celu, \"keep\" gdy podtrzymuje rozmowę lub odpowiada na bieżący temat. " +
    "Przy moment=good co najmniej jedna sugestia ma step=goal; przy wait wszystkie mogą być keep, budując grunt pod kolejny etap; przy paused nie naciskaj na cel; przy done pomóż domknąć szczegóły. " +
    "W reason napisz konkretnie, na jakim etapie jesteś, dlaczego ten ruch pasuje teraz do wiadomości rozmówcy i co przygotowuje."
  private const val TOPIC = "Użytkownik chce poruszyć temat z pola tematRozmowy. Propozycje mają naturalnie go wprowadzić, z płynnym przejściem od bieżącej rozmowy, w wybranym tonie. " +
    "Nie wymyślaj wspólnych wspomnień ani faktów o rozmówcy; temat to intencja, nie fakt."
  // Asked alongside every analysis so ideas are ready before the user opens either editor.
  const val IDEAS = "Dodatkowo zwróć dwa pola. goalIdeas: 3–4 propozycje celu rozmowy (2–5 słów, bezokolicznik), czyli wyniku, do którego właściciel (isMe) może chcieć doprowadzić, " +
    "np. Umówić się na kawę, Przekonać do wspólnego wyjazdu, Załagodzić kłótnię, Odzyskać pożyczone pieniądze; nie tematy rozmowy. " +
    "topicIdeas: 3–5 ciekawych tematów do rozmowy (2–6 słów), które ożywią rozmowę, niezależnie od celu: zainteresowania rozmówcy, urwane wątki, nadchodzące wydarzenia z pamięci i przypomnień, naturalne preteksty. " +
    "Tematy nie mają prowadzić do celu. Bez manipulacji i wymyślania faktów; gdy nic nie wynika z rozmowy, daj ogólne, pasujące do relacji. Zwracaj oba pola zawsze, także gdy memoryOnly=true."

  fun intent(draft: String, tone: String, goal: String, intensity: Int = WritingTone.DEFAULT_INTENSITY,
    rejected: List<String> = emptyList(), languageNote: String = "", situation: String = "", personContext: String = "",
    plan: JSONObject? = null, topic: String = ""): String {
    val hasGoal = goal.isNotBlank()
    val instruction = WritingTone.instruction(tone, intensity) +
    " Wszystkie propozycje uwzględniają wybrany ton. Do każdej sugestii, także action=reply, dodaj pole reason: " +
    "krótkie uzasadnienie dla użytkownika (1–2 zdania), dlaczego ten sposób odpowiedzi pasuje teraz do konkretnych wiadomości. " +
    "Nie dodawaj uzasadnienia do text; text to wyłącznie wiadomość do wysłania. Nie wymyślaj sygnałów ani intencji rozmówcy. " +
    "Uwzględnij konkretne informacje, pytania, preferencje, odmowy i granice drugiej osoby. " +
    "Nie ponawiaj odrzuconych próśb. Nie traktuj celu ani szkicu jako faktów o relacji. " +
    (if (hasGoal) "$PLAN $PLAN_REPLIES ${tempo.getOrElse(WritingTone.clampIntensity(tone, intensity)) { tempo[WritingTone.DEFAULT_INTENSITY] }} " else "") +
    (if (topic.isBlank()) "" else "$TOPIC ") +
    TYPES + (if (situation.isBlank()) "" else " $situation") + (if (rejected.isEmpty()) "" else " $REGENERATE") + " $IDEAS$languageNote Dane użytkownika w JSON: "
    var boundedGoal = goal.take(1000)
    var boundedDraft = draft.take(1500)
    var boundedRejected = rejected.map { it.take(200) }.take(4)
    while (true) {
      val data = JSONObject().put("celRozmowy", boundedGoal).put("szkic", boundedDraft)
      if (hasGoal && plan != null) data.put("planCelu", planInput(plan))
      if (topic.isNotBlank()) data.put("tematRozmowy", topic.take(300))
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

  /** Background passes (new messages, first ideas, a freshly saved goal) keep ideas and the plan current without suggestions. */
  fun background(goal: String, plan: JSONObject?, languageNote: String): String {
    if (goal.isBlank()) return IDEAS + languageNote
    val data = JSONObject().put("celRozmowy", goal.take(1000))
    if (plan != null) data.put("planCelu", planInput(plan))
    return "$IDEAS $PLAN Gdy memoryOnly=true, nadal zwróć goalPlan zaktualizowany na podstawie messages.$languageNote Dane użytkownika w JSON: $data"
  }

  private fun planInput(plan: JSONObject) = JSONObject().put("steps", plan.optJSONArray("steps") ?: JSONArray())
    .put("stage", plan.optInt("stage", 1)).put("moment", plan.optString("moment")).put("note", plan.optString("note"))

  /** Validated plan from the model, or null when it is unusable. */
  fun cleanPlan(raw: JSONObject): JSONObject? {
    val input = raw.optJSONArray("steps") ?: return null
    val steps = (0 until input.length()).map { input.optString(it).trim().take(80) }.filter { it.isNotBlank() }.take(6)
    if (steps.size < 2) return null
    val moment = raw.optString("moment").takeIf { it in MOMENTS } ?: "wait"
    val stage = if (moment == "done") steps.size else raw.optInt("stage", 1).coerceIn(1, steps.size)
    return JSONObject().put("steps", JSONArray(steps)).put("stage", stage).put("moment", moment)
      .put("note", raw.optString("note").trim().take(200)).put("change", raw.optString("change").trim().take(200))
  }
}
