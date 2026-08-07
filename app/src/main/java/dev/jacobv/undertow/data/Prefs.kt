package dev.jacobv.undertow.data

import android.content.Context
import android.content.SharedPreferences

/** Thin wrapper over SharedPreferences; safe to read synchronously from the service. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("undertow_prefs", Context.MODE_PRIVATE)

    var thresholdMinutes: Int
        get() = sp.getInt(KEY_THRESHOLD_MIN, DEFAULT_THRESHOLD_MIN)
        set(value) = sp.edit().putInt(KEY_THRESHOLD_MIN, value.coerceIn(1, 30)).apply()

    val thresholdMs: Long get() = thresholdMinutes * 60_000L

    fun isEnabled(app: TargetApp): Boolean = sp.getBoolean("enabled_${app.key}", true)

    fun setEnabled(app: TargetApp, enabled: Boolean) =
        sp.edit().putBoolean("enabled_${app.key}", enabled).apply()

    companion object {
        private const val KEY_THRESHOLD_MIN = "threshold_min"
        const val DEFAULT_THRESHOLD_MIN = 5
    }
}
