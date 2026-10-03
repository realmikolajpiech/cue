package expo.modules.subtext

import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardDraftEditorTest {
  private fun editor(text: String, start: Int = text.length, end: Int = start): BaseInputConnection {
    val draft = SpannableStringBuilder(text).apply { Selection.setSelection(this, start, end) }
    return object : BaseInputConnection(View(InstrumentationRegistry.getInstrumentation().targetContext), true) {
      override fun getEditable(): Editable = draft
      override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText = ExtractedText().apply {
        this.text = draft.toString(); startOffset = 0; partialStartOffset = -1; partialEndOffset = -1
        selectionStart = Selection.getSelectionStart(draft); selectionEnd = Selection.getSelectionEnd(draft)
      }
    }
  }

  @Test fun replacesWholeDraftIncludingBothSidesOfTheCursorAndSelectedText() {
    val input = editor("Hej 😊 to mój szkic", 4, 6)
    assertEquals("Hej 😊 to mój szkic", KeyboardDraftEditor.read(input))
    assertTrue(KeyboardDraftEditor.replace(input, "Hej 😊 to mój szkic", "Nowa odpowiedź"))
    assertEquals("Nowa odpowiedź", input.editable.toString())
    assertTrue(input.commitText("!", 1))
    assertEquals("Nowa odpowiedź!", input.editable.toString())
  }

  @Test fun editedDraftIsNeverOverwrittenByAnOlderSuggestionOrUndo() {
    val input = editor("Szkic zmieniony")
    assertFalse(KeyboardDraftEditor.replace(input, "Szkic", "Sugestia"))
    assertEquals("Szkic zmieniony", input.editable.toString())
  }

  @Test fun undoRestoresDraftButOnlyUntilTheUserEditsAgain() {
    val input = editor("Mój szkic")
    assertTrue(KeyboardDraftEditor.replace(input, "Mój szkic", "Odpowiedź"))
    assertTrue(KeyboardDraftEditor.replace(input, "Odpowiedź", "Mój szkic"))
    assertEquals("Mój szkic", input.editable.toString())
    assertTrue(KeyboardDraftEditor.replace(input, "Mój szkic", "Odpowiedź"))
    input.commitText("!", 1)
    assertFalse(KeyboardDraftEditor.replace(input, "Odpowiedź", "Mój szkic"))
    assertEquals("Odpowiedź!", input.editable.toString())
  }
}
