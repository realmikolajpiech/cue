package expo.modules.subtext

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureValue(context: Context, private val alias: String = "subtext_deepseek") {
  private val prefs = context.getSharedPreferences(if (alias == "subtext_deepseek") "subtext_secrets" else "${alias}_secrets", Context.MODE_PRIVATE)
  @Synchronized fun set(value: String) {
    if (value.isBlank()) { prefs.edit().clear().commit(); return }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
      .putString("value", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)).commit()
  }
  @Synchronized fun get(): String? = runCatching {
    val value = prefs.getString("value", null) ?: return null
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)))
    String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)))
  }.getOrNull()
  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    }.generateKey()
  }
}
