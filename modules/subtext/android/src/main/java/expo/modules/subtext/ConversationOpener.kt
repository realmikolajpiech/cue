package expo.modules.subtext

import org.json.JSONArray

/** Opening a conversation: a first message, or reaching out again after it went quiet. */
internal object ConversationOpener {
  // Shorter silences are an ongoing conversation; the normal reply flow handles them.
  const val QUIET_MS = 24 * 60 * 60 * 1000L
  private const val DAY_MS = 24 * 60 * 60 * 1000L

  enum class Kind { FIRST, REACH_OUT, LATE_REPLY }
  data class Situation(val kind: Kind, val gapMs: Long = 0, val unansweredAttempts: Int = 0)

  fun situation(messages: JSONArray, now: Long): Situation? {
    if (messages.length() == 0) return Situation(Kind.FIRST)
    val last = messages.getJSONObject(messages.length() - 1)
    val gap = now - last.optLong("timestamp")
    if (gap < QUIET_MS) return null
    if (!last.optBoolean("isMe")) return Situation(Kind.LATE_REPLY, gap)
    // Own messages after the other person's last one; a new attempt starts after a quiet day.
    var attempts = 1
    var index = messages.length() - 1
    while (index > 0 && messages.getJSONObject(index - 1).optBoolean("isMe")) {
      if (messages.getJSONObject(index).optLong("timestamp") - messages.getJSONObject(index - 1).optLong("timestamp") >= QUIET_MS) attempts++
      index--
    }
    return Situation(Kind.REACH_OUT, gap, attempts)
  }

  // The model gets the gap already computed; it is unreliable at date arithmetic.
  fun gapLabel(ms: Long): String {
    val days = (ms / DAY_MS).toInt().coerceAtLeast(1)
    return when {
      days < 14 -> plural(days, "dzień", "dni", "dni")
      days < 60 -> plural(days / 7, "tydzień", "tygodnie", "tygodni")
      days < 365 -> plural(days / 30, "miesiąc", "miesiące", "miesięcy")
      else -> plural(days / 365, "rok", "lata", "lat")
    }
  }
  private fun plural(n: Int, one: String, few: String, many: String): String {
    val word = if (n == 1) one else if (n % 10 in 2..4 && n % 100 !in 12..14) few else many
    return "$n $word"
  }

  private fun pause(ms: Long): String {
    val days = ms / DAY_MS
    return when {
      days < 4 -> "Krótka przerwa: wróć luźno, jakby rozmowa trwała dalej, najlepiej do ostatniego wątku; nie tłumacz się z przerwy."
      days < 21 -> "Przerwa kilku dni lub tygodni: potrzebny haczyk, np. nawiązanie do planu, otwartej sprawy z przypomnień albo pytanie, jak poszło coś, o czym rozmówca wspominał; nie tłumacz się."
      days < 180 -> "Dłuższa przerwa: odnów kontakt z naturalnym pretekstem; możesz z luzem przyznać, że dawno nie gadaliście, ale bez płaszczenia się i bez udawania, że to było wczoraj."
      else -> "Bardzo długa przerwa: ciepłe, niezobowiązujące odnowienie kontaktu z jednym pretekstem i łatwym pytaniem; zero presji i pretensji, nie zakładaj, że wszystko jest jak dawniej."
    }
  }

  private const val RULES = "Zasady: jeden konkretny haczyk, łatwy do podjęcia koniec (pytanie lub zaczepka), krótko; nie samo hej ani co tam, bez akapitów, przeprosin na wyrost i wymyślonych wspólnych wspomnień. " +
    "Zwróć 3 propozycje o różnych podejściach, np. Z nawiązaniem, Lekko, Z pretekstem; w reason napisz, czemu to dobry sposób, by się teraz odezwać."

  fun instruction(situation: Situation, person: String = "", context: String = ""): String = when (situation.kind) {
    Kind.FIRST -> "Sytuacja: pierwsza wiadomość do " + (person.ifBlank { "tej osoby" }) + ", nie ma żadnej historii rozmowy. Napisz opener, który zaczyna znajomość. " +
      (if (context.isBlank()) "Nic o tej osobie nie wiadomo: napisz naturalny opener bez udawania znajomości i bez zgadywania faktów. "
       else "Kontekst od użytkownika to jedyna wiedza o tej osobie: zbuduj opener na jednym haczyku z niego i nie dodawaj innych faktów. ") + RULES
    Kind.REACH_OUT -> "Sytuacja: rozmowa ucichła ${gapLabel(situation.gapMs)} temu, ostatnią wiadomość wysłał użytkownik i chce się odezwać. " +
      "Napisz wiadomość, która otwiera rozmowę na nowo, a nie odpowiedź na ostatnią wiadomość; nie proponuj no_reply tylko dlatego, że wątek się domknął. " +
      (if (situation.unansweredAttempts >= 2) "Ostatnie ${situation.unansweredAttempts} próby kontaktu użytkownika zostały bez odpowiedzi: pierwsza sugestia to no_reply z radą, by dać rozmówcy przestrzeń, pozostałe bardzo lekkie, bez dopytywania, czemu nie odpisuje. " else "") +
      pause(situation.gapMs) + " " + RULES
    Kind.LATE_REPLY -> "Sytuacja: rozmówca napisał ostatni ${gapLabel(situation.gapMs)} temu i nadal czeka na odpowiedź użytkownika. " +
      "Odpowiedz na jego wiadomość z uwzględnieniem opóźnienia: przy dłuższej przerwie odnieś się do niej krótko i z luzem, bez wylewnych przeprosin i wymyślonych wymówek; " +
      "gdy temat się zdezaktualizował, zamiast odpowiadać na stary wątek otwórz rozmowę na nowo. " + pause(situation.gapMs) + " " + RULES
  }
}
