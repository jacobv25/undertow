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
) {
    INSTAGRAM("instagram", "Instagram", setOf("com.instagram.android"), false, "clips"),
    FACEBOOK("facebook", "Facebook", setOf("com.facebook.katana"), false),
    TIKTOK("tiktok", "TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), false),
    YOUTUBE_SHORTS("youtube", "YouTube Shorts", setOf("com.google.android.youtube"), true, "reel");

    companion object {
        fun forPackage(pkg: String?): TargetApp? =
            pkg?.let { p -> entries.firstOrNull { p in it.packages } }
    }
}
