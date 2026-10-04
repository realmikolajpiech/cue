package expo.modules.subtext

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.json.JSONObject

/** A keyboard never infers the recipient from the foreground app alone. */
internal object KeyboardReplySession {
  fun available(packageName: String, type: Int, options: Int): Boolean {
    if (packageName !in setOf("com.facebook.orca", "com.instagram.android", "com.whatsapp", "com.whatsapp.w4b", "com.mikolajpiech.guardian")) return false
    if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
    if (options and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH) return false
    if (options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
    return type and InputType.TYPE_MASK_VARIATION !in setOf(
      InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
      InputType.TYPE_TEXT_VARIATION_URI)
  }

  fun rooms(rooms: List<JSONObject>, packageName: String): List<JSONObject> {
    val network = when (packageName) {
      "com.facebook.orca" -> "messenger"
      "com.instagram.android" -> "instagram"
      "com.whatsapp", "com.whatsapp.w4b" -> "whatsapp"
      else -> null
    }
    return rooms.filter { (network == null || it.optString("network") == network) &&
      (packageName == "com.mikolajpiech.guardian" || !it.optBoolean("demo")) }
  }
}
