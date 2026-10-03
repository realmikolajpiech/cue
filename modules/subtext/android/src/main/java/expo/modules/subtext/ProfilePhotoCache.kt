package expo.modules.subtext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Small, private, durable thumbnails. Requests never run on the UI thread. */
internal class ProfilePhotoCache(context: Context) {
  private val directory = File(context.noBackupFilesDir, "subtext-profile-photos").apply { mkdirs() }
  private val metadataFile = AtomicFile(File(directory, "metadata.json"))
  private var metadata = runCatching { JSONObject(String(metadataFile.readFully())) }.getOrDefault(JSONObject())
  private val lock = Any()
  private var generation = 0L

  suspend fun refresh(key: String, lookup: suspend () -> String): Result<String?>? = withContext(Dispatchers.IO) {
    val token = synchronized(lock) {
      val saved = metadata.optJSONObject(key)
      val missingFile = saved?.optString("file")?.takeIf(String::isNotBlank)?.let { !File(directory, it).isFile } == true
      if (!missingFile && System.currentTimeMillis() < (saved?.optLong("retryAt") ?: 0L)) return@withContext null
      generation
    }
    var stage = "lookup"
    var format = "unknown"
    val result = runCatching {
      val url = lookup().trim()
      format = when {
        url.startsWith("//") -> "protocol_relative"
        url.startsWith("/") -> "relative_path"
        url.startsWith("{") -> "json"
        url.startsWith("data:") -> "data"
        else -> url.substringBefore(":", "no_scheme").takeIf { it.matches(Regex("[a-zA-Z_]{1,20}")) } ?: "invalid"
      }
      if (url.isBlank()) null else {
        stage = "download"
        val bytes = download(url)
        stage = "decode"
        thumbnail(bytes)
      }
    }
    synchronized(lock) {
      if (generation != token) return@withContext null
      val old = metadata.optJSONObject(key) ?: JSONObject()
      val now = System.currentTimeMillis()
      if (result.isFailure) {
        if (old.optString("file").isNotBlank() && !File(directory, old.optString("file")).isFile) old.remove("file")
        val reason = if (stage == "lookup" && result.exceptionOrNull()?.message?.contains("metadata pending") == true) "metadata_pending" else stage
        val failures = (old.optInt("failures") + 1).coerceAtMost(5)
        old.put("error", reason).put("failures", failures)
          .put("retryAt", now + minOf(15 * 60_000L, 60_000L * (1 shl (failures - 1))))
        val failure = result.exceptionOrNull()!!
        val safeDetail = failure.message?.takeIf { it.startsWith("Profile photo") || it.startsWith("contact photo metadata pending") || it.startsWith("authenticated photo request failed") || it.startsWith("invalid photo") }
        old.put("detail", safeDetail?.take(120) ?: failure.javaClass.simpleName).put("format", format)
        metadata.put(key, old)
        saveMetadata()
        return@withContext Result.failure(result.exceptionOrNull()!!)
      }
      val bytes = result.getOrNull()
      val filename = bytes?.let { hash(key) + "-" + hash(it) + ".jpg" }
      if (filename != null) {
        val target = AtomicFile(File(directory, filename))
        val stream = target.startWrite()
        try { stream.write(bytes); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
      }
      val previous = old.optString("file")
      metadata.put(key, JSONObject().put("file", filename ?: "").put("retryAt", now + if (bytes == null) 6 * 60 * 60_000L else 24 * 60 * 60_000L))
      saveMetadata()
      if (previous.isNotBlank() && previous != filename) File(directory, previous).delete()
      Result.success(filename?.let { File(directory, it).toURI().toString() })
    }
  }

  fun clear(network: String? = null) = synchronized(lock) {
    generation++ // A download started before logout cannot write photos back.
    metadata.keys().asSequence().filter { network == null || it.startsWith("$network:") }.toList().forEach { key ->
      metadata.optJSONObject(key)?.optString("file")?.takeIf(String::isNotBlank)?.let { File(directory, it).delete() }
      metadata.remove(key)
    }
    saveMetadata()
  }

  fun prune(keys: Set<String>) = synchronized(lock) {
    val removed = metadata.keys().asSequence().filter { it !in keys }.toList()
    removed.forEach { key ->
      metadata.optJSONObject(key)?.optString("file")?.takeIf(String::isNotBlank)?.let { File(directory, it).delete() }
      metadata.remove(key)
    }
    val retained = metadata.keys().asSequence().mapNotNull { metadata.optJSONObject(it)?.optString("file") }.toSet()
    directory.listFiles()?.filter { it.extension == "jpg" && it.name !in retained }?.forEach { it.delete() }
    if (removed.isNotEmpty()) saveMetadata()
  }

  private fun saveMetadata() {
    val stream = metadataFile.startWrite()
    try { stream.write(metadata.toString().toByteArray()); metadataFile.finishWrite(stream) }
    catch (error: Exception) { metadataFile.failWrite(stream); throw error }
  }

  private fun download(raw: String): ByteArray {
    if (raw.startsWith("data:image/jpeg;base64,")) {
      val encoded = raw.substringAfter(",")
      require(encoded.length <= (MAX_BYTES + 2) / 3 * 4) { "Profile photo too large" }
      return android.util.Base64.decode(encoded, android.util.Base64.DEFAULT).also {
        require(it.size <= MAX_BYTES) { "Profile photo too large" }
      }
    }
    var url = URL(raw)
    repeat(4) {
      require(url.protocol == "https") { "Profile photo requires HTTPS" }
      val connection = url.openConnection() as HttpsURLConnection
      connection.connectTimeout = 8_000
      connection.readTimeout = 8_000
      connection.instanceFollowRedirects = false
      try {
        val code = connection.responseCode
        if (code in 300..399) {
          url = URL(url, requireNotNull(connection.getHeaderField("Location")))
        } else {
          check(code == 200) { "Profile photo download failed ($code)" }
          require(connection.contentLengthLong <= MAX_BYTES) { "Profile photo too large" }
          return connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
              val count = input.read(buffer)
              if (count < 0) break
              require(output.size() + count <= MAX_BYTES) { "Profile photo too large" }
              output.write(buffer, 0, count)
            }
            output.toByteArray()
          }
        }
      } finally { connection.disconnect() }
    }
    error("Too many profile photo redirects")
  }

  private fun thumbnail(bytes: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) { "Invalid profile photo" }
    val options = BitmapFactory.Options().apply {
      inSampleSize = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 384) inSampleSize *= 2
    }
    val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
    val scale = minOf(1f, 192f / maxOf(decoded.width, decoded.height))
    val resized = Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()), true)
    return try {
      java.io.ByteArrayOutputStream().use { output ->
        check(resized.compress(Bitmap.CompressFormat.JPEG, 88, output))
        output.toByteArray()
      }
    } finally {
      if (resized !== decoded) resized.recycle()
      decoded.recycle()
    }
  }

  private fun hash(value: String) = hash(value.toByteArray())
  private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).take(12).joinToString("") { "%02x".format(it) }
  private companion object { const val MAX_BYTES = 2 * 1024 * 1024 }
}
