package dev.jacobv.undertow.service

/**
 * What the overlay says. The first interrupt of a session stays gentle; every
 * repeat visit (snooze expired, or the user dodged out and dove back in) gets
 * the drill sergeant. Lines are original tough-love — nothing quoted or sampled,
 * so the app ships clean.
 */
object Persona {

    const val CALM_LINE = "Take a breath. Is this still what you want to be doing?"

    private val DRILL_LINES = listOf(
        "You said one more minute. That was a lie and you know it.",
        "Nobody is coming to close this app for you.",
        "This feed does not end. Your day does.",
        "You've read this screen before. It hasn't changed. Have you?",
        "The algorithm is doing its job. Go do yours.",
        "Get comfortable being uncomfortable. Put the phone down.",
        "Your thumb is getting a workout. The rest of you isn't.",
        "Scroll later. Live now. MOVE.",
    )

    /**
     * Shouted while the snooze button is held in Extreme mode. Present tense,
     * second person: the user is doing the thing *right now*.
     */
    private val HOLD_LINES = listOf(
        "You are literally holding the button. Look at yourself.",
        "Let go. LET GO. You know what this is.",
        "Every second you hold this is a second you chose the feed over your life.",
        "Is this who you are? Somebody who negotiates with a button?",
        "Your future self is watching you do this. They're not impressed.",
        "Hold it longer. Prove to me you have no discipline.",
        "Nobody is impressed by your thumb strength. Let go.",
        "The feed doesn't miss you. Your day does. LET. GO.",
    )

    fun holdLines(count: Int): List<String> = HOLD_LINES.shuffled().take(count)

    /**
     * How long the snooze hold takes. Strict is a flat 3 seconds; Extreme grows
     * with each repeat offense so "one more minute" gets pricier every time.
     */
    fun holdMs(level: Int, extreme: Boolean): Long =
        if (!extreme) 3000L else when {
            level <= 0 -> 3000L
            level == 1 -> 5000L
            else -> 8000L
        }

    /** Level 0 = first interrupt this session; anything higher is a repeat offense. */
    fun line(level: Int): String =
        if (level <= 0) CALM_LINE else DRILL_LINES.random()

    fun title(level: Int, appLabel: String, minutes: Long): String {
        val time = if (minutes == 1L) "1 minute" else "$minutes minutes"
        return if (level <= 0) "You've been scrolling $appLabel for $time"
        else "STILL on $appLabel. $time."
    }
}
