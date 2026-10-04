package expo.modules.subtext

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/** Stepped slider (like low · medium · high): drag or tap, snaps to the nearest stop. */
@SuppressLint("ViewConstructor")
internal class KeyboardLevelSlider(
  context: Context,
  private val levels: List<String>,
  private var index: Int,
  private val ink: Int,
  private val muted: Int,
  private val accent: Int,
  private val onAccent: Int,
  private val onChange: (Int) -> Unit
) : View(context) {
  private val density = resources.displayMetrics.density
  private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4 * density; strokeCap = Paint.Cap.ROUND }
  private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
  private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * density; textAlign = Paint.Align.CENTER }
  private var position = index.toFloat()
  private var dragging = false
  private var animator: ValueAnimator? = null

  init {
    isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    describe()
  }

  private val left get() = paddingLeft + 20 * density
  private val right get() = width - paddingRight - 20 * density
  private val trackY get() = paddingTop + 16 * density
  private fun x(value: Float) = left + (right - left) * value / (levels.size - 1).coerceAtLeast(1)

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((56 * density).toInt() + paddingTop + paddingBottom, heightMeasureSpec))
  }

  override fun onDraw(canvas: Canvas) {
    val y = trackY
    track.color = withAlpha(muted, 0.35f); canvas.drawLine(left, y, right, y, track)
    track.color = accent; canvas.drawLine(left, y, x(position), y, track)
    levels.indices.forEach { stop ->
      dot.color = if (stop <= position + 0.01f) accent else withAlpha(muted, 0.6f)
      canvas.drawCircle(x(stop.toFloat()), y, 4 * density, dot)
    }
    dot.color = accent; canvas.drawCircle(x(position), y, (if (dragging) 13 else 11) * density, dot)
    dot.color = onAccent; canvas.drawCircle(x(position), y, 4 * density, dot)
    levels.forEachIndexed { stop, label ->
      val active = stop == index
      text.color = if (active) accent else muted
      text.typeface = Typeface.create(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
      val labelX = x(stop.toFloat()).coerceIn(left + text.measureText(label) / 2 - 20 * density, right - text.measureText(label) / 2 + 20 * density)
      canvas.drawText(label, labelX, y + 30 * density, text)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    val value = ((event.x - left) / (right - left) * (levels.size - 1)).coerceIn(0f, (levels.size - 1).toFloat())
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); animator?.cancel(); dragging = true; position = value; tick(); invalidate() }
      MotionEvent.ACTION_MOVE -> { position = value; tick(); invalidate() }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; select(value.roundToInt()) }
    }
    return true
  }

  // Live haptic + label update while dragging across a stop.
  private fun tick() {
    val nearest = position.roundToInt()
    if (nearest != index) { index = nearest; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onChange(index); describe() }
  }

  private fun select(stop: Int) {
    if (stop != index) { index = stop; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onChange(index); describe() }
    animator?.cancel()
    animator = ValueAnimator.ofFloat(position, stop.toFloat()).apply {
      duration = 140; addUpdateListener { position = it.animatedValue as Float; invalidate() }; start()
    }
  }

  private fun describe() { contentDescription = CueLanguage.string(context, R.string.cue_kb_intensity, levels[index], index + 1, levels.size) }

  override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
    super.onInitializeAccessibilityNodeInfo(info)
    info.className = "android.widget.SeekBar"
    if (index > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    if (index < levels.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
  }

  override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
    val step = when (action) {
      AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> 1
      AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -1
      else -> return super.performAccessibilityAction(action, arguments)
    }
    if (index + step !in levels.indices) return false
    select(index + step); announceForAccessibility(levels[index]); return true
  }

  private fun withAlpha(color: Int, alpha: Float) = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
