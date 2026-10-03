// Adapted from realmikolajpiech/arie, commit ff217daadf321eca73e5245d6a85e96b059f8e8d.
package expo.modules.subtext.device

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

enum class MessagingConnectionPhase {
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    SESSION_EXPIRED,
    REAUTH_REQUIRED,
}

enum class ConversationKind { PRIVATE, GROUP, UNKNOWN }

enum class MessageSendPhase {
    ACCEPTED_LOCALLY,
    AWAITING_CONFIRMATION,
    CONFIRMED,
    TIMED_OUT,
    REJECTED,
}

data class MessageSendResult(
    val success: Boolean,
    val confirmed: Boolean,
    val phase: MessageSendPhase,
    val messageId: String? = null,
    val conversationId: String? = null,
    val recipientName: String? = null,
    val clientTransactionId: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

internal fun normalizedPersonName(value: String): String = Normalizer
    .normalize(value.trim().lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()

internal fun isNumericIdentifier(value: String): Boolean {
    val localPart = value.trim().substringBefore('@')
    val digits = localPart.count(Char::isDigit)
    return digits >= 5 && localPart.all { it.isDigit() || it in "+- ()  " }
}

internal fun conversationMatchScore(query: String, candidate: String): Int {
    val needle = normalizedPersonName(query)
    val haystack = normalizedPersonName(candidate)
    if (needle.isBlank() || haystack.isBlank() || isNumericIdentifier(candidate.trim())) return 0
    if (needle == haystack) return 100
    if (haystack.startsWith("$needle ") || haystack.endsWith(" $needle")) return 92
    val needleWords = needle.split(' ').filter(String::isNotBlank)
    val candidateWords = haystack.split(' ').filter(String::isNotBlank)
    if (needleWords.all(candidateWords::contains)) return 88
    if (haystack.contains(needle)) return 82
    val distance = levenshtein(needle, haystack)
    val maxLength = maxOf(needle.length, haystack.length)
    val similarity = if (maxLength == 0) 1.0 else 1.0 - distance.toDouble() / maxLength
    return when {
        maxLength >= 6 && similarity >= 0.88 -> 74
        maxLength >= 8 && similarity >= 0.80 -> 66
        else -> 0
    }
}

private fun levenshtein(left: String, right: String): Int {
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    for (leftIndex in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        for (rightIndex in right.indices) {
            current[rightIndex + 1] = min(
                min(current[rightIndex] + 1, previous[rightIndex + 1] + 1),
                previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1,
            )
        }
        previous = current
    }
    return previous[right.length]
}
