package expo.modules.subtext

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

internal object KeyboardDraftEditor {
  fun read(input: InputConnection): String? {
    val extracted = input.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 5000; hintMaxLines = 100 }, 0) ?: return null
    if (extracted.startOffset != 0 || extracted.partialStartOffset >= 0) return null
    val text = extracted.text?.toString() ?: return null
    if (text.length > 4000) return null
    // A partial editor response must never be mistaken for the whole draft.
    val start = minOf(extracted.selectionStart, extracted.selectionEnd).coerceIn(0, text.length)
    val end = maxOf(extracted.selectionStart, extracted.selectionEnd).coerceIn(start, text.length)
    val before = input.getTextBeforeCursor(5000, 0)?.toString() ?: return null
    val after = input.getTextAfterCursor(5000, 0)?.toString() ?: return null
    if (before != text.substring(0, start) || after != text.substring(end)) return null
    return text
  }

  fun replace(input: InputConnection, expected: String, replacement: String): Boolean {
    if (read(input) != expected) return false
    input.beginBatchEdit()
    return try {
      input.finishComposingText()
      if (!input.setSelection(0, expected.length)) false else input.commitText(replacement, 1)
    } finally { input.endBatchEdit() }
  }
}
