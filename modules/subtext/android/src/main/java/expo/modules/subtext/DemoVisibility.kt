package expo.modules.subtext

import java.text.Normalizer
import java.util.Locale

internal object DemoVisibility {
  fun allows(name: String): Boolean {
    val normalized = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "").replace('ł', 'l').trim().replace(Regex("\\s+"), " ")
    return normalized == "marcel chudyba" || normalized == "mikolaj piech"
  }
}
