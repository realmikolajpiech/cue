package expo.modules.subtext

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService.Insets
import android.text.TextUtils
import android.text.Editable
import android.text.TextWatcher
import android.text.Selection
import android.text.InputType
import android.view.inputmethod.InputConnection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.R as KeyboardR
import kotlinx.coroutines.*
import org.json.JSONObject

class SubtextKeyboard : LatinIME() {
  private enum class Mode { TYPING, PEOPLE, SEARCH, LOADING, REPLIES, ERROR }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var job: Job? = null
  private var revision = 0
  private var selected: String? = null
  private var mode = Mode.TYPING
  private var available = false
  private var bodyHeight = 0
  private var backHandled = false
  private var errorMessage = ""
  private var replies = emptyList<JSONObject>()
  private var draft = ""
  private var undo: Pair<String, String>? = null
  private lateinit var panel: LinearLayout
  private var keyboardFrame: LinearLayout? = null
  private var pickerMotion: KeyboardPickerMotion? = null
  private var toolbarMotion: KeyboardToolbarMotion? = null
  private val panelMotion = KeyboardPanelMotion()
  private val hiddenKeys = linkedMapOf<View, Int>()
  private var pickerHeight = 0
  private var inputTarget: KeyboardInputTarget? = null
  private var searchEditor: EditText? = null
  private var searchConnection: InputConnection? = null
  private var searchRooms = emptyList<JSONObject>()
  private var searchResults = emptyList<JSONObject>()
  private var peopleAdapter: BaseAdapter? = null
  private var noResults: TextView? = null
  private var strip: View? = null
  private var stripVisibility = View.VISIBLE
  private var generateAfterChoice = false
  private val consumedHardwareKeys = mutableSetOf<Int>()
  private val runtime get() = SubtextRuntime.get(this)
  private val surface get() = Settings.getValues()?.mColors?.get(ColorType.MAIN_BACKGROUND) ?: getColor(R.color.cue_login_background)
  private val ink get() = Settings.getValues()?.mColors?.get(ColorType.KEY_TEXT) ?: getColor(R.color.cue_login_text)
  private val dark get() = Color.luminance(surface) < 0.3
  private val muted get() = Color.parseColor(if (dark) "#B9C2DD" else "#606982")
  private val card get() = Color.parseColor(if (dark) "#292C38" else "#FFFFFF")
  private val accent get() = Color.parseColor(if (dark) "#A7B8FF" else "#4563AB")
  private val onAccent get() = Color.parseColor(if (dark) "#1B2442" else "#FFFFFF")

  override fun onCreate() {
    super.onCreate()
    if (runtime.prefs.getBoolean("background", false)) {
      runtime.restore()
      runCatching { runtime.startConnections() }
    }
  }

  override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    // A new editor is a new recipient decision, even inside the same messaging app.
    revision++; job?.cancel()
    if (!restarting) { selected = null; undo = null }
    endSearch(); restoreKeys()
    mode = Mode.TYPING
    available = attribute != null && KeyboardReplySession.available(attribute.packageName.orEmpty(), attribute.inputType, attribute.imeOptions)
    super.onStartInput(attribute, restarting)
  }

  override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    if (::panel.isInitialized) render()
  }

  override fun onCreateInputView(): View = super.onCreateInputView().also(::attach)
  override fun setInputView(view: View) { attach(view); super.setInputView(view) }

  private fun attach(view: View) {
    val next = view.findViewById<LinearLayout>(KeyboardR.id.main_keyboard_frame) ?: return
    if (next.findViewWithTag<View>("cue_suggestions") != null) return
    bodyHeight = dp(if (mode == Mode.PEOPLE) 136 else 124)
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    pickerMotion = KeyboardPickerMotion(next)
    toolbarMotion = KeyboardToolbarMotion(next)
    keyboardFrame = next
    hiddenKeys.clear()
    strip = next.findViewById(KeyboardR.id.strip_container)
    panel = LinearLayout(this).apply { tag = "cue_suggestions"; orientation = LinearLayout.VERTICAL }
    next.addView(panel, 0, LinearLayout.LayoutParams(-1, -2))
    render()
  }

  override fun onComputeInsets(outInsets: Insets) {
    super.onComputeInsets(outInsets)
    if (!::panel.isInitialized || !panel.isShown) return
    val position = IntArray(2); panel.getLocationInWindow(position)
    outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
    outInsets.touchableRegion.union(Rect(position[0], position[1], position[0] + panel.width, position[1] + panel.height))
    outInsets.contentTopInsets = minOf(outInsets.contentTopInsets, position[1])
    outInsets.visibleTopInsets = minOf(outInsets.visibleTopInsets, position[1])
  }

  private fun rooms() = KeyboardReplySession.rooms(runtime.store.summaries(), currentInputEditorInfo?.packageName.orEmpty())
  private fun person() = rooms().firstOrNull { it.optString("id") == selected }

  private fun restoreKeys() {
    hiddenKeys.forEach { (view, visibility) -> view.visibility = visibility }
    hiddenKeys.clear()
  }

  private fun open(next: Mode) {
    val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
    val returningKeys = mode == Mode.PEOPLE && next != Mode.PEOPLE
    val snapshot = if (next == Mode.PEOPLE) pickerMotion?.capture() else null
    if (next == Mode.PEOPLE) {
      revision++; job?.cancel()
      currentInputConnection?.finishComposingText()
      if (mode != Mode.SEARCH) {
        searchRooms = rooms()
        stripVisibility = strip?.visibility ?: View.VISIBLE
      }
      endSearch()
      val frame = keyboardFrame
      pickerHeight = frame?.let { parent -> (0 until parent.childCount).map(parent::getChildAt)
        .filter { it !== panel && it.visibility == View.VISIBLE }.sumOf { it.height } } ?: dp(280)
      if (pickerHeight <= 0) pickerHeight = dp(280)
      frame?.let { parent -> for (i in 0 until parent.childCount) {
        val child = parent.getChildAt(i)
        if (child !== panel) { hiddenKeys[child] = child.visibility; child.visibility = View.GONE }
      } }
    } else if (next == Mode.SEARCH) {
      restoreKeys()
      strip?.visibility = View.GONE
    }
    bodyHeight = dp(148)
    mode = next; render(animate = next != Mode.PEOPLE && next != Mode.SEARCH && !returningKeys)
    toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
    if (next == Mode.PEOPLE) pickerMotion?.disappear(snapshot, panel.getChildAt(1))
    else if (returningKeys) pickerMotion?.appear()
  }

  private fun typing() {
    val returningKeys = mode == Mode.PEOPLE
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
    revision++; job?.cancel(); endSearch(); restoreKeys(); mode = Mode.TYPING; render(animate = !returningKeys)
    toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
    if (returningKeys) pickerMotion?.appear()
  }

  private fun render(animate: Boolean = false) {
    if (!::panel.isInitialized) return
    val previousHeight = panel.height
    panelMotion.cancel()
    renderContent()
    if (animate && available) panelMotion.resize(panel, previousHeight)
  }

  private fun renderContent() {
    panel.removeAllViews(); panel.setBackgroundColor(surface)
    panel.visibility = if (available) View.VISIBLE else View.GONE
    if (!available) return
    strip?.visibility = if (mode == Mode.PEOPLE || mode == Mode.SEARCH) View.GONE else stripVisibility
    if (mode == Mode.PEOPLE) { renderPicker(); return }
    if (mode == Mode.SEARCH) { renderPeople(); return }
    val toolbar = brandToolbar()
    if (mode == Mode.TYPING || mode == Mode.REPLIES || mode == Mode.LOADING) {
      val room = person()
      toolbar.addView(button((room?.optString("name") ?: "Wybierz rozmowę") + " ▾", false) { undo = null; generateAfterChoice = false; open(Mode.PEOPLE) }.apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(8), 0, dp(8), 0); contentDescription = "Rozmowa: ${room?.optString("name") ?: "nie wybrano"}. Zmień rozmowę"
      }, LinearLayout.LayoutParams(0, dp(48), 1f))
      val action = undo
      if (mode == Mode.LOADING) {
        toolbar.addView(button("Anuluj", false) { typing() }, LinearLayout.LayoutParams(dp(124), dp(44)))
      } else toolbar.addView(button(if (mode == Mode.REPLIES) { if (replies[replyIndex].optString("action") == "no_reply") "Gotowe" else if (draft.isEmpty()) "Wstaw" else "Zastąp szkic" } else if (action != null) "Cofnij" else "Pomóż odpisać", true) {
        if (mode == Mode.REPLIES) {
          val reply = replies[replyIndex]
          if (reply.optString("action") == "no_reply") typing() else insert(reply.getString("text"))
        } else if (action != null) undoInsert(action) else if (room == null) { generateAfterChoice = true; open(Mode.PEOPLE) } else generate()
      }, LinearLayout.LayoutParams(dp(124), dp(44)))
    } else {
      toolbar.addView(label(person()?.optString("name") ?: "Cue", 13f, true).apply {
        setPadding(dp(8), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      }, LinearLayout.LayoutParams(0, dp(44), 1f))
      toolbar.addView(button("Zamknij", false) { typing() }, LinearLayout.LayoutParams(dp(124), dp(44)))
    }
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    if (mode == Mode.TYPING) return
    if (mode == Mode.LOADING) {
      val loading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(16), dp(8)) }
      loading.addView(ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(accent) }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(10) })
      loading.addView(label("Układam odpowiedź…", 14f).apply { setTextColor(muted) })
      panel.addView(loading, LinearLayout.LayoutParams(-1, dp(36)))
      return
    }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(8)) }
    panel.addView(body, LinearLayout.LayoutParams(-1, bodyHeight))
    when (mode) {
      Mode.PEOPLE, Mode.SEARCH -> Unit
      Mode.LOADING -> Unit
      Mode.ERROR -> {
        val center = center(body)
        center.addView(label(errorMessage, 15f).apply { gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(16), dp(16)) })
        val cloud = runtime.prefs.getBoolean("cloud", false)
        center.addView(button(if (cloud) "Spróbuj ponownie" else "Otwórz Cue", true) { if (cloud) generate() else openApp() }, LinearLayout.LayoutParams(-1, dp(48)))
      }
      Mode.REPLIES -> showReplies(body)
      else -> Unit
    }
    panel.requestLayout()
  }

  override fun getCurrentInputConnection(): InputConnection? {
    val host = super.getCurrentInputConnection() ?: return null
    val target = inputTarget?.takeIf { it.host === host } ?: KeyboardInputTarget(host).also { inputTarget = it }
    if (mode == Mode.SEARCH) searchConnection?.let(target::search) else target.composer()
    return target
  }

  private fun endSearch() {
    val wasSearching = mode == Mode.SEARCH
    inputTarget?.composer()
    searchConnection = null; searchEditor = null; peopleAdapter = null; noResults = null
    strip?.visibility = stripVisibility
    if (wasSearching) {
      val position = inputTarget?.host?.getExtractedText(ExtractedTextRequest(), 0)
      if (position != null) super.onUpdateSelection(-1, -1, position.selectionStart, position.selectionEnd, -1, -1)
    }
  }

  private fun renderPicker() {
    val toolbar = brandToolbar()
    toolbar.addView(button("Wybierz rozmowę ▴", false) { typing() }.apply {
      gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      contentDescription = "Zamknij wybór rozmowy i wróć do pisania"
    }, LinearLayout.LayoutParams(0, dp(48), 1f))
    toolbar.addView(button("Szukaj", false) { open(Mode.SEARCH) }, LinearLayout.LayoutParams(dp(124), dp(44)))
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    val list = ListView(this).apply {
      divider = null; isVerticalScrollBarEnabled = true
      val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
      setPadding(dp(12), 0, dp(12), navigation + dp(8)); clipToPadding = false
    }
    list.adapter = object : BaseAdapter() {
      override fun getCount() = searchRooms.size
      override fun getItem(position: Int) = searchRooms[position]
      override fun getItemId(position: Int) = position.toLong()
      override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val room = searchRooms[position]
        val row = (convertView as? LinearLayout) ?: LinearLayout(this@SubtextKeyboard).apply {
          gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0)
          addView(label("", 15f, true).apply { gravity = Gravity.CENTER; setTextColor(accent); background = rounded(card, 18) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
          addView(label("", 16f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
          addView(label("", 20f).apply { setTextColor(accent) })
          layoutParams = AbsListView.LayoutParams(-1, dp(52))
        }
        (row.getChildAt(0) as TextView).text = room.optString("name").trim().take(1)
        (row.getChildAt(1) as TextView).text = room.optString("name")
        (row.getChildAt(2) as TextView).text = if (room.optString("id") == selected) "✓" else ""
        return row
      }
    }
    list.setOnItemClickListener { _, _, position, _ -> choosePerson(searchRooms[position]) }
    if (searchRooms.isEmpty()) {
      panel.addView(button("Sprawdź rozmowy w Cue", false) { openApp() }, LinearLayout.LayoutParams(-1, pickerHeight))
    } else panel.addView(list, LinearLayout.LayoutParams(-1, pickerHeight))
  }

  private fun renderPeople() {
    val oldQuery = searchEditor?.text?.toString().orEmpty()
    val toolbar = brandToolbar()
    val editor = EditText(this).apply {
      setSingleLine(); textSize = 15f; setTextColor(ink); setHintTextColor(muted)
      hint = "Szukaj osoby…"; contentDescription = "Szukaj osoby, wpisując na klawiaturze"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      isFocusable = false; showSoftInputOnFocus = false
      background = rounded(card, 12); setPadding(dp(12), 0, dp(12), 0)
      setText(oldQuery); setSelection(text.length)
    }
    searchEditor = editor
    toolbar.addView(editor, LinearLayout.LayoutParams(0, dp(44), 1f))
    toolbar.addView(button("Wróć do listy", false) { open(Mode.PEOPLE) }, LinearLayout.LayoutParams(dp(124), dp(44)))
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    searchConnection = object : BaseInputConnection(editor, true) {
      override fun getEditable(): Editable = editor.editableText
      override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
        text = editor.text.toString(); startOffset = 0; partialStartOffset = -1; partialEndOffset = -1
        selectionStart = Selection.getSelectionStart(editor.text); selectionEnd = Selection.getSelectionEnd(editor.text)
      }
      override fun performEditorAction(actionCode: Int): Boolean { chooseOnlyResult(); return true }
    }
    currentInputConnection // Switch the cached input target before accepting any key.
    searchResults = KeyboardPersonSearch.filter(searchRooms, oldQuery)
    val body = FrameLayout(this).apply { setPadding(dp(12), 0, dp(12), dp(4)) }
    val list = ListView(this).apply { divider = null; isVerticalScrollBarEnabled = true }
    val adapter = object : BaseAdapter() {
      override fun getCount() = searchResults.size
      override fun getItem(position: Int) = searchResults[position]
      override fun getItemId(position: Int) = position.toLong()
      override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val room = searchResults[position]
        val row = (convertView as? LinearLayout) ?: LinearLayout(this@SubtextKeyboard).apply {
          gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0)
          addView(label("", 14f, true).apply { gravity = Gravity.CENTER; setTextColor(accent); background = rounded(card, 14) }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
          addView(label("", 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
          addView(label("", 18f).apply { setTextColor(accent) })
          layoutParams = AbsListView.LayoutParams(-1, dp(44))
        }
        (row.getChildAt(0) as TextView).text = room.optString("name").trim().take(1)
        (row.getChildAt(1) as TextView).text = room.optString("name")
        (row.getChildAt(2) as TextView).text = if (room.optString("id") == selected) "✓" else ""
        row.contentDescription = room.optString("name")
        return row
      }
    }
    peopleAdapter = adapter; list.adapter = adapter
    list.setOnItemClickListener { _, _, position, _ -> choosePerson(searchResults[position]) }
    body.addView(list, FrameLayout.LayoutParams(-1, -1))
    val empty = label("", 14f).apply { gravity = Gravity.CENTER; setTextColor(muted) }
    noResults = empty; body.addView(empty, FrameLayout.LayoutParams(-1, -1))
    panel.addView(body, LinearLayout.LayoutParams(-1, dp(136)))
    if (searchRooms.isEmpty()) {
      empty.text = "Sprawdź rozmowy w Cue"; empty.setOnClickListener { openApp() }
    }
    fun update() {
      searchResults = KeyboardPersonSearch.filter(searchRooms, editor.text.toString())
      empty.visibility = if (searchResults.isEmpty()) View.VISIBLE else View.GONE
      if (searchRooms.isNotEmpty()) empty.text = "Nie ma takiej osoby"
      adapter.notifyDataSetChanged(); list.setSelection(0)
    }
    editor.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }
      override fun afterTextChanged(s: Editable?) = Unit
    })
    update()
    panel.announceForAccessibility("Wyszukaj osobę. Klawiatura wpisuje teraz imię, nie wiadomość.")
  }

  private fun choosePerson(room: JSONObject) {
    selected = room.optString("id"); undo = null
    val shouldGenerate = generateAfterChoice
    typing()
    if (shouldGenerate) generate()
  }
  private fun chooseOnlyResult() { if (searchResults.size == 1) choosePerson(searchResults.single()) }

  override fun onEvent(event: Event) {
    if (mode != Mode.SEARCH) { super.onEvent(event); return }
    when {
      event.keyCode == KeyCode.DELETE -> searchConnection?.deleteSurroundingText(1, 0)
      event.codePoint == 10 || event.keyCode == KeyCode.SHIFT_ENTER -> chooseOnlyResult()
      event.codePoint >= 32 -> searchConnection?.commitText(String(Character.toChars(event.codePoint)), 1)
      !event.text.isNullOrEmpty() -> searchConnection?.commitText(event.text, 1)
      event.keyCode in setOf(KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.SYMBOL_ALPHA, KeyCode.ALPHA, KeyCode.SYMBOL) -> super.onEvent(event)
      // Other editing shortcuts remain contained in the internal search editor.
    }
  }
  override fun onTextInput(rawText: String?) {
    if (mode == Mode.SEARCH) searchConnection?.commitText(rawText.orEmpty(), 1) else super.onTextInput(rawText)
  }

  private fun readDraft(): String? = currentInputConnection?.let(KeyboardDraftEditor::read)

  private fun generate() {
    val id = selected ?: return open(Mode.PEOPLE)
    if (job?.isActive == true || !available) return
    val snapshot = readDraft()
    if (snapshot == null) { errorMessage = "Nie mogę odczytać tego szkicu. Wróć do pisania i spróbuj ponownie."; open(Mode.ERROR); return }
    draft = snapshot; undo = null
    val token = revision
    open(Mode.LOADING)
    job = scope.launch {
      try {
        val profile = withContext(Dispatchers.IO) { JSONObject(runtime.analyze(id, snapshot)) }
        if (token != revision) return@launch
        val suggestions = profile.getJSONArray("suggestions")
        replies = (0 until suggestions.length()).map { suggestions.getJSONObject(it) }
          .filter { it.optString("action") == "no_reply" || it.optString("text").isNotBlank() }
        if (replies.isEmpty()) throw IllegalStateException("Brak propozycji")
        replyIndex = 0; open(Mode.REPLIES)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (token == revision) {
          errorMessage = if (!runtime.prefs.getBoolean("cloud", false)) "Włącz podpowiedzi AI w ustawieniach Cue." else "Nie udało się przygotować odpowiedzi. Spróbuj jeszcze raz."
          open(Mode.ERROR)
        }
      }
    }
  }

  private var replyIndex = 0
  private fun showReplies(body: LinearLayout) {
    replyIndex = replyIndex.coerceIn(0, replies.lastIndex)
    val suggestion = replies[replyIndex]
    val noReply = suggestion.optString("action") == "no_reply"
    val scroll = ScrollView(this).apply { isFillViewport = false; isVerticalScrollBarEnabled = true }
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
    }
    if (noReply) {
      content.addView(label("Nie musisz teraz odpisywać", 16f, true))
      content.addView(label(suggestion.optString("reason").ifBlank { "Możesz wrócić do tej rozmowy później." }, 14f).apply {
        setTextColor(muted); setPadding(0, dp(6), 0, 0); setLineSpacing(dp(2).toFloat(), 1f)
      })
    } else {
      content.background = rounded(card, 12)
      content.setPadding(dp(12), dp(10), dp(12), dp(10))
      content.addView(label(suggestion.getString("text"), 15f).apply { setLineSpacing(dp(2).toFloat(), 1f) })
    }
    scroll.addView(content)
    body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    // One recommendation needs no selector that repeats the same recommendation.
    if (replies.size > 1 || !noReply) {
      val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
      val options = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
      if (replies.size > 1) replies.forEachIndexed { index, reply ->
        options.addView(button(if (reply.optString("action") == "no_reply") "Bez odpowiedzi" else reply.optString("tone").ifBlank { "${index + 1}" }, false) {
          val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
          replyIndex = index; render()
          toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
        }.apply {
          maxLines = 1; setTextColor(if (index == replyIndex) accent else muted)
          background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(if (index == replyIndex) card else Color.TRANSPARENT, 10), null)
        }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(4) })
      }
      options.addView(button("↻", false) { generate() }.apply { contentDescription = "Inne propozycje" }, LinearLayout.LayoutParams(dp(44), dp(44)))
      scroller.addView(options)
      val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
      footer.addView(scroller, LinearLayout.LayoutParams(0, dp(44), 1f))
      footer.addView(button("×", false) { typing() }.apply {
        textSize = 22f; contentDescription = "Zamknij podpowiedzi i wróć do pisania"
      }, LinearLayout.LayoutParams(dp(44), dp(44)))
      body.addView(footer)
    }
  }

  private fun replace(expected: String, text: String): Boolean = available &&
    currentInputConnection?.let { KeyboardDraftEditor.replace(it, expected, text) } == true

  private fun insert(text: String) {
    if (!replace(draft, text)) {
      errorMessage = "Szkic się zmienił. Przygotuj nowe propozycje, żeby go nie nadpisać."; mode = Mode.ERROR; render(); return
    }
    undo = text to draft
    typing()
    panel.announceForAccessibility("Wstawiono odpowiedź. Możesz ją edytować lub cofnąć.")
  }

  private fun undoInsert(action: Pair<String, String>) {
    if (!replace(action.first, action.second)) {
      undo = null; render(); panel.announceForAccessibility("Tekst się zmienił. Nie został nadpisany."); return
    }
    undo = null; render()
  }

  private fun openApp() {
    typing()
    packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { startActivity(it) }
  }

  private fun center(body: LinearLayout) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0)
    body.addView(this, LinearLayout.LayoutParams(-1, 0, 1f))
  }
  private fun brandToolbar() = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4))
    addView(mascot(32), LinearLayout.LayoutParams(dp(32), dp(32)).apply {
      marginStart = dp(4); marginEnd = dp(4)
    })
  }
  private fun mascot(size: Int) = ImageView(this).apply {
    setImageResource(R.drawable.cue_brand); contentDescription = "Cue"; scaleType = ImageView.ScaleType.CENTER_CROP
    background = rounded(accent, size / 3); clipToOutline = true
  }
  private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(ink); includeFontPadding = false
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
  }
  private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
  private fun button(text: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; textSize = 13f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setTextColor(if (primary) onAccent else ink)
    minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; setPadding(dp(8), 0, dp(8), 0)
    background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(if (primary) accent else Color.TRANSPARENT, 12), null)
    stateListAnimator = null; setOnClickListener { action() }
  }
  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

  override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
    if (mode == Mode.SEARCH) return
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    if ((mode == Mode.LOADING || mode == Mode.REPLIES) && readDraft()?.let { it != draft } == true) typing()
    if (undo != null && readDraft()?.let { it != undo?.first } == true) { undo = null; render() }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && mode != Mode.TYPING) { backHandled = true; if (mode == Mode.SEARCH) open(Mode.PEOPLE) else typing(); return true }
    if (mode == Mode.SEARCH) {
      consumedHardwareKeys.add(keyCode)
      when (keyCode) {
        KeyEvent.KEYCODE_DEL -> searchConnection?.deleteSurroundingText(1, 0)
        KeyEvent.KEYCODE_ENTER -> chooseOnlyResult()
        else -> if (event.unicodeChar >= 32) searchConnection?.commitText(String(Character.toChars(event.unicodeChar)), 1)
      }
      return true
    }
    return super.onKeyDown(keyCode, event)
  }
  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && backHandled) { backHandled = false; return true }
    if (consumedHardwareKeys.remove(keyCode)) return true
    return super.onKeyUp(keyCode, event)
  }
  override fun onFinishInputView(finishingInput: Boolean) {
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    revision++; job?.cancel(); endSearch(); restoreKeys(); mode = Mode.TYPING
    super.onFinishInputView(finishingInput)
  }
  override fun onFinishInput() { pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel(); revision++; job?.cancel(); selected = null; undo = null; endSearch(); restoreKeys(); mode = Mode.TYPING; super.onFinishInput() }
  override fun onDestroy() { pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel(); scope.cancel(); super.onDestroy() }
}
