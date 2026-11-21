package com.genius.downloader.engine

/** Infos d'un flux HLS (.m3u8) : nombre de morceaux et durée réelle. */
data class HlsInfo(val segments: Int, val durationSec: Int, val variants: Int)

object Hls {
    private val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")

    fun inspect(url: String, headers: Map<String, String>): HlsInfo? = runCatching {
        val text = Http.get(url, headers)
        if (!text.startsWith("#EXTM3U")) return null
        if ("#EXT-X-STREAM-INF" in text) {
            // Playlist « master » : on mesure la meilleure variante.
            val lines = text.lines()
            val variants = lines.withIndex().filter { it.value.startsWith("#EXT-X-STREAM-INF") }
            val best = variants.maxByOrNull { BANDWIDTH.find(it.value)?.groupValues?.get(1)?.toLongOrNull() ?: 0 }
                ?: return null
            val rel = lines.drop(best.index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: return null
            val media = java.net.URI(url).resolve(rel.trim()).toString()
            inspect(media, headers)?.copy(variants = variants.size)
        } else {
            val durations = Regex("""#EXTINF:([\d.]+)""").findAll(text).map { it.groupValues[1].toDouble() }.toList()
            HlsInfo(durations.size, durations.sum().toInt(), 1)
        }
    }.getOrNull()

    fun fmtDuration(s: Int): String {
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "${h}h${"%02d".format(m)}m${"%02d".format(sec)}s" else "${m}m${"%02d".format(sec)}s"
    }

    /** Nettoie un titre de page : « Regarder X en streaming complet | Site » → « X ». */
    fun cleanTitle(raw: String?): String {
        var t = raw.orEmpty().trim()
        // Retire le nom du site en fin de titre (« … | Site », « … - Site »)
        Regex("""^(.{6,}?)\s+[|–—-]\s+[^|–—-]{2,30}$""").find(t)?.let { t = it.groupValues[1] }
        listOf(
            Regex("""(?i)\s*(en\s+)?streaming(\s+(complet|gratuit|hd|vf|vostfr))*"""),
            Regex("""(?i)^\s*(regarder|voir|watch)\s+"""),
            Regex("""(?i)\s*(full\s+)?(movie|film)\s+complet"""),
        ).forEach { t = t.replace(it, " ") }
        return t.replace(Regex("\\s+"), " ").trim().trimEnd('-', '|', ':').trim()
    }
}
