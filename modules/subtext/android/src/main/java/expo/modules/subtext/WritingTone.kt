package expo.modules.subtext

internal object WritingTone {
  val labels = linkedMapOf("natural" to "Naturalny", "flirt" to "Flirtujący", "assertive" to "Asertywny",
    "empathetic" to "Empatyczny", "calming" to "Łagodzący konflikt")
  fun label(tone: String) = labels[tone] ?: labels.getValue("natural")
  private val shortLabels = mapOf("flirt" to "Flirt", "calming" to "Łagodzący")
  fun shortLabel(tone: String) = shortLabels[tone] ?: label(tone)
  private val instructions = mapOf(
    "natural" to "Ton naturalny: odpowiedz tak, jak zwykle pisze autor próbek, bez narzucania dodatkowego tonu.",
    "flirt" to "Ton flirtujący: pewnie, z humorem i ciekawością tej osoby. Nawiąż do konkretu z rozmowy, przeplataj droczenie ciepłem, zostaw furtkę do odbicia piłeczki. " +
      "Bez tekstów na podryw, ogólnych komplementów, przesłodzenia i tłumaczenia się; krótko.",
    "assertive" to "Ton asertywny: komunikuj jasno potrzeby i granice, spokojnie, bez agresji. Nie wymyślaj odmowy ani granicy, jeśli cel odpowiedzi jej nie wymaga.",
    "empathetic" to "Ton empatyczny: zauważ emocje rozmówcy, okaż zrozumienie i życzliwość, unikaj pustych pocieszeń i nieproszonych rad.",
    "calming" to "Ton łagodzący konflikt: pisz spokojnie i życzliwie, bez oskarżeń i eskalacji, szukaj porozumienia. Nie zakładaj konfliktu, jeśli go nie ma."
  )
  // Flirt intensity is a ceiling; the other person's replies decide how close to it a reply may go.
  private const val FLIRT_TEMPERATURE = "Po cichu oceń temperaturę rozmówcy 0–4 tylko z jego wiadomości: 0 zdawkowo lub odmawia, 1 uprzejmie bez inicjatywy, " +
    "2 dopytuje i żartuje, 3 sam się droczy lub komplementuje, 4 sam flirtuje wprost. Bez historii flirtu 0–1. " +
    "Pisz najwyżej stopień cieplej niż rozmówca i nie ponad pułap; gdy stygnie, zejdź niżej. Jedna propozycja na dopuszczalnym teraz maksimum, jedna łagodniejsza. " +
    "Po odmowie nie ponawiaj, bez presji; przy trudnym temacie wspieraj. Temperatury nie podawaj w JSON ani w reason."

  // Index 1 is the default strength.
  const val DEFAULT_INTENSITY = 1
  private val levels = mapOf(
    "flirt" to listOf("Subtelny", "Zalotny", "Odważny"),
    "assertive" to listOf("Uprzejmy", "Stanowczy", "Twardy"),
    "empathetic" to listOf("Ciepły", "Wspierający", "Bardzo bliski"),
    "calming" to listOf("Spokojny", "Pojednawczy", "Przepraszający")
  )
  private val levelInstructions = mapOf(
    "flirt" to listOf(
      "Natężenie subtelne, pułap 2: zainteresowanie między wierszami, przez humor i lekkie droczenie; bez komplementów wyglądu i dwuznaczności.",
      "Natężenie zalotne, pułap 3: wyraźny flirt na luzie: konkretne komplementy przełamane droczeniem, lekkie dwuznaczności, spotkanie z przymrużeniem oka.",
      "Natężenie odważne, pułap 4: pewność siebie i napięcie; przy 2 powiedz wprost, że ci się podoba, i zaproponuj spotkanie; przy 3–4 pikantne aluzje i niedopowiedzenia, z klasą. " +
        "Dosłowność seksualna i wulgaryzmy tylko w odpowiedzi na takie same wiadomości rozmówcy."),
    "assertive" to listOf(
      "Natężenie uprzejme: wyrażaj potrzeby łagodnie i z dużą dozą uprzejmości.",
      "Natężenie stanowcze: nazwij potrzebę lub granicę wprost w jednym–dwóch zdaniach, uprzejmie, bez zbędnych przeprosin i usprawiedliwień.",
      "Natężenie twarde: krótko i jednoznacznie, bez tłumaczenia się i przepraszania, ale nadal bez obrażania."),
    "empathetic" to listOf(
      "Natężenie ciepłe: krótko i życzliwie, bez wchodzenia głęboko w emocje.",
      "Natężenie wspierające: nazwij to, co rozmówca przeżywa, daj znać, że jesteś obok, i gdy pasuje, zaproponuj konkretną pomoc.",
      "Natężenie bardzo bliskie: okaż pełne zaangażowanie i gotowość do wsparcia, ostrożnie nazwij emocje rozmówcy, bez przesady i wymyślania faktów."),
    "calming" to listOf(
      "Natężenie spokojne: neutralnie i rzeczowo obniż temperaturę, bez przyznawania racji na siłę.",
      "Natężenie pojednawcze: uznaj punkt widzenia rozmówcy, mów o swoich odczuciach zamiast oskarżeń i zaproponuj wspólne rozwiązanie.",
      "Natężenie przepraszające: weź odpowiedzialność za własną część i przeproś wprost, ale nie przypisuj sobie winy, której nie ma w rozmowie.")
  )
  private val hints = mapOf(
    "natural" to listOf("Piszesz jak zwykle, bez dodatkowego tonu."),
    "flirt" to listOf("Ciekawość i humor, flirt między wierszami.", "Wyraźny flirt na luzie, z komplementem i droczeniem.",
      "Pewnie i z napięciem. Im bardziej odwzajemnia, tym śmielej."),
    "assertive" to listOf("Jasno, ale miękko i grzecznie.", "Spokojnie i jasno mówisz, czego chcesz.",
      "Krótko i jednoznacznie, bez tłumaczenia się."),
    "empathetic" to listOf("Krótko i życzliwie okazujesz zrozumienie.", "Zauważasz emocje i dajesz wsparcie.",
      "Bardzo osobiście i z pełnym zaangażowaniem."),
    "calming" to listOf("Neutralnie i rzeczowo obniżasz napięcie.", "Szukasz porozumienia bez oskarżeń.",
      "Bierzesz odpowiedzialność za swoją część i przepraszasz.")
  )

  fun requireValid(tone: String) { require(tone in instructions) { "Nieznany styl odpowiedzi." } }
  fun levels(tone: String): List<String> = levels[tone].orEmpty()
  fun clampIntensity(tone: String, intensity: Int) = if (levels(tone).isEmpty()) DEFAULT_INTENSITY else intensity.coerceIn(0, levels(tone).lastIndex)
  fun hint(tone: String, intensity: Int): String {
    val options = hints[tone] ?: hints.getValue("natural")
    return options[if (options.size == 1) 0 else clampIntensity(tone, intensity)]
  }
  fun instruction(tone: String, intensity: Int = DEFAULT_INTENSITY): String {
    requireValid(tone)
    val level = levelInstructions[tone]?.get(clampIntensity(tone, intensity)).orEmpty()
    return instructions.getValue(tone) + (if (level.isEmpty()) "" else " $level") + (if (tone == "flirt") " $FLIRT_TEMPERATURE" else "") +
      " Wybrany ton zmienia nastawienie odpowiedzi, ale zachowaj osobisty sposób pisania autora próbek: słownictwo, małe lub wielkie litery, długość, skróty, interpunkcję i częstość emoji."
  }
}
