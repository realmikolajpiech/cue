package expo.modules.subtext

import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper

/** Keep even HeliBoard's cached connections pointed at the currently active editor. */
internal class KeyboardInputTarget(val host: InputConnection) : InputConnectionWrapper(host, true) {
  fun search(connection: InputConnection) = setTarget(connection)
  fun composer() = setTarget(host)
}
