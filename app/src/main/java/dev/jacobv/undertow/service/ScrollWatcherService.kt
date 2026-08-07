package dev.jacobv.undertow.service

import android.accessibilityservice.AccessibilityService
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        stats = StatsStore(this)
        tracker = SessionTracker(thresholdMsFor = { prefs.thresholdMs })
        overlay = FrictionOverlay(this)
        running = true
    }

    override fun onDestroy() {
        running = false
        if (::overlay.isInitialized && overlay.isShowing) overlay.hide()
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
            // User escaped on their own (home, back, notification) — that's a win, not a snooze.
            overlay.hide()
            tracker.endSession()
        }
    }

    private fun onScrolled(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val app = TargetApp.forPackage(pkg) ?: return
        if (!prefs.isEnabled(app)) return
        if (app.shortsOnly && !inShortsSurface(event)) return
        if (overlay.isShowing) return

        val now = System.currentTimeMillis()
        val result = tracker.onScroll(pkg, now)
        if (result.countedMs > 0) stats.addDoomTime(app, result.countedMs)

        if (result.interrupt) {
            stats.recordInterrupt(app)
            val minutes = (result.sessionMs / 60_000L).coerceAtLeast(1)
            overlay.show(
                appLabel = app.label,
                sessionMinutes = minutes,
                onDone = {
                    overlay.hide()
                    stats.recordWalkedAway(app)
                    tracker.endSession()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                },
                onSnooze = {
                    overlay.hide()
                    tracker.snooze(System.currentTimeMillis())
                }
            )
        }
    }

    /**
     * True if the scroll happened inside a short-video surface (YouTube Shorts).
     * YouTube's Shorts player lives in views whose resource ids contain "reel"
     * (e.g. reel_recycler, reel_player_page_container); regular feed/player ids don't.
     */
    private fun inShortsSurface(event: AccessibilityEvent): Boolean {
        event.source?.let { src ->
            var node: AccessibilityNodeInfo? = src
            var hops = 0
            while (node != null && hops < 6) {
                if (node.viewIdResourceName?.contains("reel", ignoreCase = true) == true) return true
                node = node.parent
                hops++
            }
        }
        val root = rootInActiveWindow ?: return false
        return findIdContains(root, "reel", depth = 0)
    }

    private fun findIdContains(node: AccessibilityNodeInfo, needle: String, depth: Int): Boolean {
        if (depth > 4) return false
        if (node.viewIdResourceName?.contains(needle, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findIdContains(child, needle, depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() = Unit

    companion object {
        /** Lets MainActivity show live "service is on" state. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
