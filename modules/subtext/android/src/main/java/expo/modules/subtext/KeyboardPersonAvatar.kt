package expo.modules.subtext

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Reads the app's existing thumbnails without fetching photos while typing. */
internal class KeyboardPersonAvatar(
  context: Context,
  private val initials: TextView,
  private val scope: CoroutineScope,
) : FrameLayout(context) {
  private val photo = ImageView(context).apply {
    scaleType = ImageView.ScaleType.CENTER_CROP
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
  }
  private var uri = ""
  private var load: Job? = null

  init {
    background = initials.background
    initials.background = null
    initials.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    clipToOutline = true
    addView(initials, LayoutParams(-1, -1))
    addView(photo, LayoutParams(-1, -1))
  }

  fun bind(name: String, avatarUri: String) {
    initials.text = name.trim().take(1)
    if (uri == avatarUri && (photo.drawable != null || load?.isActive == true)) return
    load?.cancel()
    uri = avatarUri
    photo.setImageDrawable(null)
    readPhoto()
  }

  private fun readPhoto() {
    val requested = uri.takeIf { it.isNotBlank() } ?: return
    load = scope.launch {
      val bitmap = withContext(Dispatchers.IO) {
        runCatching {
          val parsed = Uri.parse(requested)
          if (parsed.scheme != "file") return@runCatching null
          val file = File(requireNotNull(parsed.path)).canonicalFile
          val directory = File(context.noBackupFilesDir, "subtext-profile-photos").canonicalFile
          if (file.parentFile != directory || !file.isFile || file.length() > 2 * 1024 * 1024) return@runCatching null
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeFile(file.path, bounds)
          if (bounds.outWidth !in 1..192 || bounds.outHeight !in 1..192) return@runCatching null
          BitmapFactory.decodeFile(file.path)
        }.getOrNull()
      }
      // A recycled row must never show the preceding person's photo.
      if (uri == requested) photo.setImageBitmap(bitmap)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (photo.drawable == null && load?.isActive != true) readPhoto()
  }

  override fun onDetachedFromWindow() {
    load?.cancel()
    photo.setImageDrawable(null)
    super.onDetachedFromWindow()
  }
}
