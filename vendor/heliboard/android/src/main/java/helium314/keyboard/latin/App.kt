// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.os.Build
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initialize(this)
    }
    companion object {
        private var initialized = false
        private var app: Application? = null
        @JvmStatic @Synchronized
        fun initialize(application: Application) {
            if (initialized) return
            with(application) {
                DebugFlags.init(this)
                FoldableUtils.init(this)
                Settings.init(this)
                SubtypeSettings.init(this)

                val scope = CoroutineScope(Dispatchers.Default)
                scope.launch { // do some uncritical work in background for faster startup
                    SupportedEmojis.load(application)
                    LayoutUtilsCustom.removeMissingLayouts(application)
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    @Suppress("DEPRECATION")
                    Log.i(
                        "startup", "Starting ${applicationInfo.processName} version ${packageInfo.versionName} (${
                            packageInfo.versionCode
                        }) on Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                    )
                }

                RichInputMethodManager.init(this)
                checkVersionUpgrade(this)
                if (BuildConfig.DEBUG) // do this on every debug apk start because we may work on adding a new toolbar key
                    upgradeToolbarPrefs(prefs())
                transferOldPinnedClips(this) // todo: remove in a few months, maybe end 2026
                app = this
                Defaults.initDynamicDefaults(this)
            }
            initialized = true
        }
        fun getApp(): Application? {
            val application = app
            app = null
            return application
        }
    }
}
