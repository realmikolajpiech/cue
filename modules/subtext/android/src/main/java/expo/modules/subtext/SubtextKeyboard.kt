package expo.modules.subtext

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.Color
import kotlinx.coroutines.*
import org.json.JSONObject

class SubtextKeyboard : InputMethodService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var job: Job? = null
  private var revision = 0
  private var selected: String? = null
  private var sensitive = false
  private var shift = false
  private var symbols = false
  private lateinit var panel: LinearLayout
  private lateinit var results: LinearLayout
  private lateinit var keys: LinearLayout
  private lateinit var hint: TextView
  private val runtime get() = SubtextRuntime.get(this)

  override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    super.onStartInput(attribute, restarting)
    revision++; job?.cancel(); selected = null
    val type = attribute?.inputType ?: 0
    val variation = type and InputType.TYPE_MASK_VARIATION
    sensitive = (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER ||
      variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
      ((attribute?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
  }
  override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    if (::panel.isInitialized) renderPanel()
  }
  override fun onCreateInputView(): View {
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(8)); setBackgroundColor(Color.rgb(241, 243, 240)) }
    panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    keys = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(panel); root.addView(keys)
    renderPanel(); renderKeys()
    return root
  }
  private fun renderPanel() {
    panel.removeAllViews()
    hint = TextView(this).apply { textSize = 13f; setTextColor(Color.rgb(31, 47, 40)); setPadding(dp(8), dp(4), dp(8), dp(4)) }
    panel.addView(hint)
    results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    if (sensitive) { hint.text = "Subtext · podpowiedzi wyłączone w tym polu"; return }
    hint.text = "Subtext · wybierz rozmowę przed użyciem AI"
    val packageName = currentInputEditorInfo?.packageName.orEmpty()
    val network = when (packageName) { "com.facebook.orca" -> "messenger"; "com.whatsapp", "com.whatsapp.w4b" -> "whatsapp"; else -> null }
    val rooms = runtime.store.rooms().filter { network == null || it.optString("network") == network }
    val spinner = Spinner(this)
    spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
      listOf("Wybierz osobę / rozmowę…") + rooms.map { "${it.optString("name")} · ${it.optString("network")}" })
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) { selected = null }
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        revision++; job?.cancel(); results.removeAllViews()
        selected = rooms.getOrNull(position - 1)?.optString("id")
      }
    }
    panel.addView(spinner)
    val generate = Button(this).apply { text = "Podpowiedz odpowiedź"; isAllCaps = false; setOnClickListener { generate() } }
    panel.addView(generate, LinearLayout.LayoutParams(-1, dp(44)))
    val scroll = ScrollView(this).apply { addView(results) }
    panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(100)))
  }
  private fun generate() {
    val id = selected ?: run { hint.text = "Najpierw wybierz właściwego rozmówcę."; return }
    if (job?.isActive == true || sensitive) return
    val token = revision
    val input = currentInputConnection ?: return
    val draft = input.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
    hint.text = "DeepSeek analizuje wybraną rozmowę…"; results.removeAllViews()
    job = scope.launch {
      try {
        val profile = withContext(Dispatchers.IO) { JSONObject(runtime.analyze(id, draft)) }
        if (token != revision) return@launch
        hint.text = "Propozycje dla wybranej rozmowy · dotknij, aby wstawić"
        results.addView(TextView(this@SubtextKeyboard).apply { text = profile.getString("beforeReply"); textSize = 12f; setTextColor(Color.DKGRAY) })
        val suggestions = profile.getJSONArray("suggestions")
        for (i in 0 until suggestions.length()) {
          val suggestion = suggestions.getJSONObject(i).getString("text")
          results.addView(Button(this@SubtextKeyboard).apply {
            text = suggestion; isAllCaps = false; textSize = 13f
            setOnClickListener {
              if (token == revision && !sensitive && selected == id) currentInputConnection?.commitText(suggestion, 1)
            }
          })
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (token == revision) hint.text = error.message ?: "Nie udało się pobrać podpowiedzi."
      }
    }
  }
  private fun renderKeys() {
    keys.removeAllViews()
    val rows = if (symbols) listOf("1234567890", "@#%&*()-+", "!?.,:;/\"'") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    for (row in rows) {
      val line = LinearLayout(this)
      row.forEach { letter ->
        val label = if (shift) letter.uppercaseChar().toString() else letter.toString()
        line.addView(key(label) { currentInputConnection?.commitText(label, 1) }, LinearLayout.LayoutParams(0, dp(44), 1f))
      }
      keys.addView(line)
    }
    val bottom = LinearLayout(this)
    listOf<Pair<String, () -> Unit>>(
      "⇧" to { shift = !shift; renderKeys() },
      (if (symbols) "ABC" else "123") to { symbols = !symbols; renderKeys() },
      "…" to { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
      "Spacja" to { currentInputConnection?.commitText(" ", 1) },
      "⌫" to { if (!currentInputConnection?.getSelectedText(0).isNullOrEmpty()) currentInputConnection?.commitText("", 1) else currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0) },
      "↵" to { currentInputConnection?.commitText("\n", 1) }
    ).forEach { (label, action) -> bottom.addView(key(label, action), LinearLayout.LayoutParams(0, dp(44), if (label == "Spacja") 2f else 1f)) }
    keys.addView(bottom)
  }
  private fun key(label: String, action: () -> Unit) = Button(this).apply {
    text = label; isAllCaps = false; textSize = 15f; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
    setOnClickListener { action() }
    val polish = mapOf("a" to "ą", "c" to "ć", "e" to "ę", "l" to "ł", "n" to "ń", "o" to "ó", "s" to "ś", "x" to "ź", "z" to "ż")
    polish[label.lowercase()]?.let { character -> setOnLongClickListener { currentInputConnection?.commitText(if (shift) character.uppercase() else character, 1); true } }
  }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
  override fun onFinishInput() { revision++; job?.cancel(); selected = null; super.onFinishInput() }
  override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
