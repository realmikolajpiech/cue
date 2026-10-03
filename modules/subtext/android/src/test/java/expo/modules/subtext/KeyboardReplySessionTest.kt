package expo.modules.subtext

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class KeyboardReplySessionTest {
  @Test fun replyToolsAreAbsentInSearchAndSensitiveFields() {
    assertTrue(KeyboardReplySession.available("com.facebook.orca", InputType.TYPE_CLASS_TEXT, 0))
    for (type in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
      InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)) {
      assertFalse(KeyboardReplySession.available("com.facebook.orca", type, 0))
    }
    assertFalse(KeyboardReplySession.available("com.facebook.orca", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH))
    assertFalse(KeyboardReplySession.available("com.facebook.orca", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
    assertFalse(KeyboardReplySession.available("com.android.chrome", InputType.TYPE_CLASS_TEXT, 0))
  }

  @Test fun aMessengerEditorNeverOffersWhatsappOrExampleContext() {
    val rooms = listOf(
      JSONObject().put("id", "m").put("network", "messenger"),
      JSONObject().put("id", "w").put("network", "whatsapp"),
      JSONObject().put("id", "demo").put("network", "messenger").put("demo", true))
    assertEquals(listOf("m"), KeyboardReplySession.rooms(rooms, "com.facebook.orca").map { it.getString("id") })
    assertEquals(listOf("w"), KeyboardReplySession.rooms(rooms, "com.whatsapp.w4b").map { it.getString("id") })
    assertEquals(3, KeyboardReplySession.rooms(rooms, "com.mikolajpiech.guardian").size)
  }
}
