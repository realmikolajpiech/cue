package expo.modules.subtext

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.Color
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.text.TextUtils
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
  private lateinit var root: LinearLayout
  private lateinit var resultScroll: ScrollView
  private lateinit var generateButton: Button
  private var dark = false
  private val surface get() = Color.parseColor(if (dark) "#17181B" else "#E9EAED")
  private val keySurface get() = Color.parseColor(if (dark) "#34363B" else "#FFFFFF")
  private val ink get() = Color.parseColor(if (dark) "#F4F4F5" else "#1B1C20")
  private val muted get() = Color.parseColor(if (dark) "#B2B4BD" else "#5B5E68")
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
    if (::panel.isInitialized) {
      dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
      root.setBackgroundColor(surface)
      renderPanel(); renderKeys()
      ViewCompat.requestApplyInsets(root)
    }
  }
  override fun onCreateInputView(): View {
    dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(6)); setBackgroundColor(surface) }
    // The IME navigation controls can overlay its content (notably on Samsung).
    // Measure only the overlap: some Android versions already inset the parent.
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
      root.post { applySystemInsets() }
      insets
    }
    root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applySystemInsets() }
    root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(view: View) { ViewCompat.requestApplyInsets(view) }
      override fun onViewDetachedFromWindow(view: View) = Unit
    })
    panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    keys = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(panel); root.addView(keys)
    renderPanel(); renderKeys()
    return root
  }
  private fun applySystemInsets() {
    val decor = window?.window?.decorView ?: return
    val insets = ViewCompat.getRootWindowInsets(decor) ?: return
    val safe = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
    val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
    val rootPosition = IntArray(2)
    val windowPosition = IntArray(2)
    root.getLocationInWindow(rootPosition)
    decor.getLocationInWindow(windowPosition)
    val left = (windowPosition[0] + safe.left - rootPosition[0]).coerceAtLeast(0)
    val right = (rootPosition[0] + root.width - (windowPosition[0] + decor.width - safe.right)).coerceAtLeast(0)
    val bottom = (rootPosition[1] + root.height -
      (windowPosition[1] + decor.height - maxOf(safe.bottom, gestures.bottom))).coerceAtLeast(0)
    val paddingLeft = dp(4) + left
    val paddingRight = dp(4) + right
    val paddingBottom = dp(6) + bottom
    // Keep all four key rows measured at their intended size. The IME frame
    // may otherwise retain its previous height when only padding changes.
    val desiredHeight = panel.measuredHeight + dp(4 * 54) + dp(4) + paddingBottom
    root.layoutParams?.let { params ->
      if (params.height != desiredHeight) { params.height = desiredHeight; root.layoutParams = params }
    }
    if (root.paddingLeft != paddingLeft || root.paddingRight != paddingRight || root.paddingBottom != paddingBottom) {
      root.setPadding(paddingLeft, dp(4), paddingRight, paddingBottom)
    }
  }

  private fun renderPanel() {
    panel.removeAllViews()
    hint = TextView(this).apply {
      textSize = 12f; setTextColor(muted); setPadding(dp(8), dp(4), dp(8), dp(4))
      visibility = View.GONE
    }
    results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    resultScroll = ScrollView(this).apply { addView(results); visibility = View.GONE }
    if (sensitive) { hint.text = "Podpowiedzi wyłączone w tym polu"; hint.visibility = View.VISIBLE; panel.addView(hint); return }
    val packageName = currentInputEditorInfo?.packageName.orEmpty()
    val network = when (packageName) { "com.facebook.orca" -> "messenger"; "com.whatsapp", "com.whatsapp.w4b" -> "whatsapp"; else -> null }
    val rooms = runtime.store.rooms().filter { network == null || it.optString("network") == network }
    val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), dp(4)) }
    val chooser = key(if (rooms.isEmpty()) "Brak rozmów" else "Wybierz rozmowę ▾") {}.apply {
      textSize = 14f; gravity = Gravity.CENTER_VERTICAL or Gravity.START
      setPadding(dp(8), 0, dp(8), 0); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      background = null
    }
    generateButton = key("Podpowiedz") { generate() }.apply { textSize = 14f; isEnabled = false; alpha = 0.45f }
    chooser.setOnClickListener {
      val themed = android.view.ContextThemeWrapper(this, if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light)
      PopupMenu(themed, chooser).apply {
        rooms.forEachIndexed { index, room -> menu.add(0, index, index, room.optString("name")) }
        setOnMenuItemClickListener { item ->
          revision++; job?.cancel(); results.removeAllViews(); resultScroll.visibility = View.GONE
          selected = rooms[item.itemId].optString("id")
          chooser.text = rooms[item.itemId].optString("name") + " ▾"
          generateButton.isEnabled = true; generateButton.alpha = 1f; generateButton.text = "Podpowiedz"
          hint.visibility = View.GONE
          true
        }
        show()
      }
    }
    toolbar.addView(chooser, LinearLayout.LayoutParams(0, dp(44), 1f))
    toolbar.addView(generateButton, LinearLayout.LayoutParams(dp(112), dp(40)))
    panel.addView(toolbar)
    if (rooms.isEmpty()) { hint.text = "Połącz konto i pobierz rozmowy w Cue."; hint.visibility = View.VISIBLE }
    panel.addView(hint)
    panel.addView(resultScroll, LinearLayout.LayoutParams(-1, dp(96)))
  }

  private fun generate() {
    val id = selected ?: run { hint.visibility = View.VISIBLE; hint.text = "Najpierw wybierz rozmowę."; return }
    if (job?.isActive == true || sensitive) return
    val token = revision
    val input = currentInputConnection ?: return
    val draft = input.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
    hint.visibility = View.GONE; generateButton.text = "Analizuję…"; generateButton.isEnabled = false; results.removeAllViews(); resultScroll.visibility = View.GONE
    job = scope.launch {
      try {
        val profile = withContext(Dispatchers.IO) { JSONObject(runtime.analyze(id, draft)) }
        if (token != revision) return@launch
        hint.visibility = View.VISIBLE; hint.text = "Dotknij propozycji, aby wstawić"; resultScroll.visibility = View.VISIBLE
        results.addView(TextView(this@SubtextKeyboard).apply { text = profile.getString("beforeReply"); textSize = 12f; setTextColor(muted); setPadding(dp(8), dp(4), dp(8), dp(4)) })
        val suggestions = profile.getJSONArray("suggestions")
        for (i in 0 until suggestions.length()) {
          val suggestion = suggestions.getJSONObject(i).getString("text")
          results.addView(Button(this@SubtextKeyboard).apply {
            text = suggestion; isAllCaps = false; textSize = 13f; setTextColor(ink); backgroundTintList = ColorStateList.valueOf(keySurface)
            setOnClickListener {
              if (token == revision && !sensitive && selected == id) currentInputConnection?.commitText(suggestion, 1)
            }
          })
        }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (token == revision) { hint.visibility = View.VISIBLE; hint.text = error.message ?: "Nie udało się pobrać podpowiedzi." }
      } finally {
        if (token == revision) { generateButton.text = "Podpowiedz"; generateButton.isEnabled = selected != null }
      }
    }
  }
  private fun renderKeys() {
    keys.removeAllViews()
    val rows = if (symbols) listOf("1234567890", "@#%&*()-+", "!?.,:;/\"'") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    for (row in rows) {
      val line = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        if (!symbols && row == "asdfghjkl") setPadding(dp(14), 0, dp(14), 0)
        if (!symbols && row == "zxcvbnm") setPadding(dp(38), 0, dp(38), 0)
      }
      row.forEach { letter ->
        val label = if (shift) letter.uppercaseChar().toString() else letter.toString()
        line.addView(key(label) { currentInputConnection?.commitText(label, 1) }, keyParams(1f))
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
    ).forEach { (label, action) -> bottom.addView(key(label, action), keyParams(if (label == "Spacja") 3f else 1f)) }
    keys.addView(bottom)
  }
  private fun key(label: String, action: () -> Unit) = Button(this).apply {
    text = label; isAllCaps = false; textSize = if (label.length == 1) 20f else 13f; setTextColor(ink); minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
    background = RippleDrawable(ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000),
      GradientDrawable().apply { setColor(keySurface); cornerRadius = dp(8).toFloat() }, null)
    stateListAnimator = null
    setOnClickListener { action() }
    val polish = mapOf("a" to "ą", "c" to "ć", "e" to "ę", "l" to "ł", "n" to "ń", "o" to "ó", "s" to "ś", "x" to "ź", "z" to "ż")
    polish[label.lowercase()]?.let { character -> setOnLongClickListener { currentInputConnection?.commitText(if (shift) character.uppercase() else character, 1); true } }
  }
  private fun keyParams(weight: Float) = LinearLayout.LayoutParams(0, dp(48), weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
  override fun onFinishInput() { revision++; job?.cancel(); selected = null; super.onFinishInput() }
  override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
