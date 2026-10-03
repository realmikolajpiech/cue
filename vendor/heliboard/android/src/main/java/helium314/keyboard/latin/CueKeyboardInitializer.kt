// Cue integration of HeliBoard. SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs

/** Initialize singletons before the IME's Java instance fields access them. */
class CueKeyboardInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        App.initialize(application)
        val preferences = application.prefs()
        if (!preferences.getBoolean("cue_keyboard_initialized", false)) {
            val polish = SubtypeSettings.getAllAvailableSubtypes().firstOrNull {
                it.locale == "pl" || it.locale == "pl_PL"
            }
            polish?.let { SubtypeSettings.addEnabledSubtype(preferences, it) }
            polish?.let { SubtypeSettings.setSelectedSubtype(preferences, it) }
            RichInputMethodManager.getInstance().refreshSubtypeCaches()
            preferences.edit().putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
                .putBoolean("cue_keyboard_initialized", true).apply()
        }
        return true
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
