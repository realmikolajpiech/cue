package expo.modules.subtext

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.mirrormsg.fbmessagebridge.Fbmessagebridge
import fi.mirrormsg.whatsappbridge.Whatsappbridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BridgeSmokeTest {
  @Test fun nativeBridgesLoadWithoutLoggingIn() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val messenger = Fbmessagebridge.newBridge("", object : fi.mirrormsg.fbmessagebridge.EventSink {
      override fun onEvent(type: String?, payload: String?) {}
    })
    assertNotNull(messenger)
    val db = File(context.cacheDir, "subtext-smoke-${System.nanoTime()}.db")
    val whatsapp = Whatsappbridge.newBridge(db.absolutePath, object : fi.mirrormsg.whatsappbridge.EventSink {
      override fun onEvent(type: String?, payload: String?) {}
    })
    assertFalse(whatsapp.isPaired)
    messenger.disconnect(); whatsapp.disconnect()
    listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).forEach { it.delete() }
  }
}
