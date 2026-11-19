package com.genius.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genius.downloader.Prefs
import com.genius.downloader.download.DownloadJob
import com.genius.downloader.download.DownloadManager
import com.genius.downloader.download.MediaKind
import com.genius.downloader.download.MediaSaver
import com.genius.downloader.engine.Analysis
import com.genius.downloader.engine.Analyzer
import com.genius.downloader.engine.GalleryItem
import com.genius.downloader.engine.MediaResult
import com.genius.downloader.engine.PlaylistEntry
import com.genius.downloader.engine.PlaylistResult
import com.genius.downloader.engine.VideoOption
import com.genius.downloader.engine.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Done(val analysis: Analysis) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val input = MutableStateFlow("")
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating

    private var analyzeJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { YtDlp.ready.await() }
            _engineReady.value = YtDlp.ready.isCompleted && !YtDlp.ready.isCancelled
        }
    }

    fun consumeToast() { _toast.value = null }
    fun toast(msg: String) { _toast.value = msg }

    fun analyze(text: String = input.value) {
        if (Analyzer.extractUrl(text) == null) {
            _state.value = UiState.Error("Collez un lien TikTok, YouTube, Instagram, X, Pixiv, Facebook…")
            return
        }
        input.value = text.trim()
        analyzeJob?.cancel()
        _state.value = UiState.Loading
        analyzeJob = viewModelScope.launch {
            _state.value = try {
                UiState.Done(Analyzer.analyze(text))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                UiState.Error(Analyzer.cleanError(e))
            }
        }
    }

    fun reset() {
        analyzeJob?.cancel()
        input.value = ""
        _state.value = UiState.Idle
    }

    // ------------------------------------------------------------ téléchargements

    fun downloadVideo(r: MediaResult, opt: VideoOption) {
        val job = if (opt.directUrl != null) {
            DownloadJob.DirectJob(opt.directUrl, opt.headers, fileName(r.title, "", opt.ext))
        } else {
            DownloadJob.YtDlpJob(
                opt.ytdlpUrl ?: r.sourceUrl,
                listOf("-f", opt.ytdlpFormat ?: "bv*+ba/b", "--merge-output-format", "mp4", "--embed-metadata"),
            )
        }
        DownloadManager.enqueue("${r.title} · ${opt.label}", r.thumbnail, MediaKind.VIDEO, job, r.thumbnailHeaders)
        toast("Vidéo ${opt.label} ajoutée aux téléchargements")
    }

    fun downloadAudio(r: MediaResult) {
        val a = r.audio ?: return
        // Toujours via yt-dlp + ffmpeg : extrait la piste audio quel que soit le conteneur source.
        val job = DownloadJob.YtDlpJob(a.directUrl ?: a.ytdlpUrl ?: r.sourceUrl, audioArgs(), a.headers)
        DownloadManager.enqueue("${r.title} · audio", r.thumbnail, MediaKind.AUDIO, job, r.thumbnailHeaders)
        toast("Audio ajouté aux téléchargements")
    }

    fun downloadGallery(r: MediaResult, items: List<GalleryItem>) {
        items.forEachIndexed { i, item ->
            val idx = r.gallery.indexOf(item).takeIf { it >= 0 } ?: i
            val ext = safeExt(item.ext, item.isVideo)
            DownloadManager.enqueue(
                title = "${r.title} · ${idx + 1}/${r.gallery.size}",
                thumbnail = item.thumb,
                kind = if (item.isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                job = DownloadJob.DirectJob(item.url, item.headers, fileName(r.title, "_${idx + 1}", ext)),
                thumbHeaders = item.headers,
            )
        }
        toast("${items.size} élément(s) ajouté(s)")
    }

    /** [maxHeight] = 0 → qualité max. [audio] → audio seulement. */
    fun downloadPlaylist(p: PlaylistResult, entries: List<PlaylistEntry>, audio: Boolean, maxHeight: Int) {
        entries.forEach { e ->
            val args = if (audio) audioArgs() else listOf(
                "-f", "bv*+ba/b",
                "-S", if (maxHeight > 0) "res:$maxHeight,vcodec:h264,acodec:m4a" else "res,vcodec:h264,acodec:m4a",
                "--merge-output-format", "mp4", "--embed-metadata",
            )
            DownloadManager.enqueue(
                e.title, e.thumbnail,
                if (audio) MediaKind.AUDIO else MediaKind.VIDEO,
                DownloadJob.YtDlpJob(e.url, args),
            )
        }
        toast("${entries.size} élément(s) de la playlist ajoutés")
    }

    private fun audioArgs(): List<String> = buildList {
        addAll(listOf("-f", "ba/b", "-x", "--audio-quality", "0", "--embed-metadata"))
        if (Prefs.audioMp3) addAll(listOf("--audio-format", "mp3"))
    }

    private fun safeExt(ext: String, video: Boolean): String {
        val e = ext.lowercase().filter { it.isLetterOrDigit() }
        val ok = if (video) setOf("mp4", "webm", "mov", "mkv") else setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif")
        return if (e in ok) e else if (video) "mp4" else "jpg"
    }

    private fun fileName(title: String, suffix: String, ext: String) =
        "${MediaSaver.sanitize(title).take(70)}$suffix.$ext"

    // ------------------------------------------------------------ moteur

    fun updateEngine() {
        if (_updating.value) return
        _updating.value = true
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    YtDlp.ready.await()
                    YtDlp.update(getApplication()).also { Prefs.lastUpdate = System.currentTimeMillis() }
                }.getOrElse { "Échec de la mise à jour : ${Analyzer.cleanError(it)}" }
            }
            _updating.value = false
            toast(msg)
        }
    }

    fun engineVersion(): String = runCatching { YtDlp.version(getApplication()) }.getOrDefault("?")
}
