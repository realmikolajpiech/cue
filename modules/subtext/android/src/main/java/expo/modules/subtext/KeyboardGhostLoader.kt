package expo.modules.subtext

import android.animation.ValueAnimator
import android.content.Context
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The Cue ghost wanders over the whole panel along a slow looping path while a reply is being written. */
internal class KeyboardGhostLoader(context: Context, private val message: String, textColor: Int, private val sparkleColor: Int) : FrameLayout(context) {
  private val density = resources.displayMetrics.density
  private fun dp(value: Float) = value * density
  private val ghostSize = dp(76f).toInt()
  private val ghost = ImageView(context).apply {
    setImageResource(R.drawable.cue_ghost_write); scaleType = ImageView.ScaleType.FIT_CENTER
    contentDescription = null; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }
  private val label = TextView(context).apply {
    text = message; textSize = 15f; setTextColor(textColor); includeFontPadding = false; gravity = Gravity.CENTER
  }
  private var animator: ValueAnimator? = null
  private var lastSparkle = 0L

  init {
    contentDescription = message
    addView(ghost, LayoutParams(ghostSize, ghostSize, Gravity.TOP or Gravity.START))
    addView(label, LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(16f).toInt() })
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!ValueAnimator.areAnimatorsEnabled()) return
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 11000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
      addUpdateListener { frame(it.animatedValue as Float) }
      start()
    }
  }

  override fun onDetachedFromWindow() {
    animator?.cancel(); animator = null
    super.onDetachedFromWindow()
  }

  private fun frame(progress: Float) {
    val t = progress * 2 * PI
    // The free area above the label; the ghost stays around its middle instead of hugging the corners.
    val margin = dp(8f)
    val freeWidth = (width - ghostSize - 2 * margin).coerceAtLeast(0f)
    val freeHeight = (height - paddingBottom - label.height - dp(24f) - ghostSize - 2 * margin).coerceAtLeast(0f)
    // Lissajous loop (2:3) around the centre, without repeating a straight line, plus a quick bob.
    val bob = (sin(8 * t) * dp(3f)).toFloat()
    val x = margin + freeWidth * (0.5f + 0.27f * sin(2 * t).toFloat())
    val y = margin + (freeHeight - dp(6f)).coerceAtLeast(0f) * (0.5f + 0.32f * sin(3 * t + PI / 4).toFloat()) + dp(3f) + bob
    ghost.translationX = x; ghost.translationY = y
    ghost.rotation = (cos(2 * t) * 10).toFloat()
    val squash = (sin(8 * t) * 0.03).toFloat()
    ghost.scaleX = 1f - squash; ghost.scaleY = 1f + squash
    label.text = message.trimEnd('…', '.') + ".".repeat(1 + (progress * 18).toInt() % 3)
    val now = System.currentTimeMillis()
    if (now - lastSparkle > 450) { lastSparkle = now; sparkle(x, y, cos(2 * t) > 0) }
  }

  // Small stars puff out behind the ghost and float away.
  private fun sparkle(x: Float, y: Float, movingRight: Boolean) {
    val star = TextView(context).apply {
      text = "✦"; textSize = 10f + (lastSparkle % 3) * 3; setTextColor(sparkleColor); includeFontPadding = false
      importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    addView(star, 0, LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
    // Stars trail behind the ghost, on the side it is flying away from.
    val behind = if (movingRight) -1 else 1
    star.translationX = x + ghostSize / 2f + behind * dp(30f); star.translationY = y + ghostSize / 2f; star.alpha = 0f
    star.animate().translationYBy(-dp(18f)).translationXBy(behind * dp(10f)).rotation(behind * 40f).alpha(1f).setDuration(400)
      .withEndAction { star.animate().alpha(0f).translationYBy(-dp(10f)).setDuration(600).withEndAction { removeView(star) }.start() }.start()
  }
}
