package com.genius.downloader.download

/**
 * Vitesse et temps restant lissés sur les dernières secondes.
 * yt-dlp donne une vitesse « instantanée » qui saute (morceau par morceau) ; ici on mesure
 * l'avancement réel sur une fenêtre glissante, pour un affichage stable comme IDM.
 */
class SpeedMeter(private val windowMs: Long = 8_000) {
    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun reset() = samples.clear()

    /** progress : 0..1. Renvoie la vitesse lissée en « fraction par seconde ». */
    fun add(timeMs: Long, progress: Float): Float {
        // Retour en arrière (nouveau fichier, ex. audio après vidéo) : on repart de zéro.
        if (samples.isNotEmpty() && progress + 0.001f < samples.last().second) samples.clear()
        samples.addLast(timeMs to progress)
        while (samples.size > 2 && timeMs - samples.first().first > windowMs) samples.removeFirst()
        return rate()
    }

    fun rate(): Float {
        if (samples.size < 2) return 0f
        val (t0, p0) = samples.first()
        val (t1, p1) = samples.last()
        val dt = (t1 - t0) / 1000f
        return if (dt < 1f) 0f else ((p1 - p0) / dt).coerceAtLeast(0f)
    }

    /** Secondes restantes, ou -1 si inconnu. */
    fun etaSec(progress: Float): Int {
        val r = rate()
        return if (r <= 0f) -1 else ((1f - progress) / r).toInt()
    }

    companion object {
        /** « 97.76MiB », « 1.2GiB », « 500KiB » → octets. */
        fun parseSize(text: String): Long {
            val m = Regex("""([\d.]+)\s*([KMGT]?i?B)""", RegexOption.IGNORE_CASE).find(text) ?: return 0
            val v = m.groupValues[1].toDoubleOrNull() ?: return 0
            val unit = m.groupValues[2].uppercase()
            val base = if ("I" in unit) 1024.0 else 1000.0
            val pow = when (unit.first()) { 'K' -> 1; 'M' -> 2; 'G' -> 3; 'T' -> 4; else -> 0 }
            return (v * Math.pow(base, pow.toDouble())).toLong()
        }

        fun fmtSpeed(bytesPerSec: Double): String = when {
            bytesPerSec <= 0 -> "…"
            bytesPerSec < 1024 * 1024 -> "%.0f Ko/s".format(bytesPerSec / 1024)
            else -> "%.1f Mo/s".format(bytesPerSec / 1024 / 1024)
        }

        fun fmtEta(sec: Int): String = when {
            sec < 0 -> ""
            sec >= 3600 -> "%dh%02d".format(sec / 3600, (sec % 3600) / 60)
            else -> "%02d:%02d".format(sec / 60, sec % 60)
        }
    }
}
