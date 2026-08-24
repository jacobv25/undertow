package dev.jacobv.undertow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.jacobv.undertow.data.Prefs
import dev.jacobv.undertow.data.StatsStore
import dev.jacobv.undertow.data.TargetApp

class ScrollWatcherService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var stats: StatsStore
    private lateinit var tracker: SessionTracker
    private lateinit var overlay: FrictionOverlay
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        stats = StatsStore(this)
        tracker = SessionTracker(thresholdMsFor = { prefs.thresholdMs })
        overlay = FrictionOverlay(this)
        tts = TextToSpeech(this) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        running = true
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized && overlay.isShowing) overlay.hide()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::tracker.isInitialized) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event)
        }
    }

    private fun onWindowChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        // Our own overlay appearing also fires this event — never treat it as leaving.
        if (pkg == packageName) return
        if (TargetApp.forPackage(pkg) == null && overlay.isShowing) {
            // Transient system surfaces (the launcher preview behind a predictive
            // back gesture, notification shade peeks) fire this without the user
            // actually leaving — wait a beat and confirm before hiding.
            handler.removeCallbacks(confirmEscape)
            handler.postDelayed(confirmEscape, ESCAPE_CONFIRM_MS)
        }
    }

    private val confirmEscape = Runnable {
        if (!overlay.isShowing) return@Runnable
        // The focused overlay is usually the "active window" itself, so
        // rootInActiveWindow can't tell us what's underneath — scan all windows
        // for the target app instead. A PiP remnant doesn't count as still there.
        val stillThere = windows.any { w ->
            !w.isInPictureInPictureMode &&
                TargetApp.forPackage(w.root?.packageName?.toString()) != null
        }
        if (stillThere) return@Runnable
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "escape confirmed, hiding overlay; windows=" +
                windows.joinToString { it.root?.packageName?.toString() ?: "?" })
        }
        // User escaped on their own (home, notification) — that's a win, not a snooze.
        // Keep the session clock, though: if they dive straight back in, the next
        // scroll re-fires the overlay with the accumulated time instead of giving
        // them a fresh minute. A real walk-away hits the idle reset anyway.
        overlay.hide()
    }

    private fun onScrolled(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val app = TargetApp.forPackage(pkg) ?: return
        if (!prefs.isEnabled(app)) return
        if (app.surfaceNeedle != null && (app.shortsOnly || prefs.surfaceOnly(app)) &&
            !inSurface(event, app)
        ) return
        if (overlay.isShowing) return
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "scroll in ${app.key} counted (source=${event.source?.viewIdResourceName})")
        }

        val now = System.currentTimeMillis()
        val result = tracker.onScroll(pkg, now)
        if (result.countedMs > 0) stats.addDoomTime(app, result.countedMs)

        if (result.interrupt) {
            stats.recordInterrupt(app)
            val minutes = (result.sessionMs / 60_000L).coerceAtLeast(1)
            // First interrupt of a session is calm; every repeat escalates.
            val level = tracker.interruptCount - 1
            val line = Persona.line(level)
            if (prefs.ttsEnabled && ttsReady) {
                tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "undertow_interrupt")
            }
            val media = prefs.nextInterruptMedia()?.let {
                FrictionOverlay.Media(it, if (Prefs.isVideo(it)) "video/mp4" else "image/*")
            }
            overlay.show(
                appLabel = app.label,
                sessionMinutes = minutes,
                strict = prefs.strictMode,
                level = level,
                line = line,
                media = media,
                onDone = {
                    overlay.hide()
                    stats.recordWalkedAway(app)
                    tracker.endSession()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    // YouTube auto-enters picture-in-picture when sent Home
                    // mid-playback, leaving the Short playing in a floating
                    // window — the opposite of "take me out". Sweep it away.
                    handler.postDelayed({ dismissPip(app, attemptsLeft = 3) }, 700)
                },
                onSnooze = {
                    overlay.hide()
                    tracker.snooze(System.currentTimeMillis())
                }
            )
        }
    }

    /**
     * True if the scroll happened inside the app's short-video surface, identified
     * by view resource ids — YouTube Shorts lives in reel_* views (reel_recycler,
     * reel_player_page_container), Instagram Reels in clips_*
     * (clips_viewer_view_pager); regular feed/player ids don't match.
     *
     * The event source is the scrolled view itself, so when it's present its
     * ancestor chain is authoritative: a Shorts scroll originates inside a reel_*
     * subtree, a feed or watch-page scroll doesn't — even while a paused Shorts
     * player is still attached behind the current screen (searching the whole
     * tree in that state false-positives on the background reel_* nodes). Only
     * when the chain carries no ids at all (id-less Litho/Compose sources) does
     * it fall back to the window tree, and there it only counts nodes the user
     * can actually see.
     */
    private fun inSurface(event: AccessibilityEvent, app: TargetApp): Boolean {
        val needle = app.surfaceNeedle ?: return true

        var node: AccessibilityNodeInfo? = event.source
        var sawAnyId = false
        var hops = 0
        while (node != null && hops < 15) {
            val id = node.viewIdResourceName
            if (id != null) {
                sawAnyId = true
                if (id.contains(needle, ignoreCase = true)) return true
            }
            node = node.parent
            hops++
        }
        if (sawAnyId) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "scroll in ${app.key} rejected by source chain: " +
                    "source=${event.source?.viewIdResourceName} class=${event.className}")
            }
            return false
        }

        val root = rootInActiveWindow ?: return false
        for (id in app.surfaceIds) {
            if (root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }) return true
        }
        if (findVisibleIdContains(root, needle)) return true

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "scroll in ${app.key} rejected: no visible '$needle' id; " +
                "class=${event.className}")
        }
        return false
    }

    /**
     * If [app] left a picture-in-picture window behind after "take me out",
     * drag it to the launcher's dismiss target. Re-checks a few times because
     * the PiP window animates in alongside the Home transition.
     */
    private fun dismissPip(app: TargetApp, attemptsLeft: Int) {
        if (attemptsLeft <= 0) return
        val pip = windows.firstOrNull { w ->
            w.isInPictureInPictureMode && w.root?.packageName?.toString() in app.packages
        }
        if (pip == null) {
            handler.postDelayed({ dismissPip(app, attemptsLeft - 1) }, 800)
            return
        }
        val bounds = Rect()
        pip.getBoundsInScreen(bounds)
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
            lineTo(dm.widthPixels / 2f, dm.heightPixels - 40f)
        }
        val drag = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "dismissing ${app.key} PiP at $bounds (attempts left: $attemptsLeft)")
        }
        dispatchGesture(drag, null, null)
        handler.postDelayed({ dismissPip(app, attemptsLeft - 1) }, 900)
    }

    private fun findVisibleIdContains(root: AccessibilityNodeInfo, needle: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 300) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser &&
                node.viewIdResourceName?.contains(needle, ignoreCase = true) == true
            ) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "Undertow"
        private const val ESCAPE_CONFIRM_MS = 600L

        /** Lets MainActivity show live "service is on" state. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
