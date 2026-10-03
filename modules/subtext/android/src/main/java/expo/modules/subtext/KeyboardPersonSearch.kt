package expo.modules.subtext

import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

internal object KeyboardPersonSearch {
  private fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ł', 'l'), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
  fun filter(rooms: List<JSONObject>, query: String): List<JSONObject> {
    val words = normalize(query).trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return rooms.filter { room -> normalize(room.optString("name")).let { name -> words.all(name::contains) } }
  }
}
