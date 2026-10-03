package expo.modules.subtext

import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardInputTargetTest {
  @Test fun goalEditorSupportsCompositionAndReplacingSelectionWithoutChangingDraft() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
      val host = connection("Mój szkic")
      val editor = EditText(instrumentation.targetContext).apply {
        setText("spotkanie jutro"); setSelection(text.length)
      }
      val goal = requireNotNull(editor.onCreateInputConnection(EditorInfo()))
      val cached = KeyboardInputTarget(host)
      cached.search(goal)
      cached.setSelection(10, 15)
      cached.setComposingText("dzi", 1)
      cached.setComposingText("dziś", 1)
      cached.finishComposingText()
      assertEquals("spotkanie dziś", editor.text.toString())
      cached.commitText("\nbez pośpiechu", 1)
      assertEquals("spotkanie dziś\nbez pośpiechu", editor.text.toString())
      assertEquals("Mój szkic", host.editable.toString())
      cached.composer()
      cached.commitText("!", 1)
      assertEquals("Mój szkic!", host.editable.toString())
      assertEquals("spotkanie dziś\nbez pośpiechu", editor.text.toString())
    }
  }
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
