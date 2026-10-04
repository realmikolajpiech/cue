package expo.modules.subtext

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Name and style side by side, vertically centred. Both stay whole when they fit;
 * otherwise the name shrinks first down to [minName], then the style gives way.
 */
internal class KeyboardConversationRow(context: Context, private val name: View, private val style: View, private val minName: Int) : ViewGroup(context) {
  init { addView(name); addView(style) }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val available = MeasureSpec.getSize(widthMeasureSpec)
    val height = MeasureSpec.getSize(heightMeasureSpec)
    val childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
    val free = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST)
    name.measure(free, childHeight); style.measure(free, childHeight)
    var nameWidth = name.measuredWidth
    var styleWidth = style.measuredWidth
    if (nameWidth + styleWidth > available) {
      nameWidth = maxOf(available - styleWidth, minOf(minName, nameWidth))
      styleWidth = maxOf(0, minOf(styleWidth, available - nameWidth))
    }
    name.measure(MeasureSpec.makeMeasureSpec(nameWidth, MeasureSpec.EXACTLY), childHeight)
    style.measure(MeasureSpec.makeMeasureSpec(styleWidth, MeasureSpec.EXACTLY), childHeight)
    setMeasuredDimension(available, height)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val h = b - t
    val nameTop = (h - name.measuredHeight) / 2
    name.layout(0, nameTop, name.measuredWidth, nameTop + name.measuredHeight)
    val styleTop = (h - style.measuredHeight) / 2
    style.layout(name.measuredWidth, styleTop, name.measuredWidth + style.measuredWidth, styleTop + style.measuredHeight)
  }
}
