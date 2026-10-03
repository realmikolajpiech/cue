package expo.modules.subtext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

internal class ConversationImages(context: Context) {
  private val directory = File(context.noBackupFilesDir, "cue-images").apply { mkdirs() }
  private fun file(network: String, roomId: String, messageId: String): File {
    val key = MessageDigest.getInstance("SHA-256").digest("$roomId:$messageId".toByteArray()).joinToString("") { "%02x".format(it) }
    return File(directory, "$network-$key.jpg")
  }
  fun cached(network: String, roomId: String, messageId: String): File? = file(network, roomId, messageId).takeIf { it.exists() }
  fun save(network: String, roomId: String, messageId: String, raw: ByteArray): File {
    require(raw.isNotEmpty() && raw.size <= 8 * 1024 * 1024) { "Zdjęcie jest za duże." }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 50_000_000) { "Nieobsługiwane zdjęcie." }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })) { "Nie można odczytać zdjęcia." }
    val bytes = try {
      var quality = 85
      var output: ByteArray
      do {
        val stream = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream))
        output = stream.toByteArray(); quality -= 15
      } while (output.size > 450_000 && quality >= 40)
      require(output.size <= 450_000) { "Zdjęcie jest za duże do analizy." }
      output
    } finally { bitmap.recycle() }
    val target = file(network, roomId, messageId)
    val atomic = AtomicFile(target)
    val stream = atomic.startWrite()
    try { stream.write(bytes); atomic.finishWrite(stream) }
    catch (error: Exception) { atomic.failWrite(stream); throw error }
    var total = directory.listFiles().orEmpty().sumOf { it.length() }
    directory.listFiles().orEmpty().sortedBy { it.lastModified() }.filter { it != target }.forEach {
      if (total > 40 * 1024 * 1024) { val length = it.length(); if (it.delete()) total -= length }
    }
    return target
  }
  fun clear(network: String? = null) { directory.listFiles().orEmpty().filter { network == null || it.name.startsWith("$network-") }.forEach { it.delete() } }
}
