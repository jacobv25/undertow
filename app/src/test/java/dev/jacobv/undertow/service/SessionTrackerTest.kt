package dev.jacobv.undertow.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    private val threshold = 5 * 60_000L
    private fun tracker() = SessionTracker(thresholdMsFor = { threshold })

    @Test
    fun `no interrupt before threshold`() {
        val t = tracker()
        var now = 0L
        repeat(29) {
            assertFalse(t.onScroll("app", now).interrupt)
            now += 10_000L
        }
    }

    @Test
    fun `interrupt fires once threshold of continuous scrolling is reached`() {
        val t = tracker()
        var now = 0L
        var interrupted = false
        repeat(40) {
            if (t.onScroll("app", now).interrupt) interrupted = true
            now += 10_000L
        }
        assertTrue(interrupted)
    }

    @Test
    fun `idle gap resets the session`() {
        val t = tracker()
        var now = 0L
        repeat(25) { t.onScroll("app", now); now += 10_000L }
        now += 120_000L // walked away for 2 minutes
        val result = t.onScroll("app", now)
        assertFalse(result.interrupt)
        assertEquals(0L, result.sessionMs)
        assertEquals(0L, result.countedMs)
    }

    @Test
    fun `switching apps resets the session`() {
        val t = tracker()
        var now = 0L
        repeat(25) { t.onScroll("a", now); now += 10_000L }
        assertEquals(0L, t.onScroll("b", now).sessionMs)
    }

    @Test
    fun `snooze suppresses interrupt until it expires and steps shrink`() {
        val t = tracker()
        var now = 0L
        repeat(31) { t.onScroll("app", now); now += 10_000L }

        assertEquals(60_000L, t.snooze(now))
        assertFalse(t.onScroll("app", now + 30_000L).interrupt)
        assertTrue(t.onScroll("app", now + 61_000L).interrupt)

        assertEquals(30_000L, t.snooze(now + 61_000L))
        assertEquals(15_000L, t.snooze(now + 62_000L))
        assertEquals(15_000L, t.snooze(now + 63_000L))
    }

    @Test
    fun `endSession clears state and counted time restarts`() {
        val t = tracker()
        t.onScroll("app", 0L)
        t.onScroll("app", 10_000L)
        t.endSession()
        val result = t.onScroll("app", 20_000L)
        assertEquals(0L, result.countedMs)
        assertEquals(0L, result.sessionMs)
    }

    @Test
    fun `counted time only accumulates within a continuous session`() {
        val t = tracker()
        assertEquals(0L, t.onScroll("app", 0L).countedMs)
        assertEquals(10_000L, t.onScroll("app", 10_000L).countedMs)
        assertEquals(0L, t.onScroll("app", 200_000L).countedMs) // gap > idle reset
    }
}
