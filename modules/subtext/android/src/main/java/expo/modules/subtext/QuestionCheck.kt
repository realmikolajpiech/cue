package expo.modules.subtext

import org.json.JSONArray
import org.json.JSONObject

/**
 * Finds a question from the other person that the user has not answered yet and that needs their decision.
 * The AI marks it during analysis; until it has seen the newest message a cautious local check stands in.
 */
internal object QuestionCheck {
  // Open questions ask for content, not a decision.
  private val openWord = Regex("^(co|coś|jak|jaki\\w*|któr\\w*|gdzie|kiedy|kto|komu|kogo|kim|dlaczego|czemu|po co|ile|skąd|dokąd|odkąd|" +
    "what\\S*|how\\S*|where\\S*|when\\S*|who\\S*|whom|whose|why|which)\\b", RegexOption.IGNORE_CASE)
  private val filler = Regex("^(a|i|no|to|ok|okej|dobra|so|and|but|ok so|hej|hey|yo|siema|elo)\\s+", RegexOption.IGNORE_CASE)
  // "kino czy kręgle?", "tea or coffee?" offer a choice, not a yes or no.
  private val choice = Regex("\\s(czy|or|albo|lub)\\s", RegexOption.IGNORE_CASE)

  fun looksLikeYesNo(text: String): Boolean {
    if (text.startsWith(MessageMedia.PHOTO)) return false
    val sentence = Regex("[^.!?]*\\?").findAll(text).lastOrNull()?.value ?: return false
    // A short greeting before a comma ("Jak tam, idziesz jutro?") is not the question itself.
    var clause = sentence.trim().removeSuffix("?").trim()
    val comma = clause.indexOf(',')
    if (comma > 0 && clause.substring(0, comma).trim().split(Regex("\\s+")).size <= 2) clause = clause.substring(comma + 1).trim()
    repeat(2) { clause = clause.replace(filler, "") }
    if (clause.split(Regex("\\s+")).count { it.any(Char::isLetter) } < 2) return false
    if (clause.startsWith("czy ", ignoreCase = true)) return !choice.containsMatchIn(clause.substring(4))
    return !openWord.containsMatchIn(clause) && !choice.containsMatchIn(clause)
  }

  /** Messages from the other person after the user's last own message. */
  fun unanswered(messages: JSONArray): List<JSONObject> {
    val all = (0 until messages.length()).map { messages.getJSONObject(it) }
    return all.drop(all.indexOfLast { it.optBoolean("isMe") } + 1).filterNot { it.optBoolean("isMe") }
  }

  /**
   * [saved] is the AI's last verdict; it is trusted only when it covered the newest message.
   * Returns {"messageId","question"} or null.
   */
  fun open(messages: JSONArray, saved: JSONObject?): JSONObject? {
    val pending = unanswered(messages)
    if (pending.isEmpty()) return null
    val ids = pending.map { it.optString("id") }.toSet()
    val newest = messages.getJSONObject(messages.length() - 1).optString("id")
    if (saved != null && saved.optString("analyzed") == newest) {
      val id = saved.optString("messageId")
      return if (id in ids && saved.optString("question").isNotBlank()) JSONObject().put("messageId", id).put("question", saved.optString("question")) else null
    }
    val message = pending.lastOrNull { looksLikeYesNo(it.optString("text")) } ?: return null
    return JSONObject().put("messageId", message.optString("id")).put("question", message.optString("text").trim().take(200))
  }
}
