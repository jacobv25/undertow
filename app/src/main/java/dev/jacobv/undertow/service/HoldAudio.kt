package dev.jacobv.undertow.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.File

/**
 * Extreme mode's consequence: noise for exactly as long as the snooze button is
 * held. Finger down starts it, finger up stops it — the button is the thing
 * making the sound. Routed as an alarm so it cuts through the ringer being on
 * silent (the user opted into that in settings), and it takes audio focus so
 * the reel underneath goes quiet. Do Not Disturb is respected: that line is
 * the difference between "you asked for this" and "everyone around you did".
 *
 * Level 0: the drill sergeant's voice (TTS). Level 1+: the user's own hold
 * clip if they've added one, louder with each offense; TTS otherwise.
 */
class HoldAudio(context: Context, private val tts: () -> TextToSpeech?) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    /** Alarm-stream volume before we cranked it; restored on stop. */
    private var savedAlarmVolume: Int? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun start(level: Int, clip: File?) {
        stop()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { audioManager.requestAudioFocus(it) }
        crankVolume(level)

        val played = clip != null && level >= 1 && runCatching { playClip(clip, level) }.isSuccess
        if (!played) speak()
    }

    /**
     * The alarm stream is usually sitting well below media volume, which would
     * let the reel drown the yelling. Extreme mode turns it up for the hold —
     * 80% on the first offense, full blast after — and restores it on release.
     */
    private fun crankVolume(level: Int) {
        val stream = AudioManager.STREAM_ALARM
        val max = audioManager.getStreamMaxVolume(stream)
        val target = if (level >= 2) max else (max * 0.8f).toInt().coerceAtLeast(1)
        val current = audioManager.getStreamVolume(stream)
        if (current >= target) return
        savedAlarmVolume = current
        runCatching { audioManager.setStreamVolume(stream, target, 0) }
    }

    private fun playClip(clip: File, level: Int) {
        val volume = if (level >= 2) 1f else 0.8f
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(clip.absolutePath)
            isLooping = true
            setVolume(volume, volume)
            setOnErrorListener { _, _, _ -> true }
            prepare()
            start()
        }
    }

    private fun speak() {
        val engine = tts() ?: return
        engine.setAudioAttributes(attrs)
        engine.setPitch(0.85f)
        engine.setSpeechRate(1.15f)
        // Enough lines to outlast the longest hold; stop() cuts them off on release.
        Persona.holdLines(4).forEachIndexed { i, line ->
            engine.speak(
                line,
                if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "undertow_hold_$i"
            )
        }
    }

    companion object {
        /** What the ordinary voice call-out uses; restored after every hold. */
        private val NORMAL_ATTRS = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tts()?.let {
            it.stop()
            it.setPitch(1f)
            it.setSpeechRate(1f)
            it.setAudioAttributes(NORMAL_ATTRS)
        }
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
        savedAlarmVolume?.let { v ->
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
        }
        savedAlarmVolume = null
    }
}
