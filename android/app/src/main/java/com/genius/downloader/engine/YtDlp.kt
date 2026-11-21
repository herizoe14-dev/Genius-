package com.genius.downloader.engine

import android.content.Context
import com.genius.downloader.Prefs
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * yt-dlp + python + ffmpeg embarqués dans l'APK (youtubedl-android).
 * Aucun serveur externe : tout s'exécute sur le téléphone.
 */
object YtDlp {
    private const val TAG = "YtDlp"
    val ready = CompletableDeferred<Unit>()

    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ready.complete(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "init failed", e)
            ready.completeExceptionally(e)
        }
    }

    fun update(context: Context): String {
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        return "${status?.name ?: "?"} – yt-dlp ${version(context)}"
    }

    fun version(context: Context): String = YoutubeDL.getInstance().versionName(context) ?: "?"

    private fun baseRequest(url: String): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--no-warnings")
        addOption("--socket-timeout", 20)
        addOption("--retries", 5)
        addOption("--no-check-certificates")
        Accounts.ytDlpCookieFile()?.let { addOption("--cookies", it.absolutePath) }
    }

    /** Lance yt-dlp -J et renvoie le JSON. */
    fun dumpJson(url: String, flatPlaylist: Boolean = true, headers: Map<String, String> = emptyMap()): JSONObject {
        val req = baseRequest(url).apply {
            addOption("-J")
            headers.forEach { (k, v) -> addOption("--add-header", "$k:$v") }
            if (flatPlaylist) addOption("--flat-playlist")
        }
        val resp = YoutubeDL.getInstance().execute(req)
        val out = resp.out.trim()
        val start = out.indexOf('{')
        if (start < 0) throw ExtractionException("Réponse yt-dlp vide")
        return JSONObject(out.substring(start))
    }

    fun analyze(url: String, headers: Map<String, String> = emptyMap()): Analysis {
        val json = dumpJson(url, headers = headers)
        return parse(url, json)
    }

    fun parse(sourceUrl: String, json: JSONObject): Analysis {
        val platform = Platform.of(sourceUrl)
        val type = json.optString("_type", "video")
        val entries = json.optJSONArray("entries")
        if ((type == "playlist" || type == "multi_video") && entries != null) {
            // Carrousel (Instagram / Twitter) : les entrées sont déjà des médias complets.
            val full = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
            val allResolved = full.isNotEmpty() && full.all { it.has("formats") || it.has("url") && it.optString("_type") != "url" }
            if (allResolved && full.size <= 30 && platform != Platform.YOUTUBE) {
                return carousel(sourceUrl, platform, json, full)
            }
            return PlaylistResult(
                sourceUrl = sourceUrl,
                platform = platform,
                title = json.optString("title").ifBlank { "Playlist" },
                uploader = json.optStringOrNull("uploader") ?: json.optStringOrNull("channel"),
                entries = full.mapNotNull { e ->
                    val u = e.optStringOrNull("url") ?: e.optStringOrNull("webpage_url") ?: return@mapNotNull null
                    PlaylistEntry(
                        url = fixEntryUrl(u, e, platform),
                        title = e.optString("title").ifBlank { e.optString("id") },
                        durationSec = e.optDouble("duration", 0.0).toInt(),
                        thumbnail = bestThumb(e),
                    )
                },
            )
        }
        return single(sourceUrl, platform, json)
    }

    private fun fixEntryUrl(u: String, e: JSONObject, platform: Platform): String {
        if (u.startsWith("http")) return u
        return if (platform == Platform.YOUTUBE) "https://www.youtube.com/watch?v=${e.optString("id", u)}" else u
    }

    private fun carousel(sourceUrl: String, platform: Platform, root: JSONObject, items: List<JSONObject>): MediaResult {
        val gallery = items.mapNotNull { e ->
            val formats = e.optJSONArray("formats")
            val vcodec = e.optString("vcodec")
            val isVideo = (formats != null && formats.length() > 0 && vcodec != "none") || e.optString("ext") in setOf("mp4", "webm", "mov")
            val url = if (isVideo) bestDirectUrl(e) else e.optStringOrNull("url") ?: bestThumb(e)
            url ?: return@mapNotNull null
            GalleryItem(
                url = url,
                thumb = bestThumb(e) ?: url,
                isVideo = isVideo,
                ext = if (isVideo) "mp4" else e.optString("ext", "jpg").ifBlank { "jpg" },
            )
        }
        val first = items.first()
        val single = single(sourceUrl, platform, first)
        return single.copy(
            title = root.optString("title").ifBlank { single.title },
            uploader = root.optStringOrNull("uploader") ?: single.uploader,
            gallery = gallery,
            videoOptions = if (items.size == 1) single.videoOptions else emptyList(),
        )
    }

    private fun bestDirectUrl(e: JSONObject): String? {
        val formats = e.optJSONArray("formats") ?: return e.optStringOrNull("url")
        val list = formats.objects().filter {
            it.optString("vcodec") != "none" && it.optString("acodec") != "none" &&
                it.optString("protocol").startsWith("http")
        }
        return list.maxByOrNull { it.optInt("height") * 10000 + it.optDouble("tbr", 0.0).toInt() }?.optString("url")
            ?: e.optStringOrNull("url")
    }

    private fun single(sourceUrl: String, platform: Platform, json: JSONObject): MediaResult {
        val pageUrl = json.optStringOrNull("webpage_url") ?: sourceUrl
        val formats = json.optJSONArray("formats")?.objects().orEmpty()
            .filterNot { isWatermarked(it) }
            .filter { it.optString("protocol") != "mhtml" }

        val videos = formats.filter { it.optString("vcodec") != "none" && it.optInt("height") > 0 }
        val audios = formats.filter { it.optString("vcodec") == "none" && it.optString("acodec") != "none" }
        val bestAudio = audios.maxByOrNull { it.optDouble("abr", it.optDouble("tbr", 0.0)) }

        val options = videos.groupBy { it.optInt("height") }
            .map { (h, fs) ->
                val f = fs.maxWith(compareBy<JSONObject>({ codecScore(it) }, { it.optDouble("tbr", 0.0) }))
                val hasAudio = f.optString("acodec").let { it != "none" && it.isNotEmpty() } ||
                    (!f.has("acodec") && audios.isEmpty())
                val id = f.optString("format_id")
                val noWm = if (platform == Platform.TIKTOK) "[format_note!*=?watermark]" else ""
                val fallback = "bv*[height<=$h]$noWm+ba/b[height<=$h]$noWm/b$noWm"
                val selector = if (hasAudio || bestAudio == null) "$id/$fallback" else "$id+ba[ext=m4a]/$id+ba/$id/$fallback"
                val duration = json.optDouble("duration", 0.0)
                val size = estimate(f, duration).let { v -> if (v <= 0) 0 else v + if (hasAudio) 0 else estimate(bestAudio, duration) }
                val fps = f.optDouble("fps", 0.0).toInt()
                VideoOption(
                    height = h,
                    label = qualityLabel(h, fps),
                    detail = listOfNotNull(
                        codecName(f.optString("vcodec")),
                        if (fps > 0) "${fps}fps" else null,
                    ).joinToString(" · "),
                    sizeBytes = size,
                    sizeApprox = f.optLong("filesize", 0) <= 0,
                    ytdlpUrl = pageUrl,
                    ytdlpFormat = selector,
                )
            }
            .sortedByDescending { it.height }

        val hasAnyAudio = bestAudio != null || videos.any { it.optString("acodec") != "none" } || formats.isEmpty()
        val audio = if (hasAnyAudio || json.optString("vcodec") != "none") AudioOption(
            label = bestAudio?.let { "${it.optString("acodec").substringBefore('.').uppercase()} · ${it.optDouble("abr", 0.0).toInt()} kbps" }
                ?: "Qualité max",
            sizeBytes = sizeOf(bestAudio),
            ytdlpUrl = pageUrl,
            ext = bestAudio?.optString("ext")?.let { if (it == "webm") "opus" else it } ?: "m4a",
        ) else null

        // Pas de formats listés (ex. image ou fichier direct) : on propose le fichier tel quel.
        val gallery = if (formats.isEmpty() && json.optString("ext") in setOf("jpg", "jpeg", "png", "webp", "gif")) {
            listOf(GalleryItem(json.optString("url"), ext = json.optString("ext")))
        } else emptyList()

        val fallbackVideo = if (options.isEmpty() && gallery.isEmpty() && json.optString("vcodec") != "none") listOf(
            VideoOption(json.optInt("height"), "Meilleure", "auto", ytdlpUrl = pageUrl, ytdlpFormat = "bv*+ba/b"),
        ) else emptyList()

        return MediaResult(
            sourceUrl = pageUrl,
            platform = platform,
            title = json.optString("title").ifBlank { json.optString("id", "Média") },
            uploader = json.optStringOrNull("uploader") ?: json.optStringOrNull("channel") ?: json.optStringOrNull("creator"),
            thumbnail = bestThumb(json),
            durationSec = json.optDouble("duration", 0.0).toInt(),
            videoOptions = options.ifEmpty { fallbackVideo },
            audio = audio,
            gallery = gallery,
        )
    }

    private fun isWatermarked(f: JSONObject): Boolean {
        val note = (f.optString("format_note") + " " + f.optString("format_id") + " " + f.optString("format")).lowercase()
        return "watermark" in note
    }

    private fun codecScore(f: JSONObject): Int {
        val c = f.optString("vcodec").lowercase()
        return when {
            c.startsWith("avc") || c.startsWith("h264") -> 3 // lisible partout
            c.startsWith("vp9") || c.startsWith("vp09") -> 2
            c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("h265") -> 2
            c.startsWith("av01") -> 1
            else -> 0
        }
    }

    private fun codecName(c: String): String? {
        val l = c.lowercase()
        return when {
            l.startsWith("avc") || l.startsWith("h264") -> "H.264"
            l.startsWith("vp9") || l.startsWith("vp09") -> "VP9"
            l.startsWith("av01") -> "AV1"
            l.startsWith("hev") || l.startsWith("hvc") || l.startsWith("h265") || l == "bytevc1" -> "H.265"
            l.isBlank() || l == "none" -> null
            else -> c.substringBefore('.')
        }
    }

    private fun qualityLabel(h: Int, fps: Int): String {
        val base = when {
            h >= 4320 -> "8K"
            h >= 2160 -> "4K"
            h >= 1440 -> "2K"
            else -> "${h}p"
        }
        return if (fps > 30) "$base$fps" else base
    }

    /** Taille réelle si connue, sinon estimée à partir du débit (kbit/s) et de la durée. */
    private fun estimate(f: JSONObject?, duration: Double): Long {
        f ?: return 0
        sizeOf(f).takeIf { it > 0 }?.let { return it }
        val tbr = f.optDouble("tbr", 0.0).takeIf { it > 0 } ?: (f.optDouble("vbr", 0.0) + f.optDouble("abr", 0.0))
        return if (tbr > 0 && duration > 0) (tbr * 1000 / 8 * duration).toLong() else 0
    }

    private fun sizeOf(f: JSONObject?): Long {
        f ?: return 0
        val s = f.optLong("filesize", 0)
        return if (s > 0) s else f.optLong("filesize_approx", 0)
    }

    private fun bestThumb(e: JSONObject): String? {
        e.optStringOrNull("thumbnail")?.let { return it }
        val arr = e.optJSONArray("thumbnails") ?: return null
        return arr.objects().maxByOrNull { it.optInt("width") * it.optInt("height") + it.optInt("preference") }
            ?.optStringOrNull("url")
            ?: arr.objects().lastOrNull()?.optStringOrNull("url")
    }

    /**
     * Télécharge avec yt-dlp dans [outDir]. Renvoie les fichiers produits.
     */
    fun download(
        url: String,
        outDir: File,
        args: List<String>,
        headers: Map<String, String>,
        processId: String,
        fileTitle: String? = null,
        onProgress: (Float, String) -> Unit,
    ): List<File> {
        outDir.mkdirs()
        val req = baseRequest(url).apply {
            val name = fileTitle?.let { com.genius.downloader.download.MediaSaver.sanitize(it).replace("%", "%%") }
            addOption("-o", File(outDir, if (name != null) "$name.%(ext)s" else "%(title).90B [%(id)s].%(ext)s").absolutePath)
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--concurrent-fragments", Prefs.connections.coerceIn(1, 8))
            addOption("--newline")
            // Reprise : on garde les morceaux déjà reçus et on réessaie longtemps si le réseau tombe.
            addOption("--continue")
            addOption("--retries", 30)
            addOption("--fragment-retries", 50)
            addOption("--retry-sleep", "exp=1:20")
            addOption("--socket-timeout", 30)
            Prefs.speedLimitKb.takeIf { it > 0 }?.let { addOption("--limit-rate", "${it}K") }
            headers.forEach { (k, v) -> addOption("--add-header", "$k:$v") }
            addCommands(args)
        }
        YoutubeDL.getInstance().execute(req, processId) { progress, _, line ->
            onProgress(progress, line)
        }
        return outDir.listFiles()?.filter {
            it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.contains(".temp.")
        }.orEmpty()
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() && it != "null" } else null
