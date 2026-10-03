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
import android.view.animation.LinearInterpolator
import androidx.core.view.doOnPreDraw
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.R as KeyboardR

/** One bitmap, one animator; key geometry comes from the active layout, including symbols. */
internal class KeyboardPickerMotion(private val frame: ViewGroup) {
  private var animator: ValueAnimator? = null
  private var layer: KeyWave? = null
  private var hidden: View? = null
  private var revealed: View? = null
  private var generation = 0

  fun cancel() {
    generation++
    animator?.removeAllListeners(); animator?.cancel(); animator = null
    layer?.let { frame.overlay.remove(it) }; layer = null
    hidden?.alpha = 1f; hidden = null
    revealed?.let { it.clipBounds = null; it.translationY = 0f }; revealed = null
  }

  fun capture(): KeyWave? {
    cancel()
    if (!frame.isLaidOut || !ValueAnimator.areAnimatorsEnabled()) return null
    val keyboard = frame.findViewById<KeyboardView>(KeyboardR.id.keyboard_view) ?: return null
    if (!keyboard.isShown || keyboard.width == 0 || keyboard.height == 0) return null
    val keys = keyboard.keyboard?.sortedKeys ?: return null
    val bitmap = Bitmap.createBitmap(keyboard.width, keyboard.height, Bitmap.Config.ARGB_8888)
    keyboard.draw(Canvas(bitmap))
    val location = Rect(0, 0, keyboard.width, keyboard.height)
    frame.offsetDescendantRectToMyCoords(keyboard, location)
    val tiles = keys.filter { !it.isSpacer }.mapNotNull { key ->
      val rect = Rect(key.hitBox).apply { offset(keyboard.paddingLeft, keyboard.paddingTop) }
      if (!rect.intersect(0, 0, bitmap.width, bitmap.height)) null else rect
    }
    if (tiles.isEmpty()) return null
    return KeyWave(bitmap, tiles, location.left.toFloat(), location.top.toFloat(), frame.resources.displayMetrics.density)
  }

  fun disappear(snapshot: KeyWave?, body: View?) {
    if (snapshot == null) return
    revealed = body
    body?.clipBounds = Rect(0, 0, 0, 0)
    layer = snapshot
    snapshot.setBounds(0, 0, frame.width, frame.height)
    frame.overlay.add(snapshot)
    run(snapshot, false, body)
  }

  fun appear() {
    cancel()
    if (!ValueAnimator.areAnimatorsEnabled()) return
    val keyboard = frame.findViewById<KeyboardView>(KeyboardR.id.keyboard_view) ?: return
    // The restored keyboard is laid out at its destination before any pixels are shown.
    hidden = keyboard; keyboard.alpha = 0f
    val token = generation
    frame.doOnPreDraw {
      if (token != generation) return@doOnPreDraw
      keyboard.alpha = 1f
      val snapshot = capture() ?: return@doOnPreDraw
      hidden = keyboard; keyboard.alpha = 0f
      layer = snapshot; snapshot.entering = true
      snapshot.setBounds(0, 0, frame.width, frame.height)
      frame.overlay.add(snapshot)
      run(snapshot, true, null)
    }
  }

  private fun run(snapshot: KeyWave, entering: Boolean, body: View?) {
    animator = ValueAnimator.ofFloat(0f, 360f).apply {
      duration = 360
      interpolator = LinearInterpolator()
      addUpdateListener {
        snapshot.elapsed = it.animatedValue as Float
        snapshot.entering = entering
        snapshot.invalidateSelf()
        body?.let { list ->
          val p = ((snapshot.elapsed - 185f) / 175f).coerceIn(0f, 1f)
          val ease = 1f - (1f - p) * (1f - p) * (1f - p)
          list.clipBounds = Rect(0, 0, list.width, (list.height * ease).toInt())
          list.translationY = (1f - ease) * 12f * frame.resources.displayMetrics.density
        }
      }
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) { this@KeyboardPickerMotion.cancel() }
      })
      start()
    }
  }

  class KeyWave(
    private val bitmap: Bitmap,
    private val tiles: List<Rect>,
    private val x: Float,
    private val y: Float,
    private val density: Float,
  ) : Drawable() {
    var elapsed = 0f
    var entering = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    override fun draw(canvas: Canvas) {
      for (tile in tiles) {
        val horizontal = tile.exactCenterX() / bitmap.width
        val vertical = tile.exactCenterY() / bitmap.height
        val order = (vertical * 0.68f + horizontal * 0.32f) * 105f
        val delay = if (entering) 105f - order else order
        val progress = ((elapsed - delay) / 210f).coerceIn(0f, 1f)
        val scale = if (entering) {
          // Restrained spring: settles at 1 with a small, deliberate overshoot.
          val t = progress - 1f
          1f + 1.7f * t * t * t + 0.7f * t * t
        } else 1f - progress * progress
        if (scale <= 0.001f) continue
        val remaining = if (entering) 1f - progress else progress * progress
        canvas.save()
        canvas.translate(x + tile.exactCenterX(), y + tile.exactCenterY() + remaining * 24f * density)
        canvas.rotate((horizontal - 0.5f) * 9f * remaining)
        canvas.scale(scale, scale)
        destination.set(-tile.width() / 2f, -tile.height() / 2f, tile.width() / 2f, tile.height() / 2f)
        canvas.drawBitmap(bitmap, tile, destination, paint)
        canvas.restore()
      }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
  }
}
