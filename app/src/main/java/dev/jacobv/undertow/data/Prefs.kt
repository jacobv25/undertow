package dev.jacobv.undertow.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

enum class Intensity { GENTLE, STRICT, EXTREME }

/** Thin wrapper over SharedPreferences; safe to read synchronously from the service. */
class Prefs(context: Context) {

    private val appContext = context.applicationContext
    private val sp: SharedPreferences =
        appContext.getSharedPreferences("undertow_prefs", Context.MODE_PRIVATE)

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

    /**
     * How hard the overlay pushes back. GENTLE: tap to snooze. STRICT: snoozing
     * takes a deliberate hold. EXTREME: the hold gets longer each repeat and the
     * phone yells at you the whole time your finger is down.
     */
    var intensity: Intensity
        get() {
            sp.getString(KEY_INTENSITY, null)?.let { return Intensity.valueOf(it) }
            // Pre-intensity installs had a bare strict-mode switch.
            return if (sp.getBoolean(KEY_STRICT, false)) Intensity.STRICT else Intensity.GENTLE
        }
        set(value) = sp.edit().putString(KEY_INTENSITY, value.name).apply()

    /** Strict or harsher: the snooze button requires a hold, not a tap. */
    val strictMode: Boolean get() = intensity >= Intensity.STRICT

    val extremeMode: Boolean get() = intensity == Intensity.EXTREME

    /**
     * User-chosen audio for Extreme mode's hold — plays (looping, out loud) for
     * as long as the snooze button is held, from the second offense on. Same
     * posture as the video clips: nothing ships with the app.
     */
    val holdClipDir: File get() = File(appContext.filesDir, "hold_clips")

    fun holdClips(): List<File> = holdClipDir.listFiles()?.sortedBy { it.name } ?: emptyList()

    fun nextHoldClip(): File? {
        val clips = holdClips()
        if (clips.isEmpty()) return null
        val i = sp.getInt(KEY_HOLD_CLIP_INDEX, 0) % clips.size
        sp.edit().putInt(KEY_HOLD_CLIP_INDEX, i + 1).apply()
        return clips[i]
    }

    fun clearHoldClips() {
        holdClipDir.deleteRecursively()
        sp.edit().remove(KEY_HOLD_CLIP_INDEX).apply()
    }

    /** Voice call-outs: speak the overlay's line aloud via TTS. */
    var ttsEnabled: Boolean
        get() = sp.getBoolean(KEY_TTS, false)
        set(value) = sp.edit().putBoolean(KEY_TTS, value).apply()

    /**
     * User-chosen interrupt clips (videos/GIFs/photos), copied into app-private
     * storage so the service can read them without URI grants. The app ships no
     * media of its own — whatever plays here is the user's, staying on-device.
     * Interrupts cycle through the clips round-robin so repeat offenders don't
     * get to tune out one familiar clip.
     */
    val interruptMediaDir: File get() = File(appContext.filesDir, "interrupt_clips")

    fun interruptMediaClips(): List<File> {
        migrateLegacyClip()
        return interruptMediaDir.listFiles()?.sortedBy { it.name } ?: emptyList()
    }

    /** The next clip in the rotation, or null when none are set. */
    fun nextInterruptMedia(): File? {
        val clips = interruptMediaClips()
        if (clips.isEmpty()) return null
        val i = sp.getInt(KEY_MEDIA_INDEX, 0) % clips.size
        sp.edit().putInt(KEY_MEDIA_INDEX, i + 1).apply()
        return clips[i]
    }

    fun clearInterruptMedia() {
        interruptMediaDir.deleteRecursively()
        sp.edit().remove(KEY_MEDIA_INDEX).remove(KEY_MEDIA_MIME).apply()
    }

    /** v0.3.x stored a single clip at files/interrupt_media — fold it into the rotation. */
    private fun migrateLegacyClip() {
        val legacy = File(appContext.filesDir, "interrupt_media")
        if (!legacy.exists()) return
        val mime = sp.getString(KEY_MEDIA_MIME, null) ?: "video/mp4"
        interruptMediaDir.mkdirs()
        legacy.renameTo(File(interruptMediaDir, "clip_0.${extFor(mime)}"))
        sp.edit().remove(KEY_MEDIA_MIME).apply()
    }

    companion object {
        private const val KEY_THRESHOLD_MIN = "threshold_min"
        private const val KEY_STRICT = "strict_mode"
        private const val KEY_INTENSITY = "intensity"
        private const val KEY_HOLD_CLIP_INDEX = "hold_clip_index"
        private const val KEY_TTS = "tts_enabled"
        private const val KEY_MEDIA_MIME = "interrupt_media_mime"
        private const val KEY_MEDIA_INDEX = "interrupt_media_index"
        const val DEFAULT_THRESHOLD_MIN = 5

        /** Only the video/image split matters — decoders sniff the real format. */
        fun extFor(mime: String) = if (mime.startsWith("video")) "mp4" else "img"
        fun isVideo(file: File) = file.extension == "mp4"
    }
}
