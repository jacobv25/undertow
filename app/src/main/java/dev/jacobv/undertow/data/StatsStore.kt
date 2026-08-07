package dev.jacobv.undertow.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

/**
 * Per-day, per-app counters kept as one JSON blob per day in SharedPreferences.
 * Volumes are tiny (a handful of writes per minute of scrolling at most), so no
 * database is warranted. Days older than [RETENTION_DAYS] are pruned on write.
 */
class StatsStore(context: Context) {

    data class AppDay(val doomMs: Long, val interrupts: Int, val walkedAway: Int)

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("undertow_stats", Context.MODE_PRIVATE)

    fun addDoomTime(app: TargetApp, ms: Long) = update(app) {
        it.copy(doomMs = it.doomMs + ms)
    }

    fun recordInterrupt(app: TargetApp) = update(app) {
        it.copy(interrupts = it.interrupts + 1)
    }

    fun recordWalkedAway(app: TargetApp) = update(app) {
        it.copy(walkedAway = it.walkedAway + 1)
    }

    /**
     * Total doomscrolling ms per day for the last [days] days, oldest first
     * (last element is today). Days with no data are 0.
     */
    fun history(days: Int): List<Long> {
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { offset ->
            val raw = sp.getString(DAY_PREFIX + today.minusDays(offset.toLong()), null)
                ?: return@map 0L
            val day = JSONObject(raw)
            TargetApp.entries.sumOf { day.optJSONObject(it.key)?.optLong("doomMs") ?: 0L }
        }
    }

    fun today(): Map<TargetApp, AppDay> {
        val day = JSONObject(sp.getString(todayKey(), null) ?: return emptyMap())
        return TargetApp.entries.mapNotNull { app ->
            day.optJSONObject(app.key)?.let { o ->
                app to AppDay(o.optLong("doomMs"), o.optInt("interrupts"), o.optInt("walkedAway"))
            }
        }.toMap()
    }

    @Synchronized
    private fun update(app: TargetApp, transform: (AppDay) -> AppDay) {
        val key = todayKey()
        val day = JSONObject(sp.getString(key, null) ?: "{}")
        val cur = day.optJSONObject(app.key)
            ?.let { AppDay(it.optLong("doomMs"), it.optInt("interrupts"), it.optInt("walkedAway")) }
            ?: AppDay(0, 0, 0)
        val next = transform(cur)
        day.put(app.key, JSONObject().apply {
            put("doomMs", next.doomMs)
            put("interrupts", next.interrupts)
            put("walkedAway", next.walkedAway)
        })
        val editor = sp.edit().putString(key, day.toString())
        sp.all.keys
            .filter { it.startsWith(DAY_PREFIX) && it < DAY_PREFIX + LocalDate.now().minusDays(RETENTION_DAYS) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun todayKey() = DAY_PREFIX + LocalDate.now()

    companion object {
        private const val DAY_PREFIX = "day_"
        private const val RETENTION_DAYS = 30L
    }
}
