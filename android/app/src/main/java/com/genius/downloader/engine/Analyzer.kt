package com.genius.downloader.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Choisit le bon extracteur selon le site, avec des solutions de secours. */
object Analyzer {

    private val URL_RE = Regex("""https?://[^\s"'<>]+""")
    private val SHORT_HOSTS = listOf(
        "vm.tiktok.com", "vt.tiktok.com", "tiktok.com/t/", "t.co/", "fb.watch", "pin.it",
        "instagram.com/share/", "facebook.com/share/", "pixiv.me",
    )

    /** Extrait le premier lien d'un texte partagé ("Regarde ça ! https://vm.tiktok.com/xyz/"). */
    fun extractUrl(text: String): String? = URL_RE.find(text.trim())?.value?.trimEnd('.', ',', ')', '!')

    suspend fun analyze(input: String): Analysis = withContext(Dispatchers.IO) {
        var url = extractUrl(input) ?: throw ExtractionException("Aucun lien valide")
        if (SHORT_HOSTS.any { it in url }) url = Http.resolve(url)
        val platform = Platform.of(url)

        val errors = mutableListOf<String>()
        suspend fun attempt(block: suspend () -> Analysis): Analysis? = try {
            block()
        } catch (e: Throwable) {
            errors += cleanError(e)
            null
        }
        suspend fun ytdlp(): Analysis? = attempt {
            YtDlp.ready.await()
            YtDlp.analyze(url)
        }

        val result: Analysis? = when (platform) {
            Platform.PIXIV -> attempt { NativeExtractors.pixiv(url) }
            Platform.TWITTER -> attempt { NativeExtractors.twitter(url) } ?: ytdlp()
            Platform.INSTAGRAM -> attempt { NativeExtractors.instagram(url) } ?: ytdlp() ?: attempt { NativeExtractors.openGraph(url) }
            Platform.TIKTOK ->
                if ("/photo/" in url) attempt { NativeExtractors.tiktok(url) } ?: ytdlp()
                else ytdlp()?.takeUnless { it is MediaResult && it.videoOptions.isEmpty() && it.gallery.isEmpty() }
                    ?: attempt { NativeExtractors.tiktok(url) }
            else -> ytdlp() ?: attempt { NativeExtractors.openGraph(url) }
        }
        result ?: throw ExtractionException(errors.firstOrNull() ?: "Impossible d'analyser ce lien")
    }

    fun cleanError(e: Throwable): String {
        val msg = (e.message ?: e.javaClass.simpleName).trim()
        val line = msg.lineSequence().lastOrNull { it.contains("ERROR") } ?: msg.lineSequence().firstOrNull().orEmpty()
        return line.removePrefix("ERROR:").replace(Regex("""^\s*\[[^\]]+]\s*"""), "").trim().take(300)
    }
}
