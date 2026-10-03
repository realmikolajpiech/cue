package expo.modules.subtext

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfilePhotoCacheTest {
  @get:Rule val directory = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
  private fun context() = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
    override fun getNoBackupFilesDir(): File = directory.root
  }

  @Test fun absentPhotosAreCachedAcrossRestarts() = runBlocking {
    val key = "whatsapp:test"
    assertTrue(ProfilePhotoCache(context()).refresh(key) { "" }!!.isSuccess)
    assertNull(ProfilePhotoCache(context()).refresh(key) { error("Must not refetch absent photo") })
  }

  @Test fun logoutInvalidatesInFlightLookup() = runBlocking {
    val cache = ProfilePhotoCache(context())
    val started = CompletableDeferred<Unit>()
    val resume = CompletableDeferred<Unit>()
    val pending = async {
      cache.refresh("messenger:test") { started.complete(Unit); resume.await(); "" }
    }
    started.await()
    cache.clear("messenger")
    resume.complete(Unit)
    assertNull(pending.await())
    assertTrue(cache.refresh("messenger:test") { "" }!!.isSuccess)
  }

  @Test fun failuresPreserveExistingPhotoAndBackOff() = runBlocking {
    val folder = File(directory.root, "subtext-profile-photos").apply { mkdirs() }
    File(folder, "existing.jpg").writeBytes(byteArrayOf(1))
    File(folder, "metadata.json").writeText("""{"messenger:test":{"file":"existing.jpg","retryAt":0}}""")
    val cache = ProfilePhotoCache(context())
    assertTrue(cache.refresh("messenger:test") { error("Offline") }!!.isFailure)
    assertTrue(File(folder, "existing.jpg").exists())
    assertNull(cache.refresh("messenger:test") { error("Must back off") })
  }

  @Test fun accountCleanupKeepsOtherNetworks() = runBlocking {
    val cache = ProfilePhotoCache(context())
    cache.refresh("messenger:test") { "" }
    cache.refresh("whatsapp:test") { "" }
    cache.clear("messenger")
    assertNull(cache.refresh("whatsapp:test") { error("Other account must stay cached") })
    assertTrue(cache.refresh("messenger:test") { "" }!!.isSuccess)
  }

  @Test fun authenticatedImageIsSizedCachedAndRemovedOnLogout() = runBlocking {
    val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.BLUE)
    val output = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
    bitmap.recycle()
    val data = "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    val cache = ProfilePhotoCache(context())
    val uri = cache.refresh("messenger:test") { data }!!.getOrThrow()!!
    val file = File(java.net.URI(uri))
    assertTrue(file.isFile)
    val saved = BitmapFactory.decodeFile(file.path)
    assertEquals(192, saved.width)
    assertEquals(128, saved.height)
    saved.recycle()
    assertNull(ProfilePhotoCache(context()).refresh("messenger:test") { error("Must use cache") })
    cache.clear("messenger")
    assertFalse(file.exists())
  }

  @Test fun invalidImageNeverReplacesCachedPhoto() = runBlocking {
    val cache = ProfilePhotoCache(context())
    assertTrue(cache.refresh("messenger:test") { "data:image/jpeg;base64,bm90IGFuIGltYWdl" }!!.isFailure)
    assertEquals(0, File(directory.root, "subtext-profile-photos").listFiles()!!.count { it.extension == "jpg" })
  }

  @Test fun photoSurvivesMessageMergeAndStoreRestart() {
    val store = SubtextStore(context())
    store.merge("messenger", "test", "Test", "PRIVATE")
    store.avatar("messenger:test", "file:///private/photo.jpg")
    store.merge("messenger", "test", "Updated name", "PRIVATE", timestamp = 20)
    store.flush()
    val restored = SubtextStore(context())
    assertEquals("file:///private/photo.jpg", restored.summaries().single().getString("avatarUri"))
    assertEquals("file:///private/photo.jpg", restored.room("messenger:test")!!.getString("avatarUri"))
    restored.avatar("messenger:test", null)
    assertFalse(restored.room("messenger:test")!!.has("avatarUri"))
    restored.clear("messenger")
    restored.avatar("messenger:test", "file:///late.jpg")
    assertNull(restored.room("messenger:test"))
  }
}
