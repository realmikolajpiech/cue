package expo.modules.subtext

import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
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
  private enum class Mode { TYPING, PEOPLE, STYLES, SEARCH, GOAL, TOPIC, DECISION, LOADING, REPLIES, ERROR }
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
  // Goal or topic editor to open once a person is picked.
  private var afterChoice: Mode? = null
  // Topic picked for the current replies; "other ideas" keeps it, a fresh suggestion drops it.
  private var activeTopic = ""
  // Topic picked from the list, shown on the tile and used by the next suggestion.
  private var pendingTopic = ""
  // A yes/no question from the other person: shown before replies, then the user's answer to it.
  private var question: JSONObject? = null
  private var decision: JSONObject? = null
  // A first message to someone without a conversation: the context the user gave, while composing it.
  private var openerContext: String? = null
  private var composingOpener = false
  // Whatever the user had open survives the keyboard hiding (app switch, minimise) until they leave it on purpose.
  // For LOADING, replies stay empty while the generation behind [token] keeps running in the background.
  private data class Parked(val app: String, val mode: Mode, val selected: String?, val opener: String?,
    val composingOpener: Boolean, val afterChoice: Mode?, val generateAfterChoice: Boolean,
    val replies: List<JSONObject>, val index: Int, val draft: String, val editorText: String?, val error: String,
    val at: Long, val token: Int?, val topic: String, val pendingTopic: String, val question: JSONObject?, val decision: JSONObject?)
  private var parked: Parked? = null
  // A background generation the visible keyboard is waiting on again after being restored.
  private var adoptedToken = -1
  // Text the user had typed into the search or goal field, put back when that editor is rebuilt.
  private var restoredEditorText: String? = null
  private fun backgroundLoading() = parked?.token != null && job?.isActive == true
  private fun park() {
    if (mode == Mode.TYPING || (mode == Mode.REPLIES && replies.isEmpty())) return
    val loading = mode == Mode.LOADING
    if (loading && job?.isActive != true) return
    parked = Parked(currentInputEditorInfo?.packageName.orEmpty(), mode, selected, openerContext, composingOpener,
      afterChoice, generateAfterChoice, if (loading) emptyList() else replies, replyIndex, draft,
      if (isEditing()) searchEditor?.text?.toString() else null, errorMessage, System.currentTimeMillis(), if (loading) revision else null, activeTopic, pendingTopic, question, decision)
  }
  private fun restoreParked(info: EditorInfo?): Boolean {
    val saved = parked ?: return false
    if (!available || info?.packageName.orEmpty() != saved.app || mode != Mode.TYPING) return false
    if (System.currentTimeMillis() - saved.at > PARKED_TTL_MS || (saved.token != null && job?.isActive != true)) { parked = null; return false }
    // Replies only make sense against the text they were written for.
    val needsDraft = saved.mode == Mode.LOADING || saved.mode == Mode.REPLIES || saved.mode == Mode.ERROR
    if (needsDraft && readDraft() != saved.draft) return false
    selected = saved.selected; openerContext = saved.opener; composingOpener = saved.composingOpener
    afterChoice = saved.afterChoice; generateAfterChoice = saved.generateAfterChoice; activeTopic = saved.topic; pendingTopic = saved.pendingTopic; question = saved.question; decision = saved.decision
    draft = saved.draft; undo = null; errorMessage = saved.error; restoredEditorText = saved.editorText
    when (saved.mode) {
      Mode.LOADING -> adoptedToken = saved.token ?: -1
      Mode.REPLIES -> { replies = saved.replies; replyIndex = saved.index.coerceIn(0, saved.replies.lastIndex) }
      Mode.SEARCH -> searchRooms = rooms()
      Mode.GOAL -> if (selected == null && !composingOpener) { restoredEditorText = null; return false }
      Mode.TOPIC -> if (selected == null) return false
      Mode.DECISION -> if (selected == null || question == null) return false
      else -> Unit
    }
    open(saved.mode)
    return true
  }

  private var goalSelectionStart = -1
  private var goalSelectionEnd = -1
  private val consumedHardwareKeys = mutableSetOf<Int>()
  private val runtime get() = SubtextRuntime.get(this)
  private companion object { const val PARKED_TTL_MS = 15 * 60_000L }
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
    park()
    revision++; adoptedToken = -1; if (!backgroundLoading()) job?.cancel()
    if (!restarting) { selected = null; undo = null; afterChoice = null; activeTopic = ""; pendingTopic = ""; question = null; decision = null; openerContext = null; composingOpener = false }
    endSearch(); restoreKeys()
    mode = Mode.TYPING
    available = attribute != null && KeyboardReplySession.available(attribute.packageName.orEmpty(), attribute.inputType, attribute.imeOptions)
    super.onStartInput(attribute, restarting)
  }

  override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
    super.onStartInputView(info, restarting)
    if (::panel.isInitialized && !restoreParked(info)) render()
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
    .filter { !runtime.prefs.getBoolean("demoMode", false) || DemoVisibility.allows(it.optString("name")) }
  private fun person() = rooms().firstOrNull { it.optString("id") == selected }

  private fun restoreKeys() {
    hiddenKeys.forEach { (view, visibility) -> view.visibility = visibility }
    hiddenKeys.clear()
  }

  private fun isEditing() = mode == Mode.SEARCH || isEditor(mode)
  // Goal and topic share one multi-line editor above the letter keys.
  private fun isEditor(value: Mode) = value == Mode.GOAL
  private fun isPicker(value: Mode) = value == Mode.PEOPLE || value == Mode.STYLES
  // Suggestions don't need the letter keys, so they take over that space like the pickers.
  private fun fullPanel(value: Mode) = isPicker(value) || value == Mode.TOPIC || value == Mode.DECISION || value == Mode.LOADING || value == Mode.REPLIES || value == Mode.ERROR

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
      if (mode != Mode.SEARCH && !isEditor(mode) && !fullPanel(mode)) stripVisibility = strip?.visibility ?: View.VISIBLE
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
    } else if (next == Mode.SEARCH || isEditor(next)) {
      restoreKeys()
      strip?.visibility = View.GONE
    }
    bodyHeight = dp(148)
    val previous = mode
    mode = next; render(animate = !fullPanel(next) && next != Mode.SEARCH && !isEditor(next) && !returningKeys)
    toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
    if (snapshot != null) pickerMotion?.disappear(snapshot, panel.getChildAt(1))
    else if (returningKeys) pickerMotion?.appear()
    slideSubpanel(previous, next)
  }

  // Goal and topic slide in over the style panel like a page pushed on top; going back slides the styles in from the other side.
  private fun slideSubpanel(from: Mode, to: Mode) {
    val sub = setOf(Mode.GOAL, Mode.TOPIC)
    val forward = from == Mode.STYLES && to in sub
    if (!forward && !(from in sub && to == Mode.STYLES) || !android.animation.ValueAnimator.areAnimatorsEnabled()) return
    val body = panel.getChildAt(1) ?: return
    val shift = dp(28).toFloat() * if (forward) 1f else -1f
    body.alpha = 0f; body.translationX = shift
    body.animate().alpha(1f).translationX(0f).setStartDelay(if (forward) 40 else 0).setDuration(260)
      .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)).start()
    // Topic rows follow one after another, settling with the same small overshoot as the decision choices.
    val rows = if (forward && to == Mode.TOPIC) ((body as? ScrollView)?.getChildAt(0) as? ViewGroup) else null
    rows?.let { list -> for (i in 0 until list.childCount) {
      val row = list.getChildAt(i)
      row.alpha = 0f; row.translationY = dp(14).toFloat()
      row.animate().alpha(1f).translationY(0f).setStartDelay(80L + i * 45L).setDuration(300)
        .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
    } }
  }

  private fun typing() {
    val returningKeys = fullPanel(mode)
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
    parked = null; adoptedToken = -1
    revision++; job?.cancel(); afterChoice = null; composingOpener = false; endSearch(); restoreKeys(); mode = Mode.TYPING; render(animate = !returningKeys)
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
    progressBase = shownProgress.toMap()
    panel.removeAllViews(); panel.setBackgroundColor(surface)
    panel.visibility = if (available) View.VISIBLE else View.GONE
    if (!available) return
    strip?.visibility = if (fullPanel(mode) || mode == Mode.SEARCH) View.GONE else stripVisibility
    if (mode == Mode.PEOPLE) { renderPicker(); return }
    if (mode == Mode.SEARCH) { renderPeople(); return }
    if (mode == Mode.GOAL) { renderGoal(); return }
    if (mode == Mode.TOPIC) { renderTopic(); return }
    if (mode == Mode.DECISION) { renderDecision(); return }
    val toolbar = brandToolbar()
    if (mode == Mode.TYPING || mode == Mode.REPLIES || mode == Mode.LOADING || mode == Mode.STYLES) {
      val room = person()
      addConversation(toolbar)
      val action = undo
      if (mode == Mode.STYLES) {
        toolbar.addView(button(s(R.string.cue_kb_suggest), true) {
          undo = null
          if (room == null && openerContext == null) { generateAfterChoice = true; open(Mode.PEOPLE) } else generate()
        }, LinearLayout.LayoutParams(dp(110), dp(44)))
      } else if (mode == Mode.LOADING) {
        toolbar.addView(button(s(R.string.cue_kb_cancel), false) { typing() }, LinearLayout.LayoutParams(dp(96), dp(44)))
      } else toolbar.addView(button(if (mode == Mode.REPLIES) { if (replies[replyIndex].optString("action") == "no_reply") s(R.string.cue_kb_done) else if (draft.isEmpty()) s(R.string.cue_kb_insert) else s(R.string.cue_kb_replace_draft) } else if (action != null) s(R.string.cue_kb_undo) else s(R.string.cue_kb_suggest), true) {
        if (mode == Mode.REPLIES) {
          val reply = replies[replyIndex]
          if (reply.optString("action") == "no_reply") typing() else insert(reply.getString("text"))
        } else if (action != null) undoInsert(action) else if (room == null && openerContext == null) { generateAfterChoice = true; open(Mode.PEOPLE) } else generate()
      }, LinearLayout.LayoutParams(dp(96), dp(44)))
    } else {
      toolbar.addView(label(person()?.optString("name") ?: "Cue", 13f, true).apply {
        setPadding(dp(8), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      }, LinearLayout.LayoutParams(0, dp(44), 1f))
      toolbar.addView(button(s(R.string.cue_kb_close), false) { typing() }, LinearLayout.LayoutParams(dp(96), dp(44)))
    }
    finishToolbar(toolbar)
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    if (mode == Mode.STYLES) { renderTones(); return }
    if (mode == Mode.TYPING) return
    if (mode == Mode.LOADING) {
      val message = if (rejectedReplies.isEmpty()) s(R.string.cue_kb_composing) else s(R.string.cue_kb_composing_again)
      val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
      panel.addView(KeyboardGhostLoader(this, message, muted, accent).apply { setPadding(0, 0, 0, navigation) }, LinearLayout.LayoutParams(-1, pickerHeight))
      return
    }
    val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), navigation + dp(8)) }
    panel.addView(body, LinearLayout.LayoutParams(-1, if (fullPanel(mode)) pickerHeight else bodyHeight))
    when (mode) {
      Mode.PEOPLE, Mode.STYLES, Mode.SEARCH, Mode.GOAL, Mode.TOPIC, Mode.DECISION -> Unit
      Mode.LOADING -> Unit
      Mode.ERROR -> {
        val center = center(body)
        center.addView(label(errorMessage, 15f).apply { gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(16), dp(16)) })
        val cloud = runtime.prefs.getBoolean("cloud", false)
        center.addView(button(if (cloud) s(R.string.cue_kb_retry) else s(R.string.cue_kb_open_app), true) { if (cloud) generate(topic = activeTopic) else openApp() }, LinearLayout.LayoutParams(-1, dp(48)))
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
    addRecipient(toolbar, button(s(R.string.cue_kb_choose_person), false) { typing() }.apply {
      gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      styleRecipient(this, true); contentDescription = s(R.string.cue_kb_close_picker)
    })
    toolbar.addView(button(s(R.string.cue_kb_search), false) { open(Mode.SEARCH) }, LinearLayout.LayoutParams(dp(96), dp(44)))
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
    // Someone the app has no conversation with yet, e.g. a new Instagram DM.
    list.addHeaderView(LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0); layoutParams = AbsListView.LayoutParams(-1, dp(52))
      addView(label("+", 20f, true).apply { gravity = Gravity.CENTER; setTextColor(accent); background = rounded(card, 18) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
      addView(label(s(R.string.cue_kb_new_person), 16f, true).apply { setTextColor(accent) }, LinearLayout.LayoutParams(0, -2, 1f))
      contentDescription = s(R.string.cue_kb_new_person)
    })
    if (searchRooms.isEmpty()) list.addFooterView(button(s(R.string.cue_kb_check_app), false) { openApp() }.apply { setTextColor(muted) }, null, false)
    list.setOnItemClickListener { _, _, position, _ ->
      if (position < list.headerViewsCount) { composingOpener = true; open(Mode.GOAL) }
      else searchRooms.getOrNull(position - list.headerViewsCount)?.let(::choosePerson)
    }
    panel.addView(list, LinearLayout.LayoutParams(-1, pickerHeight))
  }

  private fun renderPeople() {
    val oldQuery = searchEditor?.text?.toString() ?: restoredEditorText.orEmpty()
    restoredEditorText = null
    val toolbar = brandToolbar()
    val editor = EditText(this).apply {
      setSingleLine(); textSize = 15f; setTextColor(ink); setHintTextColor(muted)
      hint = s(R.string.cue_kb_search_hint); contentDescription = s(R.string.cue_kb_search_description)
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      isFocusable = false; showSoftInputOnFocus = false
      background = rounded(card, 12); setPadding(dp(12), 0, dp(12), 0)
      setText(oldQuery); setSelection(text.length)
    }
    searchEditor = editor
    toolbar.addView(editor, LinearLayout.LayoutParams(0, dp(44), 1f))
    toolbar.addView(button(s(R.string.cue_kb_back_to_list), false) { open(Mode.PEOPLE) }, LinearLayout.LayoutParams(dp(96), dp(44)))
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
      empty.text = s(R.string.cue_kb_check_app); empty.setOnClickListener { openApp() }
    }
    fun update() {
      searchResults = KeyboardPersonSearch.filter(searchRooms, editor.text.toString())
      empty.visibility = if (searchResults.isEmpty()) View.VISIBLE else View.GONE
      if (searchRooms.isNotEmpty()) empty.text = s(R.string.cue_kb_no_person)
      adapter.notifyDataSetChanged(); list.setSelection(0)
    }
    editor.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }
      override fun afterTextChanged(s: Editable?) = Unit
    })
    update()
    panel.announceForAccessibility(s(R.string.cue_kb_search_announcement))
  }

  private fun renderGoal() {
    if (composingOpener) { renderOpener(); return }
    val id = selected ?: return typing()
    runtime.dismissFinishedGoal(id)
    val toolbar = brandToolbar()
    val hasGoal = runtime.conversationGoal(id).isNotBlank()
    toolbar.addView(editorBack { endSearch(); open(Mode.STYLES) }, LinearLayout.LayoutParams(dp(44), dp(44)))
    toolbar.addView(editorTitle(s(R.string.cue_kb_goal) + (person()?.optString("name")?.substringBefore(' ')?.let { " · $it" } ?: "")), LinearLayout.LayoutParams(0, dp(44), 1f))
    if (hasGoal) toolbar.addView(button(s(R.string.cue_kb_delete), false) { runtime.setConversationGoal(id, ""); replies = emptyList(); endSearch(); open(Mode.STYLES) }.apply {
      setTextColor(muted); contentDescription = s(R.string.cue_kb_delete_goal)
    }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(4) })
    toolbar.addView(button(s(R.string.cue_kb_save), true) { saveGoal() }, LinearLayout.LayoutParams(dp(96), dp(44)))
    finishToolbar(toolbar); panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    val body = editorPanel(s(R.string.cue_kb_goal_hint), s(R.string.cue_kb_goal), runtime.conversationGoal(id), 1000)
    // The remembered route: where the conversation is now and what the plan waits for.
    if (hasGoal) body.addView(LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(2))
      val plan = runtime.goalPlan(id)
      addView(ImageView(this@SubtextKeyboard).apply { setImageDrawable(KeyboardGoalIndicator.of(plan, ink, dp(14))) }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(8) })
      addView(label(planStatus(plan), 13f).apply { setTextColor(muted); maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
    }, LinearLayout.LayoutParams(-1, -2))
    panel.announceForAccessibility(s(R.string.cue_kb_goal_announcement))
  }

  // A plain list of topics: tapping one picks it and goes back to the style panel, no typing.
  private fun renderTopic() {
    val id = selected ?: return typing()
    val toolbar = brandToolbar()
    toolbar.addView(editorBack { open(Mode.STYLES) }.apply { contentDescription = s(R.string.cue_kb_back) }, LinearLayout.LayoutParams(dp(44), dp(44)))
    toolbar.addView(editorTitle(s(R.string.cue_kb_topics) + (person()?.optString("name")?.substringBefore(' ')?.let { " · $it" } ?: "")), LinearLayout.LayoutParams(0, dp(44), 1f))
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), navigation + dp(8)) }
    val ideas = runtime.topicIdeas(id).ifEmpty { CueLanguage.resources(this).getStringArray(R.array.cue_kb_topic_ideas).toList() }
    ideas.forEach { idea ->
      val chosen = idea == pendingTopic
      list.addView(LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(12), 0)
        background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(card, 14), null)
        addView(label(idea, 15f, chosen).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        if (chosen) addView(label("✓", 18f).apply { setTextColor(accent) })
        contentDescription = idea + if (chosen) s(R.string.cue_kb_selected) else ""
        setOnClickListener { pendingTopic = idea; replies = emptyList(); open(Mode.STYLES) }
      }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
    }
    panel.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = true; addView(list) }, LinearLayout.LayoutParams(-1, pickerHeight))
    prefetchIdeas(id)
    panel.announceForAccessibility(s(R.string.cue_kb_topic_announcement))
  }
  // The other person asked something only the user can decide; their answer steers all three replies.
  private fun renderDecision() {
    val asked = question ?: return typing()
    val toolbar = brandToolbar()
    toolbar.addView(editorBack { typing() }.apply { contentDescription = s(R.string.cue_kb_back) }, LinearLayout.LayoutParams(dp(44), dp(44)))
    toolbar.addView(editorTitle(s(R.string.cue_kb_decision_title)), LinearLayout.LayoutParams(0, dp(44), 1f))
    panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), navigation + dp(8)) }
    val name = person()?.optString("name")?.substringBefore(' ').orEmpty()
    body.addView(label(if (name.isBlank()) s(R.string.cue_kb_decision_asks_someone) else s(R.string.cue_kb_decision_asks, name), 13f).apply { setTextColor(muted); setPadding(dp(4), 0, 0, dp(8)) })
    // Drawn like the incoming message it came from.
    val bubble = label(asked.optString("question"), 17f, true).apply {
      setLineSpacing(dp(3).toFloat(), 1f); setPadding(dp(18), dp(14), dp(18), dp(14)); maxLines = 4; ellipsize = TextUtils.TruncateAt.END
      background = GradientDrawable().apply {
        setColor(card); val r = dp(20).toFloat(); cornerRadii = floatArrayOf(dp(6).toFloat(), dp(6).toFloat(), r, r, r, r, r, r)
      }
    }
    body.addView(FrameLayout(this).apply { addView(bubble, FrameLayout.LayoutParams(-2, -2)) }, LinearLayout.LayoutParams(-1, -2))
    body.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
    val choices = LinearLayout(this)
    listOf(Triple("yes", "👍", R.string.cue_kb_decision_yes), Triple("no", "👎", R.string.cue_kb_decision_no),
      Triple("unsure", "🤷", R.string.cue_kb_decision_unsure)).forEachIndexed { index, (answer, emoji, text) ->
      val choice = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = RippleDrawable(ColorStateList.valueOf(0x335F78B8), rounded(androidx.core.graphics.ColorUtils.blendARGB(card, accent, if (dark) 0.14f else 0.08f), 20), null)
        addView(label(emoji, 24f).apply { gravity = Gravity.CENTER })
        addView(label(s(text), 15f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
        contentDescription = s(text)
        setOnClickListener { view ->
          view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
            decide(answer)
          }.start()
        }
      }
      choices.addView(choice, LinearLayout.LayoutParams(0, dp(88), 1f).apply { if (index > 0) marginStart = dp(10) })
      // The three answers rise in one after another.
      if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
        choice.alpha = 0f; choice.translationY = dp(18).toFloat()
        choice.animate().alpha(1f).translationY(0f).setStartDelay(60L + index * 70L).setDuration(320)
          .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
      }
    }
    body.addView(choices, LinearLayout.LayoutParams(-1, dp(88)))
    body.addView(button(s(R.string.cue_kb_decision_skip), false) { decide("skip") }.apply { textSize = 13f; setTextColor(muted) },
      LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })
    panel.addView(body, LinearLayout.LayoutParams(-1, pickerHeight))
    panel.announceForAccessibility(s(R.string.cue_kb_decision_announcement, asked.optString("question")))
  }
  private fun decide(answer: String) {
    val asked = question ?: return
    if (mode != Mode.DECISION) return
    decision = JSONObject(asked.toString()).put("answer", answer)
    generate()
  }
  private fun reanswer(answer: String) {
    val current = decision ?: return
    // Leaving an answer keeps the tab the user was on.
    answerReplies[current.optString("answer")]?.let { answerReplies[current.optString("answer")] = it.first to replyIndex }
    decision = JSONObject(current.toString()).put("answer", answer)
    val cached = answerReplies[answer]
    if (cached != null && answerContext == answerContext(selected ?: return generate(), readDraft() ?: return generate())) {
      replies = cached.first; replyIndex = cached.second.coerceIn(0, cached.first.lastIndex); rejectedReplies = emptyList()
      bubbleEnter = 0; tabFrom = -1; render()
    } else generate()
  }
  // Replies already written for each answer to the open question, valid while nothing they depend on changes.
  private val answerReplies = mutableMapOf<String, Pair<List<JSONObject>, Int>>()
  private var answerContext = ""
  private fun answerContext(id: String, draft: String): String {
    val tone = runtime.selectedTone(id)
    val newest = runtime.store.room(id)?.optJSONArray("messages")?.let { if (it.length() == 0) "" else it.getJSONObject(it.length() - 1).optString("id") }.orEmpty()
    return listOf(id, decision?.optString("messageId").orEmpty(), draft, tone, runtime.selectedIntensity(id, tone), newest).joinToString("\u0000")
  }
  private fun rememberAnswer(context: String, answer: String, written: List<JSONObject>) {
    if (context != answerContext) { answerReplies.clear(); answerContext = context }
    answerReplies[answer] = written to 0
  }
  private fun answerLabel(answer: String) = when (answer) {
    "yes" -> s(R.string.cue_kb_decision_yes); "no" -> s(R.string.cue_kb_decision_no); else -> s(R.string.cue_kb_decision_unsure)
  }

  private fun planStatus(plan: JSONObject?): String {
    if (plan == null) return s(R.string.cue_kb_goal_planning)
    val steps = plan.optJSONArray("steps")
    val stage = plan.optInt("stage", 1)
    if (plan.optString("moment") == "done") return s(R.string.cue_kb_moment_done)
    val step = steps?.optString(stage - 1).orEmpty()
    val head = s(R.string.cue_kb_goal_stage, stage, steps?.length() ?: stage) + (if (step.isBlank()) "" else ": $step") + " · " + momentLabel(plan)
    return plan.optString("note").ifBlank { null }?.let { "$head\n$it" } ?: head
  }
  private fun momentLabel(plan: JSONObject) = when (plan.optString("moment")) {
    "good" -> s(R.string.cue_kb_moment_good)
    "paused" -> s(R.string.cue_kb_moment_paused)
    "done" -> s(R.string.cue_kb_moment_done)
    "dropped" -> s(R.string.cue_kb_moment_dropped)
    else -> s(R.string.cue_kb_moment_wait)
  }

  // Same editor as the goal: what the user knows about a new person becomes the opener's hook.
  private fun renderOpener() {
    val toolbar = brandToolbar()
    toolbar.addView(editorBack { composingOpener = false; endSearch(); open(Mode.PEOPLE) }, LinearLayout.LayoutParams(dp(44), dp(44)))
    toolbar.addView(editorTitle(s(R.string.cue_kb_new_person_title)), LinearLayout.LayoutParams(0, dp(44), 1f))
    toolbar.addView(button(s(R.string.cue_kb_write), true) { startOpener() }, LinearLayout.LayoutParams(dp(96), dp(44)))
    finishToolbar(toolbar); panel.addView(toolbar, LinearLayout.LayoutParams(-1, dp(56)))
    val body = editorPanel(s(R.string.cue_kb_new_person_hint), s(R.string.cue_kb_new_person_title), openerContext.orEmpty(), 500)
    val ideas = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    fillIdeas(ideas, CueLanguage.resources(this).getStringArray(R.array.cue_kb_opener_ideas).toList())
    body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(ideas) },
      LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
    panel.announceForAccessibility(s(R.string.cue_kb_new_person_announcement))
  }
  private fun startOpener() {
    openerContext = searchEditor?.text?.toString()?.trim().orEmpty()
    selected = null; undo = null
    endSearch(); typing(); generate()
  }

  private fun editorBack(action: () -> Unit) = ImageButton(this).apply {
    setImageDrawable(getDrawable(R.drawable.cue_chevron_down)?.mutate()?.apply { setTint(ink) }); rotation = 90f
    background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), null, rounded(Color.WHITE, 12)); scaleType = ImageView.ScaleType.CENTER_INSIDE
    setPadding(dp(14), dp(14), dp(14), dp(14)); contentDescription = s(R.string.cue_kb_back_without_saving)
    setOnClickListener { action() }
  }
  private fun editorTitle(text: String) = label(text, 15f, true).apply {
    gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(4), 0, dp(4), 0)
  }
  private fun editorPanel(hintText: String, description: String, initial: String, maxLength: Int): LinearLayout {
    goalSelectionStart = -1; goalSelectionEnd = -1
    val editor = object : EditText(this) {
      override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        post { if (isEditor(mode) && searchEditor === this) syncGoalSelection(this) }
      }
    }.apply {
      textSize = 14f; setTextColor(ink); setHintTextColor(muted)
      hint = hintText; contentDescription = description
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
      filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
      isFocusable = true; isFocusableInTouchMode = true
      isCursorVisible = true; showSoftInputOnFocus = false
      gravity = Gravity.TOP or Gravity.START; background = rounded(card, 12)
      setPadding(dp(12), dp(10), dp(12), dp(10)); minLines = 2; maxLines = 3
      setText(restoredEditorText ?: initial); setSelection(text.length)
    }
    restoredEditorText = null
    searchEditor = editor
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(4)) }
    body.addView(editor, LinearLayout.LayoutParams(-1, dp(68)))
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
      if (isEditor(mode) && searchEditor === editor) {
        editor.requestFocus(); syncGoalSelection(editor)
      }
    }
    return body
  }

  private val ideasRequested = mutableSetOf<String>()
  private fun fillIdeas(row: LinearLayout, ideas: List<String>) {
    row.removeAllViews()
    ideas.forEach { idea ->
      row.addView(button(idea, false) {
        val editor = searchEditor ?: return@button
        editor.setText(idea + " "); editor.setSelection(editor.text.length); editor.requestFocus(); syncGoalSelection(editor)
      }.apply {
        textSize = 13f; setTextColor(ink); setPadding(dp(12), 0, dp(12), 0)
        background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(card, 14), null)
      }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
    }
  }
  // Conversations analysed before goal and topic ideas existed get them once, as soon as the person is picked.
  private fun prefetchIdeas(id: String) {
    if ((runtime.goalIdeas(id).isNotEmpty() && runtime.topicIdeas(id).isNotEmpty()) || !ideasRequested.add(id)) return
    scope.launch {
      val ok = try { withContext(Dispatchers.IO) { runtime.prefetchIdeas(id) }; true }
        catch (error: CancellationException) { throw error } catch (error: Exception) { false }
      if (!ok) ideasRequested.remove(id)
      if (selected != id) return@launch
      if (mode == Mode.TOPIC) render()
      if (mode == Mode.STYLES) render()
    }
  }
  // A new goal gets its route straight away; the indicator fills in once it arrives.
  private fun planGoal(id: String) {
    scope.launch {
      try { withContext(Dispatchers.IO) { runtime.planGoal(id) } }
      catch (error: CancellationException) { throw error } catch (_: Exception) { return@launch }
      if (selected == id && (mode == Mode.STYLES || mode == Mode.TYPING)) render()
    }
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
    planGoal(id)
    panel.announceForAccessibility(s(R.string.cue_kb_goal_saved))
  }

  private fun bindPlatform(icon: ImageView, room: JSONObject) {
    val platform = when (room.optString("network")) {
      "messenger" -> R.drawable.cue_network_messenger to "Messenger"
      "whatsapp" -> R.drawable.cue_network_whatsapp to "WhatsApp"
      "instagram" -> R.drawable.cue_network_instagram to "Instagram"
      else -> null
    }
    icon.visibility = if (platform == null) View.GONE else View.VISIBLE
    icon.setImageResource(platform?.first ?: 0)
    icon.contentDescription = platform?.second
  }

  private fun choosePerson(room: JSONObject) {
    selected = room.optString("id"); undo = null; openerContext = null; activeTopic = ""; pendingTopic = ""; question = null; decision = null
    prefetchIdeas(room.optString("id"))
    val shouldGenerate = generateAfterChoice
    val editor = afterChoice; afterChoice = null
    typing()
    if (editor != null) open(editor) else if (shouldGenerate) generate()
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
  private fun generate(again: Boolean = false, topic: String? = null) {
    val id = selected
    // A yes/no question from the other person is settled by the user first; a typed draft already says what they mean.
    if (id != null && !again && topic == null) {
      val asked = runtime.openQuestion(id)
      if (decision?.optString("messageId") != asked?.optString("messageId")) decision = null
      if (asked != null && decision == null && readDraft().isNullOrBlank()) { question = asked; open(Mode.DECISION); return }
    }
    // A topic picked on the style panel goes with the next fresh suggestion, then the tile clears.
    // An answered question comes first; a picked topic then waits for a later suggestion.
    val answering = decision?.optString("answer") in ConversationGoal.ANSWERS
    if (topic != null) activeTopic = topic
    else if (!again && answering) activeTopic = ""
    else if (!again) { activeTopic = pendingTopic; pendingTopic = "" }
    val opener = if (id == null) openerContext ?: return open(Mode.PEOPLE) else null
    // A generation left running for another editor must not block this one.
    if (job?.isActive == true && parked?.token != null && adoptedToken == -1) { job?.cancel(); parked = null }
    if (job?.isActive == true || !available) return
    // Everything shown so far was not good enough; the next round should go elsewhere.
    rejectedReplies = if (again) (rejectedReplies + replies.map { it.optString("text") }.filter(String::isNotBlank)).takeLast(6) else emptyList()
    val snapshot = readDraft()
    if (snapshot == null) { errorMessage = s(R.string.cue_kb_draft_unreadable); open(Mode.ERROR); return }
    draft = snapshot; undo = null; parked = null; adoptedToken = -1
    val token = revision
    val tone = runtime.selectedTone(id)
    // Written for one answer to the open question: kept so switching back to it needs no new request.
    val answeredWith = decision?.optString("answer")?.takeIf { it in ConversationGoal.ANSWERS }
    val context = if (id != null && answeredWith != null) answerContext(id, snapshot) else null
    open(Mode.LOADING)
    job = scope.launch {
      try {
        val profile = withContext(Dispatchers.IO) { JSONObject(
          if (id != null) runtime.analyze(id, snapshot, tone, rejected = rejectedReplies, topic = activeTopic,
            decision = decision?.takeIf { it.optString("answer") in ConversationGoal.ANSWERS })
          else runtime.openNewPerson("", opener.orEmpty(), snapshot, rejectedReplies)) }
        val suggestions = profile.getJSONArray("suggestions")
        val fresh = (0 until suggestions.length()).map { suggestions.getJSONObject(it) }
          .filter { it.optString("action") == "no_reply" || it.optString("text").isNotBlank() }
        if (context != null && answeredWith != null && fresh.isNotEmpty()) rememberAnswer(context, answeredWith, fresh)
        val waiting = parked?.takeIf { it.token == token }
        if (token != revision && token != adoptedToken) {
          // Finished while the keyboard was hidden: keep the replies for when the user comes back.
          if (waiting != null) parked = if (fresh.isEmpty()) null else waiting.copy(mode = Mode.REPLIES, replies = fresh, index = 0, at = System.currentTimeMillis(), token = null)
          return@launch
        }
        parked = null; adoptedToken = -1
        replies = fresh
        if (replies.isEmpty()) throw IllegalStateException("Brak propozycji")
        replyIndex = 0; open(Mode.REPLIES)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        val message = if (!runtime.prefs.getBoolean("cloud", false)) s(R.string.cue_kb_enable_ai) else s(R.string.cue_kb_generate_failed)
        if (token == revision || token == adoptedToken) {
          parked = null; adoptedToken = -1
          errorMessage = message
          open(Mode.ERROR)
        } else parked?.takeIf { it.token == token }?.let {
          // Failed while hidden: the user sees the error on return instead of a silently reset keyboard.
          parked = it.copy(mode = Mode.ERROR, error = message, at = System.currentTimeMillis(), token = null)
        }
      }
    }
  }

  private var replyIndex = 0
  private var bubbleEnter = 0
  private var tabFrom = -1
  private fun showReplies(body: LinearLayout) {
    replyIndex = replyIndex.coerceIn(0, replies.lastIndex)
    val suggestion = replies[replyIndex]
    val noReply = suggestion.optString("action") == "no_reply"
    fun pick(index: Int) {
      if (index == replyIndex || index !in replies.indices) return
      val toolbarBefore = toolbarMotion?.capture(panel.getChildAt(0))
      bubbleEnter = if (index > replyIndex) 1 else -1
      tabFrom = replyIndex
      replyIndex = index; render()
      toolbarMotion?.change(toolbarBefore, panel.getChildAt(0))
    }
    // The answer the replies were written for, as a three-way switch: tapping another answer shows its replies,
    // straight from memory when they were written already.
    // It sits first: what to answer comes before how.
    decision?.takeIf { it.optString("answer") in ConversationGoal.ANSWERS }?.let { chosen ->
      val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
      row.addView(label(s(R.string.cue_kb_decision_your_answer), 12.5f).apply { setTextColor(muted); setPadding(dp(4), 0, dp(8), 0) })
      listOf("yes" to "👍", "no" to "👎", "unsure" to "🤷").forEach { (answer, emoji) ->
        val active = answer == chosen.optString("answer")
        row.addView(button("$emoji  ${answerLabel(answer)}", false) { if (!active) reanswer(answer) }.apply {
          textSize = 13f; setPadding(dp(12), 0, dp(12), 0)
          typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
          setTextColor(if (active) onAccent else ink)
          background = RippleDrawable(ColorStateList.valueOf(0x335F78B8), rounded(if (active) accent else card, 16), null)
          contentDescription = answerLabel(answer) + if (active) s(R.string.cue_kb_selected) else ""
        }, LinearLayout.LayoutParams(-2, dp(32)).apply { marginEnd = dp(6) })
      }
      body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) },
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    // One accent bar slides between the tabs; it is null when there is only one reply.
    var indicator: View? = null
    val tabViews = mutableListOf<View>()
    fun tabX(index: Int, bar: View) = tabViews.getOrNull(index)?.let { it.left + (it.width - bar.width) / 2f } ?: 0f
    if (replies.size > 1) {
      // Text tabs like the style picker: the chosen one is bold with a short accent bar underneath.
      val frame = FrameLayout(this)
      val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
      replies.forEachIndexed { index, reply ->
        val active = index == replyIndex
        tabs.addView(button(if (reply.optString("action") == "no_reply") s(R.string.cue_kb_no_reply) else reply.optString("tone").ifBlank { "${index + 1}" }, false) { pick(index) }.apply {
          textSize = if (active) 15f else 14f; maxLines = 1; setPadding(dp(4), 0, dp(4), dp(4))
          // Long approach names shrink to fit rather than being cut off.
          androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 10, if (active) 15 else 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
          typeface = Typeface.create(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
          setTextColor(if (active) accent else muted)
          background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), null, rounded(Color.WHITE, 12))
          contentDescription = s(R.string.cue_kb_suggestion_description, index + 1, replies.size, text) + if (active) s(R.string.cue_kb_selected) else ""
        }.also(tabViews::add), LinearLayout.LayoutParams(0, dp(40), 1f))
      }
      frame.addView(tabs, FrameLayout.LayoutParams(-1, -1))
      val bar = View(this).apply { background = rounded(accent, 2) }
      frame.addView(bar, FrameLayout.LayoutParams(dp(22), dp(3), Gravity.BOTTOM or Gravity.START).apply { bottomMargin = dp(4) })
      indicator = bar
      body.addView(frame, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(2) })
      val from = tabFrom; tabFrom = -1
      bar.doOnPreDraw {
        val target = tabX(replyIndex, bar)
        if (from !in replies.indices || !android.animation.ValueAnimator.areAnimatorsEnabled()) { bar.translationX = target; return@doOnPreDraw }
        // The bar slides over, stretching mid-flight like a drop, while the tabs it passes ripple upward.
        val origin = tabX(from, bar)
        val settle = android.view.animation.OvershootInterpolator(1.2f)
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 420
          addUpdateListener { animator ->
            val f = animator.animatedFraction
            bar.translationX = origin + (target - origin) * settle.getInterpolation(f)
            bar.scaleX = 1f + 1.4f * kotlin.math.sin(Math.PI * (f * 1.5f).coerceAtMost(1f)).toFloat()
          }
          start()
        }
        val low = minOf(from, replyIndex); val high = maxOf(from, replyIndex)
        for (i in low..high) {
          val tab = tabViews[i]; val step = kotlin.math.abs(i - from)
          tab.animate().translationY(-dp(4).toFloat()).setStartDelay(step * 55L).setDuration(130)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction { tab.animate().translationY(0f).setStartDelay(0).setDuration(320).setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start() }
            .start()
        }
      }
    }
    val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = true; clipChildren = false; clipToPadding = false }
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
    // The reply sits in a full-width rounded card.
    val bubble = label(if (noReply) s(R.string.cue_kb_no_reply_needed) else suggestion.getString("text"), 15f, noReply).apply {
      setLineSpacing(dp(3).toFloat(), 1f); setTextIsSelectable(false); setPadding(dp(20), dp(16), dp(20), dp(16))
      background = rounded(androidx.core.graphics.ColorUtils.blendARGB(card, accent, if (dark) 0.18f else 0.12f), 14).apply {
        setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(accent, if (dark) 0x38 else 0x28))
      }
    }
    content.addView(bubble, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, 0) })
    if (bubbleEnter != 0) {
      // The next cloud glides in from the side it was swiped from and settles with a soft overshoot.
      val direction = bubbleEnter; bubbleEnter = 0
      bubble.alpha = 0f
      bubble.doOnPreDraw {
        it.translationX = direction * it.width * 0.45f; it.rotation = direction * 3f; it.scaleX = 0.95f; it.scaleY = 0.95f; it.alpha = 0.2f
        it.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).rotation(0f).setDuration(420)
          .setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start()
      }
    }
    val reason = suggestion.optString("reason").ifBlank { if (noReply) s(R.string.cue_kb_come_back_later) else "" }
    if (reason.isNotBlank()) {
      val step = when (suggestion.optString("step")) { "goal" -> s(R.string.cue_kb_step_goal); "keep" -> s(R.string.cue_kb_step_keep); else -> null }
      content.addView(label(s(R.string.cue_kb_why) + (step?.let { " · $it" } ?: ""), 12f, true).apply { setTextColor(accent); setPadding(dp(4), dp(12), dp(4), dp(4)) })
      content.addView(label(reason, 13f).apply { setTextColor(muted); setLineSpacing(dp(2).toFloat(), 1f); setPadding(dp(4), 0, dp(4), 0) })
    }
    scroll.addView(content)
    // Drag the cloud sideways like a carousel card; past a third of the width it flies off to the next reply.
    var downX = 0f; var downY = 0f; var dragging = false
    val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
    fun follow(shift: Float) {
      val width = bubble.width.coerceAtLeast(1).toFloat()
      bubble.translationX = shift; bubble.rotation = shift / width * 4f
      bubble.alpha = 1f - (kotlin.math.abs(shift) / width).coerceAtMost(0.5f)
      val bar = indicator ?: return
      val neighbour = replyIndex + if (shift < 0) 1 else -1
      if (neighbour !in replies.indices) return
      val progress = (kotlin.math.abs(shift) / width).coerceAtMost(1f)
      bar.translationX = tabX(replyIndex, bar) + (tabX(neighbour, bar) - tabX(replyIndex, bar)) * progress * 0.6f
    }
    // Tapping the reply inserts it. Handled here rather than with a click listener so the drag keeps working.
    val tappable = !noReply
    var pressed = false
    fun press(down: Boolean) {
      pressed = down
      bubble.animate().scaleX(if (down) 0.97f else 1f).scaleY(if (down) 0.97f else 1f).setDuration(if (down) 90 else 240)
        .setInterpolator(if (down) android.view.animation.DecelerateInterpolator() else android.view.animation.OvershootInterpolator(2.5f)).start()
    }
    if (tappable) {
      bubble.contentDescription = s(R.string.cue_kb_insert) + ": " + bubble.text
      ViewCompat.addAccessibilityAction(bubble, s(R.string.cue_kb_insert)) { _, _ -> insert(suggestion.getString("text")); true }
    }
    scroll.setOnTouchListener { view, event ->
      when (event.actionMasked) {
        android.view.MotionEvent.ACTION_DOWN -> {
          downX = event.x; downY = event.y; dragging = false
          val y = event.y + scroll.scrollY
          if (tappable && event.x >= bubble.left && event.x <= bubble.right && y >= bubble.top && y <= bubble.bottom) press(true)
        }
        android.view.MotionEvent.ACTION_MOVE -> {
          val dx = event.x - downX
          if (pressed && (kotlin.math.abs(dx) > slop || kotlin.math.abs(event.y - downY) > slop)) press(false)
          if (!dragging && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > 1.5f * kotlin.math.abs(event.y - downY)) {
            dragging = true; view.parent?.requestDisallowInterceptTouchEvent(true); bubble.animate().cancel()
          }
          if (dragging) {
            val open = (replyIndex + if (dx < 0) 1 else -1) in replies.indices
            follow(if (open) dx else dx * 0.25f)
            return@setOnTouchListener true
          }
        }
        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> if (pressed) {
          press(false)
          if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
            val text = suggestion.getString("text")
            bubble.postDelayed({ if (mode == Mode.REPLIES) insert(text) }, 140)
            return@setOnTouchListener true
          }
        } else if (dragging) {
          dragging = false
          val dx = event.x - downX
          val target = replyIndex + if (dx < 0) 1 else -1
          if (event.actionMasked == android.view.MotionEvent.ACTION_UP && target in replies.indices && kotlin.math.abs(dx) > bubble.width / 3.5f) {
            val side = if (dx < 0) -1f else 1f
            bubble.animate().translationX(side * bubble.width).rotation(side * 7f).alpha(0f).setDuration(160)
              .setInterpolator(android.view.animation.AccelerateInterpolator()).withEndAction { pick(target) }.start()
          } else {
            bubble.animate().translationX(0f).rotation(0f).alpha(1f).setDuration(380)
              .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
            indicator?.let { bar -> bar.animate().translationX(tabX(replyIndex, bar)).setDuration(320).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start() }
          }
          return@setOnTouchListener true
        }
      }
      false
    }
    body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
    val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    footer.addView(button(s(R.string.cue_kb_other_ideas), false) { generate(again = true) }.apply {
      textSize = 14f; setTextColor(accent); contentDescription = s(R.string.cue_kb_other_ideas_description)
    }, LinearLayout.LayoutParams(0, dp(44), 1f))
    footer.addView(button(s(R.string.cue_kb_keyboard), false) { typing() }.apply {
      textSize = 14f; setTextColor(muted); contentDescription = s(R.string.cue_kb_keyboard_description)
    }, LinearLayout.LayoutParams(0, dp(44), 1f))
    body.addView(footer, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
  }

  private fun replace(expected: String, text: String): Boolean = available &&
    currentInputConnection?.let { KeyboardDraftEditor.replace(it, expected, text) } == true

  private fun insert(text: String) {
    if (!replace(draft, text)) {
      errorMessage = s(R.string.cue_kb_draft_changed); mode = Mode.ERROR; render(); return
    }
    undo = text to draft
    answerReplies.clear()
    typing()
    panel.announceForAccessibility(s(R.string.cue_kb_inserted))
  }

  private fun undoInsert(action: Pair<String, String>) {
    if (!replace(action.first, action.second)) {
      undo = null; render(); panel.announceForAccessibility(s(R.string.cue_kb_text_changed)); return
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
  private fun styleSummary(): String = CueLanguage.toneShortLabel(this, runtime.selectedTone(selected))
  private fun s(id: Int, vararg args: Any): String = CueLanguage.string(this, id, *args)
  // Name opens the people list; the style part opens goal, style and intensity.
  private fun addConversation(toolbar: LinearLayout) {
    val expanded = mode == Mode.STYLES
    val room = person()
    val goalSet = selected?.let(runtime::conversationGoal)?.isNotBlank() == true
    val name = button(room?.optString("name") ?: if (openerContext != null) s(R.string.cue_kb_new_person) else s(R.string.cue_kb_choose_person), false) { undo = null; generateAfterChoice = false; open(Mode.PEOPLE) }.apply {
      gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      styleRecipient(this, false); includeFontPadding = false
      contentDescription = s(R.string.cue_kb_conversation_description, room?.optString("name") ?: s(R.string.cue_kb_not_selected))
    }
    val style = button(styleSummary(), false) { undo = null; generateAfterChoice = false; if (expanded) typing() else open(Mode.STYLES) }.apply {
      textSize = 14f; letterSpacing = -0.015f; setTextColor(muted); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false; setPadding(dp(8), 0, dp(8), 0)
      val chevron = getDrawable(R.drawable.cue_chevron_down)?.mutate()?.apply { setTint(muted); setBounds(0, 0, dp(12), dp(12)) }
      val arrow = chevron?.let { android.graphics.drawable.RotateDrawable().apply {
        drawable = it; fromDegrees = 0f; toDegrees = 180f; setBounds(0, 0, dp(12), dp(12)); level = if (expanded) 10000 else 0 } }
      setCompoundDrawablesRelative(if (goalSet) goalIndicator(dp(12)) else null, null, arrow, null)
      compoundDrawablePadding = dp(5)
      contentDescription = s(R.string.cue_kb_style_description, styleSummary()) + (if (goalSet) s(R.string.cue_kb_goal_set) + planStatus(selected?.let(runtime::goalPlan)).substringBefore('\n') + ". " else ". ") + if (expanded) s(R.string.cue_kb_close_settings) else s(R.string.cue_kb_change_goal_style)
    }
    conversationStyle = style
    val cell = KeyboardConversationRow(this, name, style, dp(110))
    toolbar.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
  }

  private fun renderTones() {
    selected?.let(::prefetchIdeas)
    val keys = WritingTone.labels.keys.toList()
    var tone = runtime.selectedTone(selected)
    val navigation = ViewCompat.getRootWindowInsets(panel)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: dp(24)
    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), navigation + dp(4)) }
    // Goal on the left, topics on the right: each opens its own editor.
    val goalText = selected?.let(runtime::conversationGoal)?.ifBlank { null }
    val plan = selected?.let(runtime::goalPlan)
    val sections = LinearLayout(this)
    val id = selected
    val finished = if (goalText == null) id?.let(runtime::finishedGoal) else null
    val goalSection = when {
      goalText != null -> section(
        if (plan == null) s(R.string.cue_kb_goal) else "${plan.optInt("stage", 1)}/${plan.optJSONArray("steps")?.length() ?: 1} · ${momentLabel(plan)}",
        goalText, true, goalIndicator(dp(14)),
        s(R.string.cue_kb_goal_description, goalText, planStatus(plan).substringBefore('\n')),
        end = { if (id != null) { runtime.setConversationGoal(id, ""); replies = emptyList(); render(); panel.announceForAccessibility(s(R.string.cue_kb_goal_ended)) } }
      ) { openEditor(Mode.GOAL) }
      // Ended on its own: a short note instead of an active goal, gone once the user looks or after a day.
      finished != null -> section(
        s(if (finished.optString("outcome") == "done") R.string.cue_kb_moment_done else R.string.cue_kb_moment_dropped),
        finished.optString("goal"), false, KeyboardGoalIndicator(muted, dp(14), "done", 1f),
        s(R.string.cue_kb_goal_description, finished.optString("goal"), s(R.string.cue_kb_add_goal)), muted = true
      ) { id?.let(runtime::dismissFinishedGoal); openEditor(Mode.GOAL) }
      else -> section(s(R.string.cue_kb_goal), s(R.string.cue_kb_add_goal), false, null,
        s(R.string.cue_kb_goal_description, s(R.string.cue_kb_not_set), s(R.string.cue_kb_add_goal))) { openEditor(Mode.GOAL) }
    }
    sections.addView(goalSection, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(4) })
    // The picked topic waits here for the next suggestion; ✕ drops it.
    val topic = pendingTopic.ifBlank { null }
    sections.addView(section(s(R.string.cue_kb_topics), topic ?: s(R.string.cue_kb_topic_empty), topic != null, null,
      s(R.string.cue_kb_topic_description), end = topic?.let { { pendingTopic = ""; render() } }, endDescription = s(R.string.cue_kb_clear_topic)) { openEditor(Mode.TOPIC) },
      LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(4) })
    val intensity = FrameLayout(this)
    val hint = label("", 15f).apply { setTextColor(ink); gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    fun showIntensity() {
      intensity.removeAllViews()
      val levels = CueLanguage.toneLevels(this, tone)
      hint.text = CueLanguage.toneHint(this, tone, runtime.selectedIntensity(selected, tone))
      if (levels.isEmpty()) return
      intensity.addView(KeyboardLevelSlider(this, levels, runtime.selectedIntensity(selected, tone), ink, muted, accent, onAccent) { level ->
        runtime.setWritingIntensity(selected, tone, level); replies = emptyList()
        hint.text = CueLanguage.toneHint(this, tone, level); conversationStyle?.text = styleSummary()
      }, FrameLayout.LayoutParams(-1, -1))
    }
    content.addView(KeyboardSwipeSelector(this, keys.map { if (it == "calming") CueLanguage.toneShortLabel(this, it) else CueLanguage.toneLabel(this, it) }, keys.indexOf(tone).coerceAtLeast(0), 20f, ink, muted, accent,
      { s(R.string.cue_kb_reply_style, it) }) { position ->
      tone = keys[position]
      runtime.setWritingTone(selected, tone); replies = emptyList()
      conversationStyle?.text = styleSummary()
      showIntensity()
    }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(6) })
    content.addView(intensity, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(10); marginStart = dp(8); marginEnd = dp(8) })
    content.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); marginStart = dp(20); marginEnd = dp(20) })
    // Goal and topic sit below the style, as extras to the reply.
    content.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
    // Clear of the IME switcher and hide buttons drawn over the bottom edge.
    content.addView(sections, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(12); bottomMargin = dp(24) })
    showIntensity()
    panel.addView(content, LinearLayout.LayoutParams(-1, pickerHeight))
  }

  private fun openEditor(editor: Mode) {
    generateAfterChoice = false
    if (selected == null) { afterChoice = editor; open(Mode.PEOPLE) } else open(editor)
  }
  private fun section(caption: String, value: String, filled: Boolean, icon: android.graphics.drawable.Drawable?, description: String,
    muted: Boolean = false, end: (() -> Unit)? = null, endDescription: String = s(R.string.cue_kb_end_goal), action: () -> Unit) = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), if (end == null) dp(10) else dp(2), dp(8))
    background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), rounded(card, 16), null)
    val texts = LinearLayout(this@SubtextKeyboard).apply { orientation = LinearLayout.VERTICAL }
    texts.addView(label(caption, 12f).apply {
      setTextColor(this@SubtextKeyboard.muted); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
      if (icon != null) { setCompoundDrawablesRelative(icon, null, null, null); compoundDrawablePadding = dp(6) }
    })
    texts.addView(label(value, 14f, filled).apply {
      setTextColor(if (filled) ink else if (muted) this@SubtextKeyboard.muted else accent); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0)
    })
    addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
    // An empty section shows + so it reads as "add"; a filled one opens its editor on tap.
    if (!filled && !muted) addView(android.widget.ImageView(this@SubtextKeyboard).apply {
      setImageDrawable(getDrawable(R.drawable.cue_plus)?.mutate()?.apply { setTint(onAccent) })
      background = rounded(accent, 13)
      scaleType = android.widget.ImageView.ScaleType.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = dp(6) })
    // Ends the goal or drops the topic in one tap, without opening the editor.
    if (end != null) addView(android.widget.ImageButton(this@SubtextKeyboard).apply {
      setImageDrawable(getDrawable(R.drawable.cue_close)?.mutate()?.apply { setTint(this@SubtextKeyboard.muted) })
      background = RippleDrawable(ColorStateList.valueOf(0x225F78B8), null, rounded(Color.WHITE, 18))
      contentDescription = endDescription; setOnClickListener { end() }
    }, LinearLayout.LayoutParams(dp(36), dp(36)))
    contentDescription = description; setOnClickListener { action() }
  }
  // Remembers what each conversation's arc last showed, so a new stage sweeps forward instead of jumping.
  private val shownProgress = mutableMapOf<String, Float>()
  private var progressBase = emptyMap<String, Float>()
  private fun goalIndicator(size: Int): KeyboardGoalIndicator {
    val id = selected
    val indicator = KeyboardGoalIndicator.of(id?.let(runtime::goalPlan), ink, size)
    val from = id?.let(progressBase::get)
    if (id != null) shownProgress[id] = indicator.target
    if (from != null && from != indicator.target && android.animation.ValueAnimator.areAnimatorsEnabled()) {
      indicator.fraction = from
      android.animation.ValueAnimator.ofFloat(from, indicator.target).apply {
        duration = 700; interpolator = android.view.animation.DecelerateInterpolator()
        addUpdateListener { indicator.fraction = it.animatedValue as Float }
        start()
      }
    }
    return indicator
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
    val primary = mode == Mode.TYPING || mode == Mode.REPLIES || mode == Mode.STYLES || isEditor(mode)
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
    if (keyCode == KeyEvent.KEYCODE_BACK && mode != Mode.TYPING) { backHandled = true; if (mode == Mode.SEARCH) open(Mode.PEOPLE) else if (mode == Mode.GOAL) { endSearch(); if (composingOpener) { composingOpener = false; open(Mode.PEOPLE) } else open(Mode.STYLES) } else if (mode == Mode.TOPIC) open(Mode.STYLES) else if (mode == Mode.DECISION) typing() else typing(); return true }
    if (isEditor(mode)) return searchEditor?.dispatchKeyEvent(event) ?: true
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
    if (isEditor(mode)) return searchEditor?.dispatchKeyEvent(event) ?: true
    return super.onKeyUp(keyCode, event)
  }
  override fun onFinishInputView(finishingInput: Boolean) {
    pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel()
    park()
    revision++; adoptedToken = -1; if (!backgroundLoading()) job?.cancel(); endSearch(); restoreKeys(); mode = Mode.TYPING
    super.onFinishInputView(finishingInput)
  }
  override fun onFinishInput() { pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel(); park(); revision++; adoptedToken = -1; if (!backgroundLoading()) job?.cancel(); selected = null; undo = null; openerContext = null; composingOpener = false; endSearch(); restoreKeys(); mode = Mode.TYPING; super.onFinishInput() }
  override fun onDestroy() { chevronAnimator?.cancel(); pickerMotion?.cancel(); toolbarMotion?.cancel(); panelMotion.cancel(); scope.cancel(); super.onDestroy() }
}
