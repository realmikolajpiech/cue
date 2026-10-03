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

  fun requireValid(tone: String) { require(tone in instructions) { "Nieznany styl odpowiedzi." } }
  fun instruction(tone: String): String {
    requireValid(tone)
    return instructions.getValue(tone) + " Wybrany ton zmienia nastawienie odpowiedzi, ale zachowaj osobisty sposób pisania autora próbek: słownictwo, małe lub wielkie litery, długość, skróty, interpunkcję i częstość emoji."
  }
}
