package expo.modules.subtext

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** A rounded parallelogram leaning right. Keep horizontal content padding above [skew]. */
internal class KeyboardSlantBubble(fill: Int, stroke: Int, private val skew: Float, corner: Float, strokeWidth: Float) : Drawable() {
  private val rounding = CornerPathEffect(corner)
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL; pathEffect = rounding }
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke; style = Paint.Style.STROKE; this.strokeWidth = strokeWidth; pathEffect = rounding }
  private var shape = Path()

  override fun onBoundsChange(bounds: Rect) {
    val inset = strokePaint.strokeWidth / 2
    val left = bounds.left + inset; val right = bounds.right - inset
    val top = bounds.top + inset; val bottom = bounds.bottom - inset
    shape = Path().apply {
      moveTo(left + skew, top); lineTo(right, top); lineTo(right - skew, bottom); lineTo(left, bottom); close()
    }
  }

  override fun draw(canvas: Canvas) {
    canvas.drawPath(shape, fillPaint)
    if (strokePaint.strokeWidth > 0) canvas.drawPath(shape, strokePaint)
  }
  override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha; strokePaint.alpha = alpha; invalidateSelf() }
  override fun setColorFilter(colorFilter: ColorFilter?) { fillPaint.colorFilter = colorFilter; strokePaint.colorFilter = colorFilter; invalidateSelf() }
  @Deprecated("Deprecated in Java")
  override fun getOpacity() = PixelFormat.TRANSLUCENT
}
