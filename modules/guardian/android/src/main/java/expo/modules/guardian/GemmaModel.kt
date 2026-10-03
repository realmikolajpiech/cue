package expo.modules.guardian

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** One pinned, portable CPU/GPU artifact. Never initialize unverified model bytes. */
data class GemmaModel(val id: String, val sizeBytes: Long, val sha256: String, val maxNumTokens: Int) {
  companion object {
    fun load(context: Context): GemmaModel {
      val json = JSONObject(context.assets.open("gemma3-1b.json").bufferedReader().use { it.readText() })
      return GemmaModel(json.getString("id"), json.getLong("sizeBytes"), json.getString("sha256"), json.getInt("maxNumTokens"))
    }
  }
  fun copyVerified(input: InputStream, target: File, checkCancelled: () -> Unit = {}) {
    try {
      val hash = MessageDigest.getInstance("SHA-256")
      var count = 0L
      target.outputStream().use { output ->
        val chunk = ByteArray(1024 * 1024)
        while (true) {
          checkCancelled()
          val n = input.read(chunk)
          if (n < 0) break
          count += n
          require(count <= sizeBytes) { "model_size_mismatch" }
          hash.update(chunk, 0, n); output.write(chunk, 0, n)
        }
        require(count == sizeBytes) { "model_size_mismatch" }
        require(hash.digest().joinToString("") { "%02x".format(it) } == sha256) { "model_checksum_mismatch" }
        output.fd.sync()
      }
    } catch (e: Exception) { target.delete(); throw e }
  }
  fun verify(file: File): Boolean {
    if (!file.isFile || file.length() != sizeBytes) return false
    val hash = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val chunk = ByteArray(1024 * 1024)
      while (true) {
        val n = input.read(chunk)
        if (n < 0) break
        hash.update(chunk, 0, n)
      }
    }
    return hash.digest().joinToString("") { "%02x".format(it) } == sha256
  }
}
