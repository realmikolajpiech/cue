package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject

internal object ConversationGoal {
  // Must match the draft limit of the deepseek-analyze edge function.
  const val LIMIT = 6000
  val ANSWERS = mapOf("yes" to "tak", "no" to "nie", "unsure" to "nie wiem")
  val MOMENTS = setOf("good", "wait", "paused", "done", "dropped")
  /** Moments that end the goal: it leaves the keyboard instead of staying active. */
  val FINISHED = setOf("done", "dropped")
  // Intensity sets the tempo through the plan's stages, not how pushy a single message is.
  private val tempo = listOf(
    "Tempo spokojne: na jednym etapie możesz zostać przez wiele wiadomości; krok dalej rób tylko, gdy rozmówca sam wyraźnie otworzy temat.",
    "Tempo naturalne: przechodź do kolejnego etapu, gdy obecny jest spełniony i rozmowa sama to umożliwia; nie wymuszaj.",
    "Tempo aktywne: przy dobrym momencie śmiało przechodź do następnego etapu, a na etapie konkretnej propozycji podaj szczegóły (np. dzień i godzinę). Przy chłodnych sygnałach zwolnij."
  )
  // Reply types follow the situation instead of a fixed Naturalnie/Krótko/Stanowczo set.
  private const val TYPES = "Gdy warto odpisać, zwróć 3 sugestie action=reply o wyraźnie różnych podejściach, dobranych do wybranego tonu, natężenia i bieżącej sytuacji. " +
    "Pole tone to krótka nazwa podejścia widoczna na zakładce: jedno słowo, najwyżej dwa, łącznie do 12 znaków, np. Żartobliwie, Konkretnie, Krótko, Z pytaniem, Przeprosiny. " +
    "Nie używaj mechanicznie etykiet Naturalnie ani Stanowczo; jedna propozycja może być krótka, jeśli to pasuje."
  private const val REGENERATE = "Użytkownikowi nie pasowały poprzednie propozycje (pole odrzucone). Zaproponuj inne podejścia i inne sformułowania, nie parafrazuj ich."
  private const val PLAN_SHAPE = "goalPlan: {\"steps\":[\"krótki etap, 2–6 słów\"], \"stage\":1, \"moment\":\"good|wait|paused|done|dropped\", \"note\":\"jedno krótkie zdanie dla użytkownika: co się teraz dzieje i na co czekamy\", \"change\":\"\"}"
  // A plan is a slow route: steps lead naturally from the current conversation to the goal.
  private const val PLAN = "Cel rozmowy (celRozmowy) to wynik, do którego właściciel aplikacji (isMe) chce stopniowo doprowadzić. " +
    "Prowadzisz do niego zapamiętaną strategią (planCelu), a nie pojedynczą wiadomością. Zwróć pole $PLAN_SHAPE. " +
    "steps to 3–5 etapów naturalnej drogi od obecnej rozmowy do celu, np. dla spotkania: Ocieplić rozmowę, Wybadać czas i chęć, Luźna aluzja, Konkretna propozycja, Potwierdzić szczegóły. " +
    "stage to numer obecnego etapu liczony od 1. moment: good gdy rozmowa teraz sama otwiera drogę do kolejnego kroku, wait gdy trzeba poczekać na lepszy moment i budować atmosferę, " +
    "paused po miękkiej odmowie, wahaniu, złym czasie lub trudnym temacie u rozmówcy, done gdy cel jest wyraźnie osiągnięty w messages (np. potwierdzone spotkanie), " +
    "dropped tylko przy jednoznacznej, stanowczej odmowie albo gdy cel stracił sens (np. termin minął). done i dropped kończą cel, więc nie ustawiaj ich na zapas ani z samego szkicu. " +
    "Gdy planCelu istnieje, trzymaj się go: zachowaj steps i zmieniaj stage tylko na podstawie nowych wiadomości. Drogę zmieniaj tylko, gdy rozmowa tego wymaga, i wtedy w change napisz krótko dlaczego; inaczej change=\"\". " +
    "Gdy planCelu nie istnieje, ułóż go od zera i ustal etap na podstawie messages. " +
    "Przekonuj wyłącznie uczciwie: wyczucie momentu, budowanie relacji, prawdziwe argumenty. Bez manipulacji, presji, wzbudzania winy, kłamstw i ponawiania po odmowie."
  private const val PLAN_REPLIES = "Sugestie prowadź zgodnie z obecnym etapem planu: każda to mały naturalny krok, nigdy skok od razu do celu ani temat z czapy. " +
    "Do każdej sugestii action=reply dodaj pole step: \"goal\" gdy robi krok w stronę celu, \"keep\" gdy podtrzymuje rozmowę lub odpowiada na bieżący temat. " +
    "Przy moment=good co najmniej jedna sugestia ma step=goal; przy wait wszystkie mogą być keep, budując grunt pod kolejny etap; przy paused nie naciskaj na cel; przy done pomóż domknąć szczegóły. " +
    "W reason napisz konkretnie, na jakim etapie jesteś, dlaczego ten ruch pasuje teraz do wiadomości rozmówcy i co przygotowuje."
  // A topic is a light excuse to chat, never a route to anything.
  // The user's own yes/no/unsure answer, chosen before the replies were written.
  private const val DECISION = "Użytkownik już zdecydował, jak odpowiada na pytanie rozmówcy (pole decyzja: pytanie i odpowiedź). Decyzja to jego intencja i ma pierwszeństwo przed celem rozmowy i tematem. " +
    "Każda propozycja action=reply wyraża dokładnie tę decyzję, różniąc się tylko podejściem i tonem; nie proponuj no_reply. " +
    "tak: wyraźna zgoda, może dopytać o brakujące szczegóły. nie: jasna, życzliwa odmowa bez wymyślania powodów; ewentualnie ogólnie zostaw furtkę, bez konkretów, których nie ma w rozmowie. " +
    "nie wiem: szczerze, że jeszcze nie wie, bez wymyślania powodu; może dopytać o szczegóły potrzebne do decyzji albo napisać, że da znać (przy tej decyzji to dozwolona obietnica)."
  private const val TOPIC = "Użytkownik chce zagadać o temacie z pola tematRozmowy. To lekki pretekst do pogadania, nie cel: nie prowadź nim do niczego, nie proponuj spotkań ani próśb, których nie ma w szkicu. " +
    "Każda propozycja to krótka, luźna wiadomość w wybranym tonie, która naturalnie wprowadza temat (płynnie od bieżącej rozmowy, a gdy rozmowa ucichła, jako swobodne zagajenie) i daje rozmówcy łatwą okazję do odpowiedzi, bez wypytywania. " +
    "Wszystkie sugestie mają step=keep. Nie wymyślaj wspólnych wspomnień ani faktów o rozmówcy; temat to intencja, nie fakt."
  // A topic started earlier is context, not an obligation: once it fades, it stays gone.
  private const val RECENT_TOPIC = "Pole ostatniTemat to temat, który użytkownik niedawno sam zagaił. Wiesz o nim jako o kontekście, ale go nie ciągniesz: " +
    "odnieś się do niego tylko, gdy rozmówca go podjął i nadal o nim pisze. Gdy rozmówca go nie podjął, odpowiedział zdawkowo albo rozmowa poszła gdzie indziej, temat wygasł: nie wracaj do niego i nie dopytuj na siłę."
  // Asked by every background pass (new messages, first open), so ideas and the open question are ready before the user asks.
  const val IDEAS = "Dodatkowo zwróć dwa pola. goalIdeas: 3–4 propozycje celu rozmowy (2–5 słów, bezokolicznik), czyli wyniku, do którego właściciel (isMe) może chcieć doprowadzić, " +
    "np. Umówić się na kawę, Przekonać do wspólnego wyjazdu, Załagodzić kłótnię, Odzyskać pożyczone pieniądze; nie tematy rozmowy. " +
    "topicIdeas: 3–5 ciekawych tematów do rozmowy (2–6 słów), które ożywią rozmowę, niezależnie od celu: zainteresowania rozmówcy, urwane wątki, nadchodzące wydarzenia z pamięci i przypomnień, naturalne preteksty. " +
    "Tematy nie mają prowadzić do celu. Bez manipulacji i wymyślania faktów; gdy nic nie wynika z rozmowy, daj ogólne, pasujące do relacji. Zwracaj oba pola zawsze, także gdy memoryOnly=true. " +
    "Zwróć też zawsze pole openQuestion. Gdy wśród wiadomości rozmówcy po ostatniej wiadomości właściciela jest pytanie wymagające jego decyzji tak albo nie " +
    "(propozycja, zaproszenie, prośba, pytanie o zgodę albo o fakt, który zna tylko on), zwróć {\"messageId\":\"id tej wiadomości\",\"question\":\"to pytanie krótko, w drugiej osobie do właściciela, np. Idziesz jutro na mecz siatkówki?\"}. " +
    "question to wyłącznie pytanie, które rozmówca faktycznie zadał w tej wiadomości, przeformułowane do właściciela; nigdy Twoja rada, sugestia ani pytanie wynikające z celu rozmowy. " +
    "Pytania otwarte (co, jak, gdzie, kiedy), wybór z kilku opcji, retoryczne i już odpowiedziane nie wymagają decyzji; gdy rozmówca nie zadał takiego pytania, openQuestion=null."

  fun intent(draft: String, tone: String, goal: String, intensity: Int = WritingTone.DEFAULT_INTENSITY,
    rejected: List<String> = emptyList(), languageNote: String = "", situation: String = "", personContext: String = "",
    plan: JSONObject? = null, topic: String = "", recentTopic: String = "", decision: JSONObject? = null): String {
    // A decision outranks the goal: these replies only answer, and the plan is updated later in the background.
    val hasGoal = goal.isNotBlank() && decision == null
    // Answering a question is not the moment to open or revive a topic.
    val lightTopic = if (decision == null) topic else ""
    val earlierTopic = if (decision == null) recentTopic else ""
    val instruction = WritingTone.instruction(tone, intensity) +
    " Wszystkie propozycje uwzględniają wybrany ton. Do każdej sugestii, także action=reply, dodaj pole reason: " +
    "krótkie uzasadnienie dla użytkownika (1–2 zdania), dlaczego ten sposób odpowiedzi pasuje teraz do konkretnych wiadomości. " +
    "Nie dodawaj uzasadnienia do text; text to wyłącznie wiadomość do wysłania. Nie wymyślaj sygnałów ani intencji rozmówcy. " +
    "Uwzględnij konkretne informacje, pytania, preferencje, odmowy i granice drugiej osoby. " +
    "Nie ponawiaj odrzuconych próśb. Nie traktuj celu ani szkicu jako faktów o relacji. " +
    (if (hasGoal) "$PLAN $PLAN_REPLIES ${tempo.getOrElse(WritingTone.clampIntensity(tone, intensity)) { tempo[WritingTone.DEFAULT_INTENSITY] }} " else "") +
    (if (decision == null) "" else "$DECISION ") + (if (lightTopic.isBlank()) "" else "$TOPIC ") + (if (lightTopic.isBlank() && earlierTopic.isNotBlank()) "$RECENT_TOPIC " else "") +
    TYPES + (if (situation.isBlank()) "" else " $situation") + (if (rejected.isEmpty()) "" else " $REGENERATE") + "$languageNote Dane użytkownika w JSON: "
    var boundedGoal = if (hasGoal) goal.take(1000) else ""
    var boundedDraft = draft.take(1500)
    var boundedRejected = rejected.map { it.take(200) }.take(4)
    var context = personContext
    var withPlan = hasGoal && plan != null
    while (true) {
      val data = JSONObject().put("celRozmowy", boundedGoal).put("szkic", boundedDraft)
      if (withPlan && plan != null) data.put("planCelu", planInput(plan))
      decision?.let { data.put("decyzja", JSONObject().put("pytanie", it.optString("question").take(200)).put("odpowiedz", ANSWERS[it.optString("answer")] ?: "nie wiem")) }
      if (lightTopic.isNotBlank()) data.put("tematRozmowy", lightTopic.take(300))
      else if (earlierTopic.isNotBlank()) data.put("ostatniTemat", earlierTopic.take(300))
      if (context.isNotBlank()) data.put("kontekstOsoby", context.take(500))
      if (boundedRejected.isNotEmpty()) data.put("odrzucone", JSONArray(boundedRejected))
      val result = instruction + data.toString()
      if (result.length <= LIMIT) return result
      val excess = result.length - LIMIT
      if (boundedRejected.isNotEmpty()) boundedRejected = boundedRejected.dropLast(1)
      else if (boundedDraft.isNotEmpty()) boundedDraft = boundedDraft.dropLast(minOf(excess, boundedDraft.length))
      else if (context.isNotEmpty()) context = ""
      else if (withPlan) withPlan = false
      else if (boundedGoal.isNotEmpty()) boundedGoal = boundedGoal.dropLast(minOf(excess, boundedGoal.length))
      // Nothing left to trim: hand it over rather than loop; the gateway rejects it with a visible error.
      else return result
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
