package com.genius.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genius.downloader.Prefs
import com.genius.downloader.download.Enqueue
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

    /** Résultat du détecteur de flux. */
    fun analyzeStream(streamUrl: String, headers: Map<String, String>, pageTitle: String?, pageUrl: String?) {
        analyzeJob?.cancel()
        pageUrl?.let { input.value = it }
        _state.value = UiState.Loading
        analyzeJob = viewModelScope.launch {
            _state.value = try {
                UiState.Done(Analyzer.analyzeStream(streamUrl, headers, pageTitle, pageUrl))
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
        Enqueue.video(r, opt)
        toast("Vidéo ${opt.label} ajoutée aux téléchargements")
    }

    fun downloadAudio(r: MediaResult) {
        Enqueue.audio(r)
        toast("Audio ajouté aux téléchargements")
    }

    fun downloadGallery(r: MediaResult, items: List<GalleryItem>) {
        Enqueue.gallery(r, items)
        toast("${items.size} élément(s) ajouté(s)")
    }

    /** [maxHeight] = 0 → qualité max. [audio] → audio seulement. */
    fun downloadPlaylist(p: PlaylistResult, entries: List<PlaylistEntry>, audio: Boolean, maxHeight: Int) {
        Enqueue.playlist(entries, audio, maxHeight)
        toast("${entries.size} élément(s) de la playlist ajoutés")
    }

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
