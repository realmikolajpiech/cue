package expo.modules.guardian

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class GemmaModelTest {
  private val bytes = "verified test model bytes".toByteArray()
  private val model = GemmaModel("test", bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, 4096)
  @Test fun onlyExactArtifactCanBeInstalled() {
    val file = File.createTempFile("model", ".part")
    try {
      model.copyVerified(bytes.inputStream(), file)
      assertTrue(model.verify(file))
      for (invalid in listOf(bytes.dropLast(1).toByteArray(), bytes + byteArrayOf(0), bytes.copyOf().also { it[0] = 0 })) {
        assertThrows(IllegalArgumentException::class.java) { model.copyVerified(invalid.inputStream(), file) }
        assertFalse(file.exists())
      }
    } finally { file.delete() }
  }
  @Test fun cancellationRemovesPartialFile() {
    val file = File.createTempFile("model", ".part")
    assertThrows(CancellationException::class.java) {
      model.copyVerified(bytes.inputStream(), file) { throw CancellationException() }
    }
    assertFalse(file.exists())
  }
  @Test fun reportsCopiedBytesAndStillRejectsCorruptDownload() {
    val file = File.createTempFile("model", ".part")
    val progress = mutableListOf<Long>()
    try {
      model.copyVerified(bytes.inputStream(), file, onProgress = { progress.add(it) })
      assertEquals(bytes.size.toLong(), progress.last())
      assertTrue(model.verify(file))
      val corrupt = bytes.copyOf().also { it[0] = 0 }
      assertThrows(IllegalArgumentException::class.java) {
        model.copyVerified(corrupt.inputStream(), file, onProgress = { progress.add(it) })
      }
      assertFalse(file.exists())
    } finally { file.delete() }
  }
}
