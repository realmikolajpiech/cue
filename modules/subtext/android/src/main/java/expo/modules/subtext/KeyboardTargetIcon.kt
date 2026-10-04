package expo.modules.subtext

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Small muted "goal set" mark: a ring with a dot, drawn so it matches any text colour. */
internal class KeyboardTargetIcon(color: Int, private val size: Int) : Drawable() {
  private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = size / 9f }
  private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
  init { setBounds(0, 0, size, size) }
  override fun draw(canvas: Canvas) {
    val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
    canvas.drawCircle(cx, cy, size / 2f - ring.strokeWidth, ring)
    canvas.drawCircle(cx, cy, size / 7f, dot)
  }
  override fun getIntrinsicWidth() = size
  override fun getIntrinsicHeight() = size
  override fun setAlpha(alpha: Int) { ring.alpha = alpha; dot.alpha = alpha }
  override fun setColorFilter(colorFilter: ColorFilter?) { ring.colorFilter = colorFilter; dot.colorFilter = colorFilter }
  @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
