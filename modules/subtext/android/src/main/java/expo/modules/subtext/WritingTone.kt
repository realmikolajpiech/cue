package expo.modules.subtext

internal object WritingTone {
  val labels = linkedMapOf("natural" to "Naturalny", "flirt" to "Flirtujący", "assertive" to "Asertywny",
    "empathetic" to "Empatyczny", "calming" to "Łagodzący konflikt")
  fun label(tone: String) = labels[tone] ?: labels.getValue("natural")
  private val instructions = mapOf(
    "natural" to "Ton naturalny: odpowiedz tak, jak zwykle pisze autor próbek, bez narzucania dodatkowego tonu.",
    "flirt" to "Ton flirtujący: okaż subtelne zainteresowanie, ciepło i lekki humor, bez nachalności ani zakładania istniejącej relacji. W trudnej sytuacji zachowaj takt, nie seksualizuj wsparcia.",
    "assertive" to "Ton asertywny: komunikuj jasno potrzeby i granice, spokojnie, bez agresji. Nie wymyślaj odmowy ani granicy, jeśli cel odpowiedzi jej nie wymaga.",
    "empathetic" to "Ton empatyczny: zauważ emocje rozmówcy, okaż zrozumienie i życzliwość, unikaj pustych pocieszeń i nieproszonych rad.",
    "calming" to "Ton łagodzący konflikt: pisz spokojnie i życzliwie, bez oskarżeń i eskalacji, szukaj porozumienia. Nie zakładaj konfliktu, jeśli go nie ma."
  )

  // Index 1 is the default strength; the tone instruction alone already describes it.
  const val DEFAULT_INTENSITY = 1
  private val levels = mapOf(
    "flirt" to listOf("Subtelny", "Zalotny", "Odważny"),
    "assertive" to listOf("Uprzejmy", "Stanowczy", "Twardy"),
    "empathetic" to listOf("Ciepły", "Wspierający", "Bardzo bliski"),
    "calming" to listOf("Spokojny", "Pojednawczy", "Przepraszający")
  )
  private val levelInstructions = mapOf(
    "flirt" to listOf(
      "Natężenie subtelne: zainteresowanie tylko delikatnie zasugeruj, raczej ciepłem i humorem niż komplementami.", "",
      "Natężenie odważne: pisz pewnie i bezpośrednio, możesz wprost zaproponować spotkanie; wyraźne komplementy tylko gdy rozmówca odwzajemnia zainteresowanie; bez wulgarności, seksualizacji i presji."),
    "assertive" to listOf(
      "Natężenie uprzejme: wyrażaj potrzeby łagodnie i z dużą dozą uprzejmości.", "",
      "Natężenie twarde: krótko i jednoznacznie, bez tłumaczenia się i przepraszania, ale nadal bez obrażania."),
    "empathetic" to listOf(
      "Natężenie ciepłe: krótko i życzliwie, bez wchodzenia głęboko w emocje.", "",
      "Natężenie bardzo bliskie: okaż pełne zaangażowanie i gotowość do wsparcia, ostrożnie nazwij emocje rozmówcy, bez przesady i wymyślania faktów."),
    "calming" to listOf(
      "Natężenie spokojne: neutralnie i rzeczowo obniż temperaturę, bez przyznawania racji na siłę.", "",
      "Natężenie przepraszające: weź odpowiedzialność za własną część i przeproś wprost, ale nie przypisuj sobie winy, której nie ma w rozmowie.")
  )
  private val hints = mapOf(
    "natural" to listOf("Piszesz jak zwykle, bez dodatkowego tonu."),
    "flirt" to listOf("Lekkie zainteresowanie, ciepło i odrobina humoru.", "Wyraźny flirt z humorem, ale bez nacisku.",
      "Pewnie i wprost, z konkretną propozycją."),
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
    return instructions.getValue(tone) + (if (level.isEmpty()) "" else " $level") +
      " Wybrany ton zmienia nastawienie odpowiedzi, ale zachowaj osobisty sposób pisania autora próbek: słownictwo, małe lub wielkie litery, długość, skróty, interpunkcję i częstość emoji."
  }
}
