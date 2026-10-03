package expo.modules.subtext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator

/** Resize the assistant area; the actual typing keys remain anchored to the bottom. */
internal class KeyboardPanelMotion {
  private var animator: ValueAnimator? = null
  private var panel: ViewGroup? = null
  private var body: View? = null
  fun cancel() {
    animator?.removeAllListeners(); animator?.cancel(); animator = null
    panel?.let { it.layoutParams = it.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT } }
    body?.let { it.alpha = 1f; it.translationY = 0f }
    panel = null; body = null
  }
  fun resize(view: ViewGroup, from: Int) {
    if (from <= 0 || !ValueAnimator.areAnimatorsEnabled()) return
    val width = view.width.takeIf { it > 0 } ?: return
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val target = view.measuredHeight
    if (target == from) return
    panel = view
    body = view.getChildAt(1)
    view.layoutParams = view.layoutParams.apply { height = from }
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 240
      interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
      addUpdateListener {
        val p = it.animatedValue as Float
        view.layoutParams = view.layoutParams.apply { height = (from + (target - from) * p).toInt() }
        body?.let { child -> child.alpha = p; child.translationY = (1f - p) * 10f * view.resources.displayMetrics.density }
      }
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) { this@KeyboardPanelMotion.cancel() }
      })
      start()
    }
  }
}
