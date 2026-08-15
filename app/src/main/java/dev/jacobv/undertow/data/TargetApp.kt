package dev.jacobv.undertow.data

/**
 * The feed apps Undertow watches. `shortsOnly` apps only count scrolling inside
 * their short-video surface — identified by [surfaceNeedle], a substring of the
 * surface's view resource ids (YouTube Shorts uses reel_*, Instagram Reels uses
 * clips_*) — so e.g. watching a normal YouTube video costs nothing. Apps with a
 * needle but `shortsOnly = false` can opt into surface-only counting in settings.
 */
enum class TargetApp(
    val key: String,
    val label: String,
    val packages: Set<String>,
    val shortsOnly: Boolean,
    val surfaceNeedle: String? = null,
    /** Fully-qualified view ids of the surface, for the fast native whole-tree lookup. */
    val surfaceIds: List<String> = emptyList(),
) {
    INSTAGRAM(
        "instagram", "Instagram", setOf("com.instagram.android"), false, "clips",
        listOf("com.instagram.android:id/clips_viewer_view_pager"),
    ),
    // Threads is a pure text feed — all scrolling counts, like Facebook.
    THREADS("threads", "Threads", setOf("com.instagram.barcelona"), false),
    FACEBOOK("facebook", "Facebook", setOf("com.facebook.katana"), false),
    TIKTOK("tiktok", "TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), false),
    YOUTUBE_SHORTS(
        "youtube", "YouTube Shorts", setOf("com.google.android.youtube"), true, "reel",
        listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_fragment_root",
        ),
    );

    companion object {
        fun forPackage(pkg: String?): TargetApp? =
            pkg?.let { p -> entries.firstOrNull { p in it.packages } }
    }
}
