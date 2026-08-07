package dev.jacobv.undertow.data

/**
 * The feed apps Undertow watches. `shortsOnly` apps only count scrolling that
 * happens inside their short-video surface (detected via view ids), so e.g.
 * watching a normal YouTube video or browsing subscriptions costs nothing.
 */
enum class TargetApp(
    val key: String,
    val label: String,
    val packages: Set<String>,
    val shortsOnly: Boolean,
) {
    INSTAGRAM("instagram", "Instagram", setOf("com.instagram.android"), false),
    FACEBOOK("facebook", "Facebook", setOf("com.facebook.katana"), false),
    TIKTOK("tiktok", "TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), false),
    YOUTUBE_SHORTS("youtube", "YouTube Shorts", setOf("com.google.android.youtube"), true);

    companion object {
        fun forPackage(pkg: String?): TargetApp? =
            pkg?.let { p -> entries.firstOrNull { p in it.packages } }
    }
}
