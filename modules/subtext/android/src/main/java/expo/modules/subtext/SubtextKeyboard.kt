package expo.modules.subtext

import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.R as KeyboardR
import android.text.InputType
import android.view.View
import android.inputmethodservice.InputMethodService.Insets
import android.graphics.Rect
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.graphics.Color
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.text.TextUtils
import kotlinx.coroutines.*
import org.json.JSONObject

class SubtextKeyboard : LatinIME() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var job: Job? = null
  private var revision = 0
  private var selected: String? = null
  private var sensitive = false
  private lateinit var resultScroll: LinearLayout
  private lateinit var generateButton: Button
  private var dark = false
  private val surface get() = Settings.getValues()?.mColors?.get(ColorType.MAIN_BACKGROUND) ?: Color.parseColor(if (dark) "#17181B" else "#E9EAED")
  private val keySurface get() = Settings.getValues()?.mColors?.get(ColorType.KEY_BACKGROUND) ?: Color.parseColor(if (dark) "#34363B" else "#FFFFFF")
  private val ink get() = Settings.getValues()?.mColors?.get(ColorType.KEY_TEXT) ?: Color.parseColor(if (dark) "#F4F4F5" else "#1B1C20")
  private val muted get() = Settings.getValues()?.mColors?.get(ColorType.SUGGESTION_TYPED_WORD) ?: Color.parseColor(if (dark) "#B2B4BD" else "#5B5E68")
  private lateinit var panel: LinearLayout
  private lateinit var results: LinearLayout
  private lateinit var hint: TextView
  private val runtime get() = SubtextRuntime.get(this)

  override fun onCreate() {
    super.onCreate()
    // The IME can start in a fresh process without opening the Expo activity.
    if (runtime.prefs.getBoolean("background", false)) {
      runtime.restore()
      runCatching { runtime.startConnections() }
    }
  }

  override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    revision++; job?.cancel(); selected = null
    val type = attribute?.inputType ?: 0
    val variation = type and InputType.TYPE_MASK_VARIATION
    sensitive = (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER ||
      variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
      ((attribute?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
    super.onStartInput(attribute, restarting)
  }
  override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    if (::panel.isInitialized) {
      dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
      panel.setBackgroundColor(surface)
      renderPanel()
    }
  }
  override fun onCreateInputView(): View = super.onCreateInputView().also(::attachCuePanel)

  // HeliBoard also replaces its view directly when the theme or orientation changes.
  override fun setInputView(view: View) {
    attachCuePanel(view)
    super.setInputView(view)
  }

  override fun onComputeInsets(outInsets: Insets) {
    super.onComputeInsets(outInsets)
    if (!::panel.isInitialized || !panel.isShown) return
    // Upstream's region starts at its spelling strip; include Cue's panel above it.
    val position = IntArray(2)
    panel.getLocationInWindow(position)
    outInsets.touchableRegion.union(Rect(position[0], position[1], position[0] + panel.width, position[1] + panel.height))
    outInsets.contentTopInsets = minOf(outInsets.contentTopInsets, position[1])
    outInsets.visibleTopInsets = minOf(outInsets.visibleTopInsets, position[1])
  }

  private fun attachCuePanel(view: View) {
    val frame = view.findViewById<LinearLayout>(KeyboardR.id.main_keyboard_frame) ?: return
    if (frame.findViewWithTag<View>("cue_suggestions") != null) return
    dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    panel = LinearLayout(this).apply {
      tag = "cue_suggestions"
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(surface)
      setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    frame.addView(panel, 0, LinearLayout.LayoutParams(-1, -2))
    renderPanel()
  }

  private fun renderPanel() {
    panel.removeAllViews()
    hint = TextView(this).apply {
      textSize = 12f; setTextColor(muted); setPadding(dp(8), dp(4), dp(8), dp(4))
      maxLines = 2; ellipsize = TextUtils.TruncateAt.END
      visibility = View.GONE
    }
    results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    resultScroll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
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
    panel.addView(resultScroll, LinearLayout.LayoutParams(-1, dp(116)))
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
        hint.visibility = View.GONE
        val suggestions = profile.getJSONArray("suggestions")
        val replies = (0 until suggestions.length()).map { suggestions.getJSONObject(it).getString("text") }.filter { it.isNotBlank() }
        if (replies.isEmpty()) { hint.text = "Brak propozycji. Spróbuj ponownie."; hint.visibility = View.VISIBLE }
        else showReplies(replies, id, token)

      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (token == revision) { hint.visibility = View.VISIBLE; hint.text = error.message ?: "Nie udało się pobrać podpowiedzi." }
      } finally {
        if (token == revision) { generateButton.text = "Podpowiedz"; generateButton.isEnabled = selected != null }
      }
    }
  }
  private fun showReplies(replies: List<String>, id: String, token: Int) {
    resultScroll.removeAllViews()
    resultScroll.visibility = View.VISIBLE
    var index = 0
    val reply = TextView(this).apply {
      textSize = 15f; setTextColor(ink); setPadding(dp(12), dp(8), dp(12), dp(8))
      setLineSpacing(dp(2).toFloat(), 1f)
    }
    // Only the answer scrolls. The keyboard and controls keep their own height.
    val textScroll = ScrollView(this).apply {
      isFillViewport = true
      background = GradientDrawable().apply { setColor(keySurface); cornerRadius = dp(8).toFloat() }
      addView(reply, FrameLayout.LayoutParams(-1, -2))
    }
    resultScroll.addView(textScroll, LinearLayout.LayoutParams(-1, dp(72)))
    val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    val previous = key("‹") {}; previous.contentDescription = "Poprzednia odpowiedź"
    val next = key("›") {}; next.contentDescription = "Następna odpowiedź"
    val count = TextView(this).apply { textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER }
    val insert = key("Wstaw") {
      if (token == revision && !sensitive && selected == id) {
        onTextInput(replies[index]); resultScroll.visibility = View.GONE
      }
    }
    fun update() {
      reply.text = replies[index]
      count.text = "${index + 1} / ${replies.size}"
      previous.isEnabled = index > 0; previous.alpha = if (index > 0) 1f else 0.35f
      next.isEnabled = index < replies.lastIndex; next.alpha = if (index < replies.lastIndex) 1f else 0.35f
      textScroll.scrollTo(0, 0)
    }
    previous.setOnClickListener { if (index > 0) { index--; update() } }
    next.setOnClickListener { if (index < replies.lastIndex) { index++; update() } }
    controls.addView(previous, LinearLayout.LayoutParams(dp(44), dp(40)))
    controls.addView(count, LinearLayout.LayoutParams(dp(48), dp(40)))
    controls.addView(next, LinearLayout.LayoutParams(dp(44), dp(40)))
    controls.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
    controls.addView(insert, LinearLayout.LayoutParams(dp(88), dp(40)))
    resultScroll.addView(controls, LinearLayout.LayoutParams(-1, dp(44)))
    update()
    panel.requestLayout()
  }

  private fun key(label: String, action: () -> Unit) = Button(this).apply {
    text = label; isAllCaps = false; textSize = if (label.length == 1) 20f else 13f; setTextColor(ink); minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
    background = RippleDrawable(ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000),
      GradientDrawable().apply { setColor(keySurface); cornerRadius = dp(8).toFloat() }, null)
    stateListAnimator = null
    setOnClickListener { action() }
  }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
  override fun onFinishInputView(finishingInput: Boolean) {
    revision++; job?.cancel()
    super.onFinishInputView(finishingInput)
  }
  override fun onFinishInput() { revision++; job?.cancel(); selected = null; super.onFinishInput() }
  override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
