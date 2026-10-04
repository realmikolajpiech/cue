package expo.modules.subtext

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
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
  private enum class Mode { TYPING, PEOPLE, STYLES, SEARCH, GOAL, LOADING, REPLIES, ERROR }
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
  private var headerExpanded = false
  private var chevronAnimator: android.animation.ValueAnimator? = null
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
  private var goalAfterChoice = false
  private var goalSelectionStart = -1
  private var goalSelectionEnd = -1
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
    if (!restarting) { selected = null; undo = null; goalAfterChoice = false }
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

  private fun isEditing() = mode == Mode.SEARCH || mode == Mode.GOAL
  private fun isPicker(value: Mode) = value == Mode.PEOPLE || value == Mode.STYLES
  // Suggestions don't need the letter keys, so they take over that space like the pickers.
  private fun fullPanel(value: Mode) = isPicker(value) || value == Mode.LOADING || value == Mode.REPLIES || value == Mode.ERROR

  private fun open(next: Mode) {
    val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
    val returningKeys = fullPanel(mode) && !fullPanel(next)
    val snapshot = if (fullPanel(next) && !fullPanel(mode)) pickerMotion?.capture() else null
    if (fullPanel(next)) {
      if (isPicker(next)) {
        revision++; job?.cancel()
        currentInputConnection?.finishComposingText()
        if (mode != Mode.SEARCH && !isPicker(mode)) searchRooms = rooms()
        endSearch()
      }
      if (mode != Mode.SEARCH && mode != Mode.GOAL && !fullPanel(mode)) stripVisibility = strip?.visibility ?: View.VISIBLE
      if (!fullPanel(mode)) {
        val frame = keyboardFrame
        pickerHeight = frame?.let { parent -> (0 until parent.childCount).map(parent::getChildAt)
          .filter { it !== panel && it.visibility == View.VISIBLE }.sumOf { it.height } } ?: dp(280)
        if (pickerHeight <= 0) pickerHeight = dp(280)
        frame?.let { parent -> for (i in 0 until parent.childCount) {
          val child = parent.getChildAt(i)
          if (child !== panel) { hiddenKeys[child] = child.visibility; child.visibility = View.GONE }
        } }
      }
    } else if (next == Mode.SEARCH || next == Mode.GOAL) {
      restoreKeys()
      strip?.visibility = View.GONE
    }
    bodyHeight = dp(148)
    mode = next; render(animate = !fullPanel(next) && next != Mode.SEARCH && next != Mode.GOAL && !returningKeys)
    toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
    if (snapshot != null) pickerMotion?.disappear(snapshot, panel.getChildAt(1))
    else if (returningKeys) pickerMotion?.appear()
  }

  private fun typing() {
    val returningKeys = fullPanel(mode)
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
    revision++; job?.cancel(); goalAfterChoice = false; endSearch(); restoreKeys(); mode = Mode.TYPING; render(animate = !returningKeys)
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
    strip?.visibility = if (fullPanel(mode) || mode == Mode.SEARCH) View.GONE else stripVisibility
    if (mode == Mode.PEOPLE) { renderPicker(); return }
    if (mode == Mode.SEARCH) { renderPeople(); return }
    if (mode == Mode.GOAL) { renderGoal(); return }
    val toolbar = brandToolbar()
    if (mode == Mode.TYPING || mode == Mode.REPLIES || mode == Mode.LOADING || mode == Mode.STYLES) {
      val room = person()
      addConversation(toolbar)
      val action = undo
      if (mode == Mode.STYLES) {
        toolbar.addView(button("Podpowiedz", true) {
          undo = null
          if (room == null) { generateAfterChoice = true; open(Mode.PEOPLE) } else generate()
        }, LinearLayout.LayoutParams(dp(110), dp(44)))
      } else if (mode == Mode.LOADING) {
        toolbar.addView(button("Anuluj", false) { typing() }, LinearLayout.LayoutParams(dp(96), dp(44)))
      } else toolbar.addView(button(if (mode == Mode.REPLIES) { if (replies[replyIndex].optString("action") == "no_reply") "Gotowe" else if (draft.isEmpty()) "Wstaw" else "Zastąp szkic" } else if (action != null) "Cofnij" else "Podpowiedz", true) {
        if (mode == Mode.REPLIES) {
          val reply = replies[replyIndex]
          if (reply.optString("action") == "no_reply") typing() else insert(reply.getString("text"))
        } else if (action != null) undoInsert(action) else if (room == null) { generateAfterChoice = true; open(Mode.PEOPLE) } else generate()
      }, LinearLayout.LayoutParams(dp(96), dp(44)))
    } else {
      toolbar.addView(label(person()?.optString("name") ?: "Cue", 13f, true).apply {
        setPadding(dp(8), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      }, LinearLayout.LayoutParams(0, dp(44), 1f))
      toolbar.addView(button("Zamknij", false) { typing() }, LinearLayout.LayoutParams(dp(96), dp(44)))
    }
    finishToolbar(toolbar)
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    if (mode == Mode.STYLES) { renderTones(); return }
    if (mode == Mode.TYPING) return
    if (mode == Mode.LOADING) {
      val loading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(20), 0, dp(20), dp(24)) }
      loading.addView(ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(accent) }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(12) })
      loading.addView(label(if (rejectedReplies.isEmpty()) "Układam odpowiedź…" else "Szukam innych propozycji…", 15f).apply { setTextColor(muted) })
      panel.addView(loading, LinearLayout.LayoutParams(-1, pickerHeight))
      return
    }
    val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), navigation + dp(8)) }
    panel.addView(body, LinearLayout.LayoutParams(-1, if (fullPanel(mode)) pickerHeight else bodyHeight))
    when (mode) {
      Mode.PEOPLE, Mode.STYLES, Mode.SEARCH, Mode.GOAL -> Unit
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
    if (isEditing()) searchConnection?.let(target::search) else target.composer()
    return target
  }

  private fun endSearch() {
    val wasSearching = isEditing()
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
    addRecipient(toolbar, button("Wybierz osobę", false) { typing() }.apply {
      gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      styleRecipient(this, true); contentDescription = "Zamknij wybór rozmowy"
    })
    toolbar.addView(button("Szukaj", false) { open(Mode.SEARCH) }, LinearLayout.LayoutParams(dp(96), dp(44)))
    finishToolbar(toolbar)
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
          addView(KeyboardPersonAvatar(this@SubtextKeyboard, label("", 15f, true).apply { gravity = Gravity.CENTER; setTextColor(accent); background = rounded(card, 18) }, scope), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
          addView(label("", 16f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
          addView(label("", 20f).apply { setTextColor(accent) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
          addView(ImageView(this@SubtextKeyboard).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(20), dp(20)))
          layoutParams = AbsListView.LayoutParams(-1, dp(52))
        }
        (row.getChildAt(0) as KeyboardPersonAvatar).bind(room.optString("name"), room.optString("avatarUri"))
        (row.getChildAt(1) as TextView).text = room.optString("name")
        (row.getChildAt(2) as TextView).text = if (room.optString("id") == selected) "✓" else ""
        row.getChildAt(2).visibility = if (room.optString("id") == selected) View.VISIBLE else View.GONE
        bindPlatform(row.getChildAt(3) as ImageView, room)
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
    toolbar.addView(button("Wróć do listy", false) { open(Mode.PEOPLE) }, LinearLayout.LayoutParams(dp(96), dp(44)))
    finishToolbar(toolbar)
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
          addView(KeyboardPersonAvatar(this@SubtextKeyboard, label("", 14f, true).apply { gravity = Gravity.CENTER; setTextColor(accent); background = rounded(card, 14) }, scope), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
          addView(label("", 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
          addView(label("", 18f).apply { setTextColor(accent) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
          addView(ImageView(this@SubtextKeyboard).apply { scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(20), dp(20)))
          layoutParams = AbsListView.LayoutParams(-1, dp(44))
        }
        (row.getChildAt(0) as KeyboardPersonAvatar).bind(room.optString("name"), room.optString("avatarUri"))
        (row.getChildAt(1) as TextView).text = room.optString("name")
        (row.getChildAt(2) as TextView).text = if (room.optString("id") == selected) "✓" else ""
        row.getChildAt(2).visibility = if (room.optString("id") == selected) View.VISIBLE else View.GONE
        bindPlatform(row.getChildAt(3) as ImageView, room)
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

  private fun renderGoal() {
    val id = selected ?: return typing()
    val toolbar = brandToolbar()
    val hasGoal = runtime.conversationGoal(id).isNotBlank()
    toolbar.addView(ImageButton(this).apply {
      setImageDrawable(getDrawable(R.drawable.cue_chevron_down)?.mutate()?.apply { setTint(ink) }); rotation = 90f
      background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), null, rounded(Color.WHITE, 12)); scaleType = ImageView.ScaleType.CENTER_INSIDE
      setPadding(dp(14), dp(14), dp(14), dp(14)); contentDescription = "Wróć bez zapisywania"
      setOnClickListener { endSearch(); open(Mode.STYLES) }
    }, LinearLayout.LayoutParams(dp(44), dp(44)))
    toolbar.addView(label("Cel rozmowy" + (person()?.optString("name")?.substringBefore(' ')?.let { " · $it" } ?: ""), 15f, true).apply {
      gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(4), 0, dp(4), 0)
    }, LinearLayout.LayoutParams(0, dp(44), 1f))
    if (hasGoal) toolbar.addView(button("Usuń", false) { runtime.setConversationGoal(id, ""); replies = emptyList(); endSearch(); open(Mode.STYLES) }.apply {
      setTextColor(muted); contentDescription = "Usuń cel rozmowy"
    }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(4) })
    toolbar.addView(button("Zapisz", true) { saveGoal() }, LinearLayout.LayoutParams(dp(96), dp(44)))
    finishToolbar(toolbar); panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    goalSelectionStart = -1; goalSelectionEnd = -1
    val editor = object : EditText(this) {
      override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        post { if (mode == Mode.GOAL && searchEditor === this) syncGoalSelection(this) }
      }
    }.apply {
      textSize = 14f; setTextColor(ink); setHintTextColor(muted)
      hint = "np. umówić się w piątek na 18"; contentDescription = "Cel rozmowy"
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
      filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
      isFocusable = true; isFocusableInTouchMode = true
      isCursorVisible = true; showSoftInputOnFocus = false
      gravity = Gravity.TOP or Gravity.START; background = rounded(card, 12)
      setPadding(dp(12), dp(10), dp(12), dp(10)); minLines = 2; maxLines = 3
      setText(runtime.conversationGoal(id)); setSelection(text.length)
    }
    searchEditor = editor
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(4)) }
    body.addView(editor, LinearLayout.LayoutParams(-1, dp(68)))
    // Quick starts fill the field; the user can then add details like the day or time.
    val ideas = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    listOf("Umówić się", "Przeprosić", "Postawić granicę", "Wyjaśnić nieporozumienie", "Pogodzić się").forEach { idea ->
      ideas.addView(button(idea, false) {
        editor.setText(idea + " "); editor.setSelection(editor.text.length); editor.requestFocus(); syncGoalSelection(editor)
      }.apply {
        textSize = 13f; setTextColor(ink); setPadding(dp(12), 0, dp(12), 0)
        background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(card, 14), null)
      }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
    }
    body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(ideas) },
      LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
    panel.addView(body)
    // Use Android's full editor connection: composition, selection, clipboard and cursor keys.
    editor.setOnEditorActionListener { _, _, _ -> searchConnection?.commitText("\n", 1); true }
    searchConnection = editor.onCreateInputConnection(EditorInfo().apply {
      inputType = editor.inputType
      imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
      initialSelStart = editor.selectionStart; initialSelEnd = editor.selectionEnd
    })
    currentInputConnection
    editor.post {
      if (mode == Mode.GOAL && searchEditor === editor) {
        editor.requestFocus(); syncGoalSelection(editor)
      }
    }
    panel.announceForAccessibility("Wpisz cel rozmowy. Klawiatura edytuje cel, nie wiadomość.")
  }

  private fun syncGoalSelection(editor: EditText) {
    val start = editor.selectionStart; val end = editor.selectionEnd
    if (start == goalSelectionStart && end == goalSelectionEnd) return
    val previousStart = goalSelectionStart; val previousEnd = goalSelectionEnd
    goalSelectionStart = start; goalSelectionEnd = end
    super.onUpdateSelection(previousStart, previousEnd, start, end,
      BaseInputConnection.getComposingSpanStart(editor.text), BaseInputConnection.getComposingSpanEnd(editor.text))
  }

  private fun saveGoal() {
    val id = selected ?: return
    runtime.setConversationGoal(id, searchEditor?.text?.toString().orEmpty())
    replies = emptyList(); endSearch(); open(Mode.STYLES)
    panel.announceForAccessibility("Zapisano cel rozmowy")
  }

  private fun bindPlatform(icon: ImageView, room: JSONObject) {
    val platform = when (room.optString("network")) {
      "messenger" -> R.drawable.cue_network_messenger to "Messenger"
      "whatsapp" -> R.drawable.cue_network_whatsapp to "WhatsApp"
      else -> null
    }
    icon.visibility = if (platform == null) View.GONE else View.VISIBLE
    icon.setImageResource(platform?.first ?: 0)
    icon.contentDescription = platform?.second
  }

  private fun choosePerson(room: JSONObject) {
    selected = room.optString("id"); undo = null
    val shouldGenerate = generateAfterChoice
    val editGoal = goalAfterChoice; goalAfterChoice = false
    typing()
    if (editGoal) open(Mode.GOAL) else if (shouldGenerate) generate()
  }
  private fun submitEditor() { if (mode == Mode.GOAL) saveGoal() else chooseOnlyResult() }
  private fun chooseOnlyResult() { if (searchResults.size == 1) choosePerson(searchResults.single()) }

  override fun onEvent(event: Event) {
    if (mode != Mode.SEARCH) { super.onEvent(event); return }
    when {
      event.keyCode == KeyCode.DELETE -> searchConnection?.deleteSurroundingText(1, 0)
      event.codePoint == 10 || event.keyCode == KeyCode.SHIFT_ENTER -> submitEditor()
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

  private var rejectedReplies = emptyList<String>()
  private fun generate(again: Boolean = false) {
    val id = selected ?: return open(Mode.PEOPLE)
    if (job?.isActive == true || !available) return
    // Everything shown so far was not good enough; the next round should go elsewhere.
    rejectedReplies = if (again) (rejectedReplies + replies.map { it.optString("text") }.filter(String::isNotBlank)).takeLast(6) else emptyList()
    val snapshot = readDraft()
    if (snapshot == null) { errorMessage = "Nie mogę odczytać tego szkicu. Wróć do pisania i spróbuj ponownie."; open(Mode.ERROR); return }
    draft = snapshot; undo = null
    val token = revision
    val tone = runtime.selectedTone(id)
    open(Mode.LOADING)
    job = scope.launch {
      try {
        val profile = withContext(Dispatchers.IO) { JSONObject(runtime.analyze(id, snapshot, tone, rejected = rejectedReplies)) }
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
    fun pick(index: Int) {
      if (index == replyIndex || index !in replies.indices) return
      val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
      replyIndex = index; render()
      toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
    }
    if (replies.size > 1) {
      val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(2)); background = rounded(card, 14) }
      replies.forEachIndexed { index, reply ->
        val active = index == replyIndex
        tabs.addView(button(if (reply.optString("action") == "no_reply") "Bez odpowiedzi" else reply.optString("tone").ifBlank { "${index + 1}" }, false) { pick(index) }.apply {
          textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(6), 0, dp(6), 0)
          setTextColor(if (active) onAccent else muted)
          background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(if (active) accent else Color.TRANSPARENT, 12), null)
          contentDescription = "Propozycja ${index + 1} z ${replies.size}: $text" + if (active) ", wybrana" else ""
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
      }
      body.addView(tabs, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(2) })
    }
    val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true }
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    if (noReply) {
      content.addView(label("Nie musisz teraz odpisywać", 18f, true).apply { setPadding(dp(4), dp(8), dp(4), 0) })
    } else {
      content.addView(label(suggestion.getString("text"), 18f).apply {
        setLineSpacing(dp(3).toFloat(), 1f); setTextIsSelectable(false)
        background = rounded(card, 16); setPadding(dp(16), dp(14), dp(16), dp(14))
      })
    }
    val reason = suggestion.optString("reason").ifBlank { if (noReply) "Możesz wrócić do tej rozmowy później." else "" }
    if (reason.isNotBlank()) {
      content.addView(label("Dlaczego ta odpowiedź", 12f, true).apply { setTextColor(accent); setPadding(dp(4), dp(16), dp(4), dp(4)) })
      content.addView(label(reason, 14f).apply { setTextColor(muted); setLineSpacing(dp(2).toFloat(), 1f); setPadding(dp(4), 0, dp(4), 0) })
    }
    scroll.addView(content)
    // Swipe across the suggestion to move between reply types.
    var downX = 0f; var downY = 0f
    scroll.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        android.view.MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
        android.view.MotionEvent.ACTION_UP -> {
          val dx = event.x - downX
          if (kotlin.math.abs(dx) > dp(60) && kotlin.math.abs(dx) > 2 * kotlin.math.abs(event.y - downY)) { pick(replyIndex + if (dx < 0) 1 else -1); return@setOnTouchListener true }
        }
      }
      false
    }
    body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
    val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    footer.addView(button("↻  Inne propozycje", false) { generate(again = true) }.apply {
      textSize = 14f; setTextColor(accent); contentDescription = "Wygeneruj inne propozycje"
    }, LinearLayout.LayoutParams(0, dp(44), 1f))
    footer.addView(button("⌨  Klawiatura", false) { typing() }.apply {
      textSize = 14f; setTextColor(muted); contentDescription = "Zamknij podpowiedzi i wróć do pisania"
    }, LinearLayout.LayoutParams(0, dp(44), 1f))
    body.addView(footer, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
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
  }
  private fun addRecipient(toolbar: LinearLayout, recipient: Button) {
    val cell = FrameLayout(this)
    cell.addView(recipient, FrameLayout.LayoutParams(-2, dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
    cell.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
      val width = (right - left).coerceAtLeast(0)
      if (recipient.maxWidth != width) recipient.maxWidth = width
    }
    toolbar.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
  }

  private var conversationStyle: Button? = null
  private fun styleSummary(): String = WritingTone.shortLabel(runtime.selectedTone(selected))
  // Name opens the people list; the style part opens goal, style and intensity.
  private fun addConversation(toolbar: LinearLayout) {
    val expanded = mode == Mode.STYLES
    val room = person()
    val goalSet = selected?.let(runtime::conversationGoal)?.isNotBlank() == true
    val name = button(room?.optString("name") ?: "Wybierz osobę", false) { undo = null; generateAfterChoice = false; open(Mode.PEOPLE) }.apply {
      gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      styleRecipient(this, false); includeFontPadding = false
      contentDescription = "Rozmowa: ${room?.optString("name") ?: "nie wybrano"}. Zmień osobę"
    }
    val style = button(styleSummary(), false) { undo = null; generateAfterChoice = false; if (expanded) typing() else open(Mode.STYLES) }.apply {
      textSize = 14f; letterSpacing = -0.015f; setTextColor(muted); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setPadding(dp(8), 0, dp(8), 0)
      val chevron = getDrawable(R.drawable.cue_chevron_down)?.mutate()?.apply { setTint(muted); setBounds(0, 0, dp(12), dp(12)) }
      val arrow = chevron?.let { android.graphics.drawable.RotateDrawable().apply {
        drawable = it; fromDegrees = 0f; toDegrees = 180f; setBounds(0, 0, dp(12), dp(12)); level = if (expanded) 10000 else 0 } }
      setCompoundDrawablesRelative(if (goalSet) KeyboardTargetIcon(muted, dp(12)) else null, null, arrow, null)
      compoundDrawablePadding = dp(5)
      contentDescription = "Styl: ${styleSummary()}" + (if (goalSet) ", cel ustawiony. " else ". ") + if (expanded) "Zamknij ustawienia" else "Zmień cel i styl"
    }
    conversationStyle = style
    val cell = KeyboardConversationRow(this, name, style, dp(110))
    toolbar.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
  }

  private fun renderTones() {
    val keys = WritingTone.labels.keys.toList()
    var tone = runtime.selectedTone(selected)
    val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), navigation + dp(4)) }
    val goalText = selected?.let(runtime::conversationGoal)?.ifBlank { null }
    val goal = LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(12), dp(8))
      background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), null, rounded(Color.WHITE, 16))
      val texts = LinearLayout(this@SubtextKeyboard).apply { orientation = LinearLayout.VERTICAL }
      texts.addView(label("Cel rozmowy", 12f).apply { setTextColor(muted) })
      texts.addView(label(goalText ?: "np. umówić się w piątek na 18", 15f, goalText != null).apply {
        setTextColor(if (goalText != null) ink else muted); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0)
      })
      addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
      addView(label(if (goalText != null) "Edytuj" else "Dodaj", 14f, true).apply { setTextColor(accent); setPadding(dp(12), 0, dp(4), 0) })
      contentDescription = "Cel rozmowy: ${goalText ?: "nie ustawiono"}. " + if (goalText != null) "Edytuj cel" else "Dodaj cel"
      setOnClickListener {
        generateAfterChoice = false
        if (selected == null) { goalAfterChoice = true; open(Mode.PEOPLE) } else open(Mode.GOAL)
      }
    }
    content.addView(goal, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(4) })
    val intensity = FrameLayout(this)
    val hint = label("", 15f).apply { setTextColor(ink); gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    fun showIntensity() {
      intensity.removeAllViews()
      val levels = WritingTone.levels(tone)
      hint.text = WritingTone.hint(tone, runtime.selectedIntensity(selected, tone))
      if (levels.isEmpty()) return
      intensity.addView(KeyboardLevelSlider(this, levels, runtime.selectedIntensity(selected, tone), ink, muted, accent, onAccent) { level ->
        runtime.setWritingIntensity(selected, tone, level); replies = emptyList()
        hint.text = WritingTone.hint(tone, level); conversationStyle?.text = styleSummary()
      }, FrameLayout.LayoutParams(-1, -1))
    }
    content.addView(KeyboardSwipeSelector(this, keys.map { if (it == "calming") "Łagodzący" else WritingTone.label(it) }, keys.indexOf(tone).coerceAtLeast(0), 20f, ink, muted, accent,
      { "Styl odpowiedzi: $it" }) { position ->
      tone = keys[position]
      runtime.setWritingTone(selected, tone); replies = emptyList()
      conversationStyle?.text = styleSummary()
      showIntensity()
    }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(6) })
    content.addView(intensity, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(10); marginStart = dp(8); marginEnd = dp(8) })
    content.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); marginStart = dp(20); marginEnd = dp(20) })
    showIntensity()
    panel.addView(content, LinearLayout.LayoutParams(-1, pickerHeight))
  }

  private fun styleRecipient(view: Button, expanded: Boolean) {
    view.textSize = 14f
    view.letterSpacing = -0.015f
    view.setTextColor(ink)
    chevronAnimator?.cancel()
    val startLevel = if (headerExpanded) 10000 else 0
    val endLevel = if (expanded) 10000 else 0
    val chevron = android.graphics.drawable.RotateDrawable().apply {
      drawable = getDrawable(R.drawable.cue_chevron_down)?.mutate()?.apply { setTint(muted) }
      fromDegrees = 0f; toDegrees = 180f
      setBounds(0, 0, dp(14), dp(14)); level = startLevel
    }
    if (startLevel != endLevel && android.animation.ValueAnimator.areAnimatorsEnabled()) {
      chevronAnimator = android.animation.ValueAnimator.ofInt(startLevel, endLevel).apply {
        duration = 140
        addUpdateListener { chevron.level = it.animatedValue as Int }
        start()
      }
    } else chevron.level = endLevel
    headerExpanded = expanded
    view.setCompoundDrawablesRelative(null, null, chevron, null)
    view.compoundDrawablePadding = dp(6)
    view.setPadding(dp(4), 0, dp(12), 0)
  }
  private fun finishToolbar(toolbar: LinearLayout) {
    val action = toolbar.getChildAt(toolbar.childCount - 1) as? Button ?: return
    val primary = mode == Mode.TYPING || mode == Mode.REPLIES || mode == Mode.STYLES || mode == Mode.GOAL
    // Compact tonal pill: clearly tappable, without a heavy outline.
    val fill = if (primary) Color.parseColor(if (dark) "#2E3C5E" else "#E3EAFB") else Color.TRANSPARENT
    val pressed = Color.parseColor(if (dark) "#3A4C75" else "#CFDCF7")
    action.background = RippleDrawable(ColorStateList.valueOf(pressed), InsetDrawable(rounded(fill, 16), 0, dp(10), 0, dp(10)),
      InsetDrawable(rounded(Color.WHITE, 16), 0, dp(10), 0, dp(10)))
    action.setTextColor(if (primary) accent else muted)
    action.textSize = 12.5f; action.letterSpacing = 0.01f
    action.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    action.setPadding(dp(11), 0, dp(11), 0)
    action.maxLines = 1; action.ellipsize = TextUtils.TruncateAt.END
    action.layoutParams = (action.layoutParams as LinearLayout.LayoutParams).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT; height = dp(48) }
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
    if (isEditing()) return
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    if ((mode == Mode.LOADING || mode == Mode.REPLIES) && readDraft()?.let { it != draft } == true) typing()
    if (undo != null && readDraft()?.let { it != undo?.first } == true) { undo = null; render() }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && mode != Mode.TYPING) { backHandled = true; if (mode == Mode.SEARCH) open(Mode.PEOPLE) else if (mode == Mode.GOAL) { endSearch(); open(Mode.STYLES) } else typing(); return true }
    if (mode == Mode.GOAL) return searchEditor?.dispatchKeyEvent(event) ?: true
    if (mode == Mode.SEARCH) {
      consumedHardwareKeys.add(keyCode)
      when (keyCode) {
        KeyEvent.KEYCODE_DEL -> searchConnection?.deleteSurroundingText(1, 0)
        KeyEvent.KEYCODE_ENTER -> submitEditor()
        else -> if (event.unicodeChar >= 32) searchConnection?.commitText(String(Character.toChars(event.unicodeChar)), 1)
      }
      return true
    }
    return super.onKeyDown(keyCode, event)
  }
  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && backHandled) { backHandled = false; return true }
    if (consumedHardwareKeys.remove(keyCode)) return true
    if (mode == Mode.GOAL) return searchEditor?.dispatchKeyEvent(event) ?: true
    return super.onKeyUp(keyCode, event)
  }
  override fun onFinishInputView(finishingInput: Boolean) {
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    revision++; job?.cancel(); endSearch(); restoreKeys(); mode = Mode.TYPING
    super.onFinishInputView(finishingInput)
  }
  override fun onFinishInput() { pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel(); revision++; job?.cancel(); selected = null; undo = null; endSearch(); restoreKeys(); mode = Mode.TYPING; super.onFinishInput() }
  override fun onDestroy() { chevronAnimator?.cancel(); pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel(); scope.cancel(); super.onDestroy() }
}
