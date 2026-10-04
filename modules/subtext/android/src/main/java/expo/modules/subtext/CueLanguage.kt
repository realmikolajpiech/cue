package expo.modules.subtext

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Interface language chosen in the Cue app, shared with the keyboard through preferences. */
internal object CueLanguage {
  private const val KEY = "language"
  private var cached: Pair<String, Resources>? = null

  fun get(context: Context): String = context.getSharedPreferences("subtext", Context.MODE_PRIVATE)
    .getString(KEY, null) ?: if (Locale.getDefault().language == "pl") "pl" else "en"

  fun set(context: Context, language: String) {
    require(language == "pl" || language == "en") { "Unsupported language: $language" }
    context.getSharedPreferences("subtext", Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
  }

  /** Resources resolved for the app language rather than the system locale. */
  fun resources(context: Context): Resources {
    val language = get(context)
    cached?.let { (cachedLanguage, resources) -> if (cachedLanguage == language) return resources }
    val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale(language)) }
    return context.createConfigurationContext(configuration).resources.also { cached = language to it }
  }

  fun string(context: Context, id: Int, vararg args: Any): String = resources(context).getString(id, *args)

  private val tones = listOf("natural", "flirt", "assertive", "empathetic", "calming")
  private fun toneIndex(tone: String) = tones.indexOf(tone).coerceAtLeast(0)
  private val levelArrays = mapOf("flirt" to R.array.cue_levels_flirt, "assertive" to R.array.cue_levels_assertive,
    "empathetic" to R.array.cue_levels_empathetic, "calming" to R.array.cue_levels_calming)
  private val hintArrays = mapOf("natural" to R.array.cue_hints_natural, "flirt" to R.array.cue_hints_flirt,
    "assertive" to R.array.cue_hints_assertive, "empathetic" to R.array.cue_hints_empathetic, "calming" to R.array.cue_hints_calming)

  fun toneLabel(context: Context, tone: String): String = resources(context).getStringArray(R.array.cue_tone_labels)[toneIndex(tone)]
  fun toneShortLabel(context: Context, tone: String): String = resources(context).getStringArray(R.array.cue_tone_short_labels)[toneIndex(tone)]
  fun toneLevels(context: Context, tone: String): List<String> = levelArrays[tone]?.let { resources(context).getStringArray(it).toList() }.orEmpty()
  fun toneHint(context: Context, tone: String, intensity: Int): String {
    val options = resources(context).getStringArray(hintArrays[tone] ?: R.array.cue_hints_natural)
    return options[if (options.size == 1) 0 else WritingTone.clampIntensity(tone, intensity)]
  }

  /** Every AI field, reply text included, follows the language chosen in the app. */
  fun promptNote(context: Context): String = if (get(context) == "en")
    " Language: English. Write every JSON text field in English, including suggestions text, tone, reason, goalIdeas, topicIdeas, goalPlan, summary, memory and reminders, even when the conversation is in another language."
  else " Język: polski. Wszystkie pola tekstowe JSON, także propozycje odpowiedzi (text), tone, reason, goalIdeas, topicIdeas, goalPlan, podsumowania, pamięć i przypomnienia, pisz po polsku, nawet gdy rozmowa jest w innym języku."

  /** Incoming messages and goals for the writing style preview, in the app language. */
  fun previewScenarios(context: Context): List<Pair<String, String>> = if (get(context) == "en") listOf(
    "Hey, how are you?" to "Reply to a casual greeting without inventing events from your life.",
    "Fancy meeting up tomorrow?" to "Say you'd like to meet and ask about the time, without inventing your plans.",
    "I did it! I got the job!" to "Congratulate them on the good news.",
    "I'm having a rough day." to "Show support and encourage them to share what happened.",
    "Sorry, I'll be 20 minutes late." to "Accept the small delay.",
    "What are we doing this weekend?" to "Suggest a walk together as a hypothetical idea, without inventing preferences or plans."
  ) else listOf(
    "Hej, co u Ciebie?" to "Odpowiedz na luźne przywitanie bez wymyślania wydarzeń ze swojego życia.",
    "Masz ochotę spotkać się jutro?" to "Wyraź chęć spotkania i zapytaj o godzinę, bez wymyślania swoich planów.",
    "Udało się! Dostałem tę pracę!" to "Pogratuluj rozmówcy dobrej wiadomości.",
    "Mam dziś ciężki dzień." to "Okaż wsparcie i zachęć rozmówcę do opowiedzenia, co się stało.",
    "Sorry, będę 20 minut później." to "Zaakceptuj drobne spóźnienie.",
    "Co robimy na weekend?" to "Zaproponuj wspólny spacer jako hipotetyczny pomysł, bez wymyślania preferencji ani planów."
  )
}
