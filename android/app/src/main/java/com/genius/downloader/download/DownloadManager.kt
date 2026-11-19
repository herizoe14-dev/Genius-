package com.genius.downloader.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.genius.downloader.engine.Analyzer
import com.genius.downloader.engine.Http
import com.genius.downloader.engine.YtDlp
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Ce qu'il faut télécharger. */
sealed interface DownloadJob {
    /** Téléchargement via yt-dlp (vidéo avec choix de qualité, audio, playlists…). */
    data class YtDlpJob(val url: String, val args: List<String>, val headers: Map<String, String> = emptyMap()) : DownloadJob

    /** Fichier direct (image, vidéo sans filigrane déjà résolue…). */
    data class DirectJob(val url: String, val headers: Map<String, String>, val fileName: String) : DownloadJob
}

enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELED }

data class DownloadTask(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val thumbHeaders: Map<String, String>,
    val kind: MediaKind,
    val job: DownloadJob,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val info: String = "",
    val error: String? = null,
    val uris: List<String> = emptyList(),
    val mime: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val active get() = status == Status.QUEUED || status == Status.RUNNING
}

object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()
    private lateinit var app: Context

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun enqueue(
        title: String,
        thumbnail: String?,
        kind: MediaKind,
        job: DownloadJob,
        thumbHeaders: Map<String, String> = emptyMap(),
    ) {
        val task = DownloadTask(UUID.randomUUID().toString().take(12), title, thumbnail, thumbHeaders, kind, job)
        _tasks.update { listOf(task) + it }
        start(task.id)
    }

    private fun start(id: String) {
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            slots.withPermit { run(id) }
            jobs.remove(id)
        }
        jobs[id] = job
        job.start()
    }

    fun cancel(id: String) {
        val t = find(id) ?: return
        if (t.job is DownloadJob.YtDlpJob) YtDlp.cancel(id)
        jobs.remove(id)?.cancel()
        set(id) { it.copy(status = Status.CANCELED, info = "Annulé") }
        File(app.cacheDir, "dl/$id").deleteRecursively()
    }

    fun retry(id: String) {
        val t = find(id) ?: return
        if (t.active) return
        set(id) { it.copy(status = Status.QUEUED, progress = 0f, error = null, info = "") }
        start(id)
    }

    fun remove(id: String) {
        cancel(id)
        File(app.cacheDir, "dl/$id").deleteRecursively()
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.value.filterNot { it.active }.forEach { File(app.cacheDir, "dl/${it.id}").deleteRecursively() }
        _tasks.update { list -> list.filter { it.active } }
    }

    private fun find(id: String) = _tasks.value.firstOrNull { it.id == id }

    private fun set(id: String, f: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }

    private suspend fun run(id: String) {
        val task = find(id) ?: return
        if (task.status == Status.CANCELED) return
        set(id) { it.copy(status = Status.RUNNING, info = "Démarrage…", error = null) }
        // On NE vide PAS le dossier : les morceaux déjà reçus servent à reprendre.
        val workDir = File(app.cacheDir, "dl/$id").apply { mkdirs() }
        var attempt = 0
        while (true) {
            try {
                val files = when (val job = task.job) {
                    is DownloadJob.YtDlpJob -> {
                        YtDlp.ready.await()
                        YtDlp.download(job.url, workDir, job.args, job.headers, id) { p, line ->
                            set(id) { it.copy(progress = (p / 100f).coerceIn(0f, 1f), info = prettyLine(line)) }
                        }
                    }
                    is DownloadJob.DirectJob -> listOf(direct(id, job, workDir))
                }
                coroutineContext.ensureActive()
                if (files.isEmpty()) throw IllegalStateException("Aucun fichier produit")
                set(id) { it.copy(progress = 1f, info = "Enregistrement…") }
                val saved = files.sortedBy { it.name }.map { MediaSaver.publish(app, it) to MediaSaver.mimeOf(it) }
                set(id) {
                    it.copy(
                        status = Status.DONE, progress = 1f, uris = saved.map { s -> s.first.toString() },
                        mime = saved.firstOrNull()?.second,
                        info = "Enregistré dans ${folderOf(it.kind)}",
                    )
                }
                workDir.deleteRecursively()
                return
            } catch (e: YoutubeDL.CanceledException) {
                set(id) { it.copy(status = Status.CANCELED, info = "Annulé") }
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (find(id)?.status != Status.CANCELED) set(id) { it.copy(status = Status.CANCELED, info = "Annulé") }
                throw e
            } catch (e: Throwable) {
                if (find(id)?.status == Status.CANCELED) return
                attempt++
                val network = isNetworkError(e) || !Net.isOnline(app)
                if (attempt <= (if (network) 15 else 2)) {
                    // Coupure (modem qui redémarre, réseau mobile…) : on attend le retour du réseau puis on reprend.
                    set(id) { it.copy(info = "Connexion perdue · reprise automatique ($attempt)…") }
                    Net.awaitOnline(app)
                    delay(3000L * attempt.coerceAtMost(5))
                    set(id) { it.copy(info = "Reprise du téléchargement…") }
                    continue
                }
                set(id) { it.copy(status = Status.FAILED, error = Analyzer.cleanError(e), info = "") }
                return
            }
        }
    }

    private fun isNetworkError(e: Throwable): Boolean {
        if (e is java.io.IOException) return true
        val m = (e.message ?: "").lowercase()
        return listOf(
            "timed out", "timeout", "connection reset", "connection aborted", "connection refused", "unreachable",
            "unable to resolve", "temporary failure", "name resolution", "no route", "incompleteread", "broken pipe",
            "giving up after", "connectionerror", "remote end closed", "ssl", "eof occurred", "errno",
        ).any { it in m }
    }

    private suspend fun direct(id: String, job: DownloadJob.DirectJob, dir: File): File {
        val out = File(dir, job.fileName)
        val already = if (out.exists()) out.length() else 0L
        val headers = if (already > 0) job.headers + ("Range" to "bytes=$already-") else job.headers
        Http.execute(job.url, headers).use { resp ->
            if (resp.code == 416) return out // déjà complet
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Réponse vide")
            val resumed = resp.code == 206 && already > 0
            var done = if (resumed) already else 0L
            val total = body.contentLength().let { if (it > 0) it + done else it }
            var last = 0L
            body.byteStream().use { input ->
                java.io.FileOutputStream(out, resumed).use { output ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 250) {
                            last = now
                            set(id) {
                                it.copy(
                                    progress = if (total > 0) done.toFloat() / total else 0f,
                                    info = if (total > 0) "${fmtSize(done)} / ${fmtSize(total)}" else fmtSize(done),
                                )
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun folderOf(kind: MediaKind) = when (kind) {
        MediaKind.VIDEO -> "Films/Genius"
        MediaKind.AUDIO -> "Musique/Genius"
        MediaKind.IMAGE -> "Images/Genius"
    }

    private val PROGRESS_RE = Regex("""of\s+(~?)\s*([\d.]+\s*\w+).*?at\s+([\d.]+\s*\w+/s)(?:.*?ETA\s+(\S+))?""")

    private fun prettyLine(line: String): String {
        PROGRESS_RE.find(line)?.let { m ->
            val eta = m.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
            val size = (if (m.groupValues[1].isNotEmpty()) "≈ " else "") + m.groupValues[2]
            return listOfNotNull(size, m.groupValues[3], eta?.let { "reste $it" }).joinToString(" · ")
        }
        return when {
            "[Merger]" in line -> "Fusion audio + vidéo…"
            "[ExtractAudio]" in line -> "Extraction de l'audio…"
            "[Metadata]" in line -> "Métadonnées…"
            "Destination" in line -> "Téléchargement…"
            else -> line.take(60)
        }
    }

    fun fmtSize(b: Long): String = when {
        b <= 0 -> "?"
        b < 1024 * 1024 -> "%.0f Ko".format(b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f Mo".format(b / 1024.0 / 1024)
        else -> "%.2f Go".format(b / 1024.0 / 1024 / 1024)
    }
}
