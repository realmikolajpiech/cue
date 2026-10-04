package expo.modules.subtext

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import org.json.JSONObject

/**
 * One-colour goal progress: a faint ring whose arc fills with each stage of the plan.
 * A good moment fills the centre solid; a reached goal turns into the target mark.
 */
internal class KeyboardGoalIndicator(color: Int, private val size: Int, val moment: String, val target: Float) : Drawable() {
  private val stroke = size / 8f
  private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke; alpha = 70 }
  private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
    if (moment == "paused") alpha = 120
  }
  private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
  private val oval = RectF()
  private val reached = KeyboardTargetIcon(color, size)
  /** Shown share of the arc, animatable towards [target]. */
  var fraction = target
    set(value) { field = value; invalidateSelf() }
  init { setBounds(0, 0, size, size) }

  override fun draw(canvas: Canvas) {
    if (moment == "done") { reached.bounds = bounds; reached.draw(canvas); return }
    val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
    val radius = size / 2f - stroke / 2f
    canvas.drawCircle(cx, cy, radius, track)
    oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
    if (fraction > 0f) canvas.drawArc(oval, -90f, 360f * fraction.coerceIn(0f, 1f), false, arc)
    if (moment == "good") canvas.drawCircle(cx, cy, radius - stroke * 1.3f, fill)
  }
  override fun getIntrinsicWidth() = size
  override fun getIntrinsicHeight() = size
  override fun setAlpha(alpha: Int) { track.alpha = alpha * 70 / 255; arc.alpha = alpha; fill.alpha = alpha; reached.alpha = alpha }
  override fun setColorFilter(colorFilter: ColorFilter?) { track.colorFilter = colorFilter; arc.colorFilter = colorFilter; fill.colorFilter = colorFilter; reached.colorFilter = colorFilter }
  @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

  companion object {
    /** Indicator for a saved plan; without one yet only the empty ring shows that a route is being planned. */
    fun of(plan: JSONObject?, color: Int, size: Int): KeyboardGoalIndicator {
      if (plan == null) return KeyboardGoalIndicator(color, size, "wait", 0f)
      val steps = plan.optJSONArray("steps")?.length()?.coerceAtLeast(1) ?: 1
      return KeyboardGoalIndicator(color, size, plan.optString("moment", "wait"), plan.optInt("stage", 1).coerceIn(1, steps) / steps.toFloat())
    }
  }
}
