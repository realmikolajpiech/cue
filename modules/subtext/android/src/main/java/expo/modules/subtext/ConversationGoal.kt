package expo.modules.subtext

import org.json.JSONObject

internal object ConversationGoal {
  fun intent(draft: String, tone: String, goal: String): String {
    val instruction = WritingTone.instruction(tone) +
    " Wszystkie propozycje uwzględniają wybrany ton. Do każdej sugestii, także action=reply, dodaj pole reason: " +
    "krótkie uzasadnienie dla użytkownika (1–2 zdania), dlaczego ten krok pasuje teraz do konkretnych wiadomości i celu. " +
    "Nie dodawaj uzasadnienia do text; text to wyłącznie wiadomość do wysłania. Nie wymyślaj sygnałów ani intencji rozmówcy. " +
    "Cel jest kierunkiem, nie powodem do nacisku: proponuj mały adekwatny krok, a gdy nie ma dobrego momentu, odpowiedz na bieżący temat lub zaproponuj poczekanie. " +
    "Uwzględnij konkretne informacje, pytania, preferencje, odmowy i granice drugiej osoby. " +
    "Przy flircie zwiększaj bezpośredniość tylko przy wyraźnym odwzajemnieniu; uprzejmość nie oznacza zainteresowania. " +
    "Nie poruszaj celu w każdej odpowiedzi, nie ponawiaj odrzuconych próśb. Nie traktuj celu ani szkicu jako faktów o relacji. " +
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
