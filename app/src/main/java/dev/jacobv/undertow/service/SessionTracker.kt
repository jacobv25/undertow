package dev.jacobv.undertow.service

/**
 * Pure state machine for one "doom session": a run of continuous scrolling in a
 * single target app. Leaving the app for more than [idleResetMs] (or scrolling a
 * different target app) starts a fresh session. Snoozes get shorter each time so
 * "one more minute" can't be ridden forever.
 */
class SessionTracker(
    private val thresholdMsFor: (String) -> Long,
    private val idleResetMs: Long = 60_000L,
) {

    data class ScrollResult(
        /** Fire the friction overlay now. */
        val interrupt: Boolean,
        /** Milliseconds of doomscrolling this event adds to today's stats. */
        val countedMs: Long,
        /** Total elapsed time in the current session. */
        val sessionMs: Long,
    )

    var currentPkg: String? = null
        private set
    private var sessionStartMs = 0L
    private var lastScrollMs = 0L
    private var snoozeUntilMs = 0L
    private var snoozeCount = 0

    fun onScroll(pkg: String, now: Long): ScrollResult {
        val continuing = pkg == currentPkg && now - lastScrollMs <= idleResetMs
        if (!continuing) {
            currentPkg = pkg
            sessionStartMs = now
            snoozeUntilMs = 0L
            snoozeCount = 0
        }
        val countedMs = if (continuing) now - lastScrollMs else 0L
        lastScrollMs = now

        val sessionMs = now - sessionStartMs
        val interrupt = sessionMs >= thresholdMsFor(pkg) && now >= snoozeUntilMs
        return ScrollResult(interrupt, countedMs, sessionMs)
    }

    /** Grants a grace period and returns its length; each snooze is shorter. */
    fun snooze(now: Long): Long {
        val duration = SNOOZE_STEPS_MS[minOf(snoozeCount, SNOOZE_STEPS_MS.lastIndex)]
        snoozeCount++
        snoozeUntilMs = now + duration
        return duration
    }

    fun endSession() {
        currentPkg = null
        sessionStartMs = 0L
        lastScrollMs = 0L
        snoozeUntilMs = 0L
        snoozeCount = 0
    }

    companion object {
        val SNOOZE_STEPS_MS = longArrayOf(60_000L, 30_000L, 15_000L)
    }
}
