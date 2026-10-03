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
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.view.doOnPreDraw

/** Only the action changes. Brand and recipient stay native and stationary. */
internal class KeyboardToolbarMotion(private val frame: ViewGroup) {
  data class Snapshot(val image: Bitmap, val background: Bitmap, val bounds: Rect, val contentLeft: Int)
  private var animator: ValueAnimator? = null
  private var overlay: Drawable? = null
  private var hidden = emptyList<View>()
  private var generation = 0

  fun cancel() {
    generation++
    animator?.removeAllListeners(); animator?.cancel(); animator = null
    overlay?.let { frame.overlay.remove(it) }; overlay = null
    hidden.forEach { it.alpha = 1f }; hidden = emptyList()
  }

  fun capture(toolbar: View?): Snapshot? { cancel(); return snapshot(toolbar) }

  private fun snapshot(toolbar: View?): Snapshot? {
    if (toolbar !is ViewGroup || toolbar.width <= 0 || toolbar.height <= 0 || !ValueAnimator.areAnimatorsEnabled()) return null
    val mascot = (0 until toolbar.childCount).map(toolbar::getChildAt).filterIsInstance<ImageView>().firstOrNull()
    val bounds = Rect(0, 0, toolbar.width, toolbar.height).also { frame.offsetDescendantRectToMyCoords(toolbar, it) }
    val action = toolbar.getChildAt(toolbar.childCount - 1) ?: return null
    val contentLeft = bounds.left + action.left
    mascot?.alpha = 0f
    val bitmap = Bitmap.createBitmap(toolbar.width, toolbar.height, Bitmap.Config.ARGB_8888)
    val background = Bitmap.createBitmap(toolbar.width, toolbar.height, Bitmap.Config.ARGB_8888)
    val buttonBackground = action.background
    // A newly laid-out View has not drawn yet at pre-draw. Android normally
    // assigns these bounds in View.draw(), so capture must initialize them too.
    // Otherwise the incoming pill is empty until the real action is revealed.
    buttonBackground?.setBounds(0, 0, action.width, action.height)
    Canvas(background).apply {
      translate(action.left.toFloat(), action.top.toFloat())
      buttonBackground?.draw(this)
    }
    val backgroundAlpha = buttonBackground?.alpha ?: 255
    buttonBackground?.alpha = 0
    toolbar.draw(Canvas(bitmap))
    buttonBackground?.alpha = backgroundAlpha
    mascot?.alpha = 1f
    return Snapshot(bitmap, background, bounds, contentLeft)
  }

  fun change(before: Snapshot?, toolbar: View?) {
    if (before == null || toolbar !is ViewGroup) return
    hidden = listOfNotNull(toolbar.getChildAt(toolbar.childCount - 1))
    hidden.forEach { it.alpha = 0f }
    val token = generation
    frame.doOnPreDraw {
      if (token != generation) return@doOnPreDraw
      hidden.forEach { it.alpha = 1f }
      val after = snapshot(toolbar)
      if (after == null) { cancel(); return@doOnPreDraw }
      hidden.forEach { it.alpha = 0f }
      val movement = ControlsLayer(before, after, frame.resources.displayMetrics.density)
      overlay = movement; movement.setBounds(0, 0, frame.width, frame.height)
      frame.overlay.add(movement)
      animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 260
        interpolator = PathInterpolator(0.3f, 0f, 0.2f, 1f)
        addUpdateListener { movement.progress = it.animatedValue as Float; movement.invalidateSelf() }
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator) { this@KeyboardToolbarMotion.cancel() }
        })
        start()
      }
    }
  }

  private class ControlsLayer(val before: Snapshot, val after: Snapshot, val density: Float) : Drawable() {
    var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas) {
      val p = progress
      canvas.save()
      // Recipient and chevron are never captured on screen: no pressed ripple replay or text jitter.
      canvas.clipRect(maxOf(before.contentLeft, after.contentLeft), after.bounds.top, after.bounds.right, after.bounds.bottom)
      paint.alpha = ((1f - p) * 255).toInt()
      canvas.drawBitmap(before.background, before.bounds.left.toFloat(), before.bounds.top.toFloat(), paint)
      paint.alpha = (p * 255).toInt()
      canvas.drawBitmap(after.background, after.bounds.left.toFloat(), after.bounds.top.toFloat(), paint)
      val outgoing = (p / 0.6f).coerceIn(0f, 1f)
      val incoming = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
      paint.alpha = ((1f - outgoing) * 255).toInt()
      canvas.drawBitmap(before.image, before.bounds.left.toFloat(), before.bounds.top - outgoing * 4f * density, paint)
      paint.alpha = (incoming * 255).toInt()
      canvas.drawBitmap(after.image, after.bounds.left.toFloat(), after.bounds.top + (1f - incoming) * 4f * density, paint)
      canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
  }
}
