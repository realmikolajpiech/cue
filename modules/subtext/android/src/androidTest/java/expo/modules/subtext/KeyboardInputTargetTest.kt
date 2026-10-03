package expo.modules.subtext

import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardInputTargetTest {
  private fun connection(value: String): BaseInputConnection {
    val buffer = SpannableStringBuilder(value).apply { Selection.setSelection(this, length) }
    return object : BaseInputConnection(View(InstrumentationRegistry.getInstrumentation().targetContext), true) {
      override fun getEditable(): Editable = buffer
    }
  }
  @Test fun cachedConnectionCannotEditComposerWhileSearchingAndReturnsToDraftAfterSelection() {
    val host = connection("Mój szkic")
    val search = connection("")
    val cached = KeyboardInputTarget(host)
    cached.search(search)
    cached.commitText("Annax", 1)
    cached.deleteSurroundingText(1, 0)
    assertEquals("Anna", search.editable.toString())
    assertEquals("Mój szkic", host.editable.toString())
    cached.composer()
    cached.commitText("!", 1)
    assertEquals("Mój szkic!", host.editable.toString())
    assertEquals("Anna", search.editable.toString())
  }
}
