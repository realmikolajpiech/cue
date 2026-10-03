package expo.modules.subtext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.view.doOnPreDraw

/** Keep the brand anchored; only the title and action transition between states. */
internal class KeyboardToolbarMotion(private val frame: ViewGroup) {
  data class Snapshot(val image: Bitmap, val bounds: Rect, val mascot: Bitmap?, val anchor: Rect?)
  private var animator: ValueAnimator? = null
  private var overlay: Drawable? = null
  private var hidden: View? = null
  private var generation = 0

  fun cancel() {
    generation++
    animator?.removeAllListeners(); animator?.cancel(); animator = null
    overlay?.let { frame.overlay.remove(it) }; overlay = null
    hidden?.alpha = 1f; hidden = null
  }

  fun capture(toolbar: View?): Snapshot? {
    cancel()
    return snapshot(toolbar)
  }

  private fun snapshot(toolbar: View?): Snapshot? {
    if (toolbar !is ViewGroup || toolbar.width <= 0 || toolbar.height <= 0 || !ValueAnimator.areAnimatorsEnabled()) return null
    val mascot = (0 until toolbar.childCount).map(toolbar::getChildAt).filterIsInstance<ImageView>().firstOrNull()
    fun bounds(view: View) = Rect(0, 0, view.width, view.height).also { frame.offsetDescendantRectToMyCoords(view, it) }
    fun image(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    val icon = mascot?.takeIf { it.width > 0 && it.height > 0 }?.let(::image)
    val anchor = mascot?.let(::bounds)
    mascot?.alpha = 0f
    val bitmap = image(toolbar)
    mascot?.alpha = 1f
    return Snapshot(bitmap, bounds(toolbar), icon, anchor)
  }

  fun change(before: Snapshot?, toolbar: View?) {
    if (before == null || toolbar == null) return
    hidden = toolbar; toolbar.alpha = 0f
    val token = generation
    frame.doOnPreDraw {
      if (token != generation) return@doOnPreDraw
      toolbar.alpha = 1f
      val after = snapshot(toolbar)
      if (after == null) { cancel(); return@doOnPreDraw }
      toolbar.alpha = 0f
      val movement = ToolbarLayer(before, after, frame.resources.displayMetrics.density)
      overlay = movement; movement.setBounds(0, 0, frame.width, frame.height)
      frame.overlay.add(movement)
      animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 220
        interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
        addUpdateListener { movement.progress = it.animatedValue as Float; movement.invalidateSelf() }
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator) { this@KeyboardToolbarMotion.cancel() }
        })
        start()
      }
    }
  }

  private class ToolbarLayer(val before: Snapshot, val after: Snapshot, val density: Float) : Drawable() {
    var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    override fun draw(canvas: Canvas) {
      val p = progress
      val direction = if ((after.anchor?.centerX() ?: 0) > (before.anchor?.centerX() ?: 0)) 1f else -1f
      canvas.save()
      canvas.clipRect(bounds)
      paint.alpha = ((1f - p) * 255).toInt()
      canvas.drawBitmap(before.image, before.bounds.left - direction * p * 16f * density, before.bounds.top.toFloat(), paint)
      paint.alpha = (p * 255).toInt()
      canvas.drawBitmap(after.image, after.bounds.left + direction * (1f - p) * 16f * density, after.bounds.top.toFloat(), paint)
      val from = before.anchor; val to = after.anchor
      if (from != null && to != null && before.mascot != null) {
        fun mix(a: Int, b: Int) = a + (b - a) * p
        val arc = 0f
        destination.set(mix(from.left, to.left), mix(from.top, to.top) + arc, mix(from.right, to.right), mix(from.bottom, to.bottom) + arc)
        paint.alpha = 255
        canvas.drawBitmap(before.mascot, null, destination, paint)
      } else {
        before.mascot?.let { paint.alpha = ((1f - p) * 255).toInt(); canvas.drawBitmap(it, null, before.anchor!!, paint) }
        after.mascot?.let { paint.alpha = (p * 255).toInt(); canvas.drawBitmap(it, null, after.anchor!!, paint) }
      }
      canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
  }
}
