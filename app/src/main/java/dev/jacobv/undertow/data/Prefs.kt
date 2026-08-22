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

    /** Only count scrolling inside the app's short-video surface (e.g. Instagram Reels). */
    fun surfaceOnly(app: TargetApp): Boolean = sp.getBoolean("surface_only_${app.key}", false)

    fun setSurfaceOnly(app: TargetApp, value: Boolean) =
        sp.edit().putBoolean("surface_only_${app.key}", value).apply()

    /** Strict mode: the snooze button requires a 3-second hold. */
    var strictMode: Boolean
        get() = sp.getBoolean(KEY_STRICT, false)
        set(value) = sp.edit().putBoolean(KEY_STRICT, value).apply()

    /** Voice call-outs: speak the overlay's line aloud via TTS. */
    var ttsEnabled: Boolean
        get() = sp.getBoolean(KEY_TTS, false)
        set(value) = sp.edit().putBoolean(KEY_TTS, value).apply()

    companion object {
        private const val KEY_THRESHOLD_MIN = "threshold_min"
        private const val KEY_STRICT = "strict_mode"
        private const val KEY_TTS = "tts_enabled"
        const val DEFAULT_THRESHOLD_MIN = 5
    }
}
