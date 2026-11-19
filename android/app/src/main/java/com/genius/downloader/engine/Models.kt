package com.genius.downloader.engine

/** Résultat de l'analyse d'un lien. */
sealed interface Analysis

/** Un seul contenu (vidéo, audio et/ou images). */
data class MediaResult(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val thumbnailHeaders: Map<String, String> = emptyMap(),
    val durationSec: Int = 0,
    val videoOptions: List<VideoOption> = emptyList(),
    val audio: AudioOption? = null,
    val gallery: List<GalleryItem> = emptyList(),
) : Analysis

/** Une playlist (YouTube, chaîne, profil TikTok…). */
data class PlaylistResult(
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val uploader: String? = null,
    val entries: List<PlaylistEntry>,
) : Analysis

data class PlaylistEntry(
    val url: String,
    val title: String,
    val durationSec: Int = 0,
    val thumbnail: String? = null,
)

/**
 * Une qualité vidéo. Soit téléchargée par yt-dlp ([ytdlpUrl] + [ytdlpFormat]),
 * soit directement par HTTP ([directUrl]).
 */
data class VideoOption(
    val height: Int,
    val label: String,
    val detail: String,
    val sizeBytes: Long = 0,
    val ytdlpUrl: String? = null,
    val ytdlpFormat: String? = null,
    val directUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val ext: String = "mp4",
)

data class AudioOption(
    val label: String,
    val sizeBytes: Long = 0,
    val ytdlpUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val directUrl: String? = null,
    val ext: String = "m4a",
)

data class GalleryItem(
    val url: String,
    val thumb: String = url,
    val isVideo: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val ext: String = "jpg",
)

enum class Platform(val label: String) {
    YOUTUBE("YouTube"),
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    TWITTER("X / Twitter"),
    PIXIV("Pixiv"),
    FACEBOOK("Facebook"),
    OTHER("Web");

    companion object {
        fun of(url: String): Platform {
            val host = runCatching { java.net.URI(url.trim()).host }.getOrNull()?.lowercase() ?: return OTHER
            fun on(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }
            return when {
                on("youtube.com", "youtu.be", "youtube-nocookie.com") -> YOUTUBE
                on("tiktok.com") -> TIKTOK
                on("instagram.com", "instagr.am") -> INSTAGRAM
                on("twitter.com", "x.com", "fxtwitter.com", "vxtwitter.com", "fixupx.com") -> TWITTER
                on("pixiv.net") -> PIXIV
                on("facebook.com", "fb.watch", "fb.com") -> FACEBOOK
                else -> OTHER
            }
        }
    }
}

class ExtractionException(message: String) : Exception(message)
