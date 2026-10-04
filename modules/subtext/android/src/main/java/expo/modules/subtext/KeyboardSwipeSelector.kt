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
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/** Carousel with one option in the middle; it follows the finger and selects whatever it settles on. */
@SuppressLint("ViewConstructor")
internal class KeyboardSwipeSelector(
  context: Context,
  private val options: List<String>,
  private var index: Int,
  private val size: Float,
  private val ink: Int,
  private val muted: Int,
  private val accent: Int,
  private val describe: (String) -> String,
  private val onChange: (Int) -> Unit
) : View(context) {
  private val density = resources.displayMetrics.density
  private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
  private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
  private var position = index.toFloat()
  private var startX = 0f
  private var startPosition = 0f
  private var dragging = false
  private var nearest = index
  private var velocity: VelocityTracker? = null
  private var animator: ValueAnimator? = null

  init { isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; bindDescription() }

  private val itemWidth get() = width / 2.6f

  override fun onDraw(canvas: Canvas) {
    val baseline = height / 2f - 2 * density
    options.forEachIndexed { option, label ->
      val distance = abs(option - position)
      if (distance > 2f) return@forEachIndexed
      val focus = (1f - distance).coerceIn(0f, 1f)
      text.typeface = Typeface.create(Typeface.DEFAULT, if (focus > 0.5f) Typeface.BOLD else Typeface.NORMAL)
      text.textSize = size * density * (0.72f + 0.28f * focus)
      // Long names shrink to their slot so neighbours never overlap.
      val room = itemWidth - 16 * density
      val measured = text.measureText(label)
      if (measured > room) text.textSize *= room / measured
      text.color = blend(ink, accent, focus)
      text.alpha = (255 * (0.35f + 0.65f * focus)).toInt()
      canvas.drawText(label, width / 2f + (option - position) * itemWidth, baseline + text.textSize / 3, text)
    }
    val gap = 12 * density
    val dotsLeft = width / 2f - gap * (options.size - 1) / 2
    options.indices.forEach { option ->
      val active = option == nearest
      dot.color = if (active) accent else muted; dot.alpha = if (active) 255 else 100
      canvas.drawCircle(dotsLeft + option * gap, height - 10 * density, 3 * density, dot)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    val last = options.lastIndex.toFloat()
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        parent?.requestDisallowInterceptTouchEvent(true)
        animator?.cancel(); dragging = false
        startX = event.x; startPosition = position
        velocity?.recycle(); velocity = VelocityTracker.obtain().also { it.addMovement(event) }
      }
      MotionEvent.ACTION_MOVE -> {
        velocity?.addMovement(event)
        val dx = event.x - startX
        if (abs(dx) > 6 * density) dragging = true
        if (dragging) {
          var next = startPosition - dx / itemWidth
          if (next < 0f) next *= 0.3f else if (next > last) next = last + (next - last) * 0.3f
          position = next; updateNearest(); invalidate()
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        velocity?.addMovement(event); velocity?.computeCurrentVelocity(1000)
        val speed = velocity?.xVelocity ?: 0f
        velocity?.recycle(); velocity = null
        val target = when {
          !dragging && event.actionMasked == MotionEvent.ACTION_UP ->
            (startPosition + ((event.x - width / 2f) / itemWidth).roundToInt()).roundToInt()
          abs(speed) > 600 * density && abs(position - startPosition) < 0.5f ->
            startPosition.roundToInt() + if (speed < 0) 1 else -1
          else -> position.roundToInt()
        }
        settle(target.coerceIn(0, options.lastIndex))
      }
    }
    return true
  }

  private fun updateNearest() {
    val next = position.roundToInt().coerceIn(0, options.lastIndex)
    if (next != nearest) { nearest = next; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
  }

  private fun settle(target: Int) {
    nearest = target
    if (target != index) { index = target; onChange(index); bindDescription(); announceForAccessibility(describe(options[index])) }
    animator?.cancel()
    animator = ValueAnimator.ofFloat(position, target.toFloat()).apply {
      duration = 220; interpolator = DecelerateInterpolator()
      addUpdateListener { position = it.animatedValue as Float; invalidate() }
      start()
    }
  }

  private fun bindDescription() {
    contentDescription = describe(options[index]) + CueLanguage.string(context, R.string.cue_kb_swipe_position, index + 1, options.size)
  }

  override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
    super.onInitializeAccessibilityNodeInfo(info)
    if (index > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    if (index < options.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
  }

  override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
    val step = when (action) {
      AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> 1
      AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -1
      else -> return super.performAccessibilityAction(action, arguments)
    }
    if (index + step !in options.indices) return false
    settle(index + step); return true
  }

  override fun onDetachedFromWindow() { animator?.cancel(); velocity?.recycle(); velocity = null; super.onDetachedFromWindow() }

  private fun blend(from: Int, to: Int, amount: Float) = Color.rgb(
    (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
    (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
    (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt())
}
