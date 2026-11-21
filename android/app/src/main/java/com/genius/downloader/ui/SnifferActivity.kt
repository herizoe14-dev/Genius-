package com.genius.downloader.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.os.Parcel
import android.webkit.WebStorage
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.genius.downloader.MainActivity
import com.genius.downloader.download.DownloadJob
import com.genius.downloader.download.DownloadManager
import com.genius.downloader.download.Enqueue
import com.genius.downloader.download.MediaKind
import com.genius.downloader.download.MediaSaver
import com.genius.downloader.engine.AdFilter
import com.genius.downloader.engine.Analyzer
import com.genius.downloader.engine.BrowserStore
import com.genius.downloader.engine.Hls
import com.genius.downloader.engine.HlsInfo
import com.genius.downloader.engine.MediaResult
import com.genius.downloader.engine.VideoOption
import com.genius.downloader.ui.theme.Bg
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.Danger
import com.genius.downloader.ui.theme.GeniusTheme
import com.genius.downloader.ui.theme.Magenta
import com.genius.downloader.ui.theme.Success
import com.genius.downloader.ui.theme.Surface1
import com.genius.downloader.ui.theme.Surface2
import com.genius.downloader.ui.theme.TextDim
import com.genius.downloader.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Un flux vidéo repéré pendant la navigation. */
data class DetectedStream(val url: String, val kind: String, val referer: String?, val title: String) {
    val friendlyKind: String
        get() {
            val name = url.substringBefore('?').substringAfterLast('/').lowercase()
            return when {
                kind == "HLS" && ("master" in name || name == "playlist.m3u8") -> "Vidéo · toutes les qualités (recommandé)"
                kind == "HLS" && Regex("""(audio|-a\d+\.m3u8$|aac)""").containsMatchIn(name) && !Regex("""-v\d""").containsMatchIn(name) -> "Piste audio seule (HLS)"
                kind == "HLS" -> "Vidéo · une seule qualité (HLS)"
                kind == "DASH" -> "Vidéo · toutes les qualités (DASH)"
                kind == "Fichier" -> "Fichier à télécharger"
                else -> "Fichier vidéo ($kind)"
            }
        }
    val label: String
        get() = runCatching { java.net.URI(url) }.getOrNull()?.let { "${it.host} · …${it.path.takeLast(40)}" } ?: url.take(60)
}

/** Contenu de la fenêtre flottante de téléchargement. */
private sealed interface Panel {
    data object None : Panel
    data object Streams : Panel
    data class Loading(val stream: DetectedStream) : Panel
    data class Ready(val stream: DetectedStream, val result: MediaResult, val hls: HlsInfo?) : Panel
    data class Failed(val stream: DetectedStream, val message: String) : Panel
}

/**
 * Navigateur intégré de Genius :
 * - recherche Google / navigation libre, précédent / suivant, plein écran vidéo ;
 * - filtre de pubs doux (les régies reçoivent une réponse vide, la page n'est jamais bloquée) ;
 * - détection des vidéos (MP4/WebM, HLS, DASH, fichiers) y compris dans les iframes ;
 * - fenêtre flottante de téléchargement (titre, durée, morceaux, taille, qualité).
 */
class SnifferActivity : ComponentActivity() {
    /** Un onglet du navigateur : sa page et les vidéos détectées dedans. */
    private inner class BrowserTab(val web: WebView) {
        val streams = mutableStateListOf<DetectedStream>()
        var title by mutableStateOf("Nouvel onglet")
        var url by mutableStateOf("")
        var progress by mutableIntStateOf(0)
        @Volatile var pageUrl: String? = null
        @Volatile var pageTitle: String = ""
        /** Onglet restauré pas encore affiché : chargé seulement quand on l'ouvre (comme Chrome). */
        var pendingState: Bundle? = null
        var pendingUrl: String? = null
    }

    private enum class ListMode { History, Bookmarks }

    private lateinit var container: FrameLayout
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private val tabs = mutableStateListOf<BrowserTab>()
    private var currentIndex by mutableIntStateOf(0)
    private val current: BrowserTab get() = tabs[currentIndex.coerceIn(0, tabs.lastIndex)]
    private val web: WebView get() = current.web
    private val streams get() = current.streams
    private val pageTitle get() = current.title
    private val pageProgress get() = current.progress
    private val currentPage get() = current.pageUrl

    private var address by mutableStateOf("")
    private var canBack by mutableStateOf(false)
    private var canForward by mutableStateOf(false)
    private var filtered by mutableIntStateOf(0)
    private var panel by mutableStateOf<Panel>(Panel.None)
    private var fullscreen by mutableStateOf(false)
    private var showTabs by mutableStateOf(false)
    private var listMode by mutableStateOf<ListMode?>(null)
    private var starred by mutableStateOf(false)
    private var editingAddress by mutableStateOf(false)
    private var confirmClear by mutableStateOf(false)
    /** Change à chaque modification de l'historique/des favoris pour rafraîchir l'affichage. */
    private var storeVersion by mutableIntStateOf(0)
    private var analyzeJob: Job? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        BrowserStore.init(filesDir)
        val extra = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }
        container = FrameLayout(this)
        // Reprend les onglets de la dernière fois (avec leur historique Précédent/Suivant).
        if (!restoreSession()) newTab(extra ?: HOME) else extra?.let { newTab(it) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    confirmClear -> confirmClear = false
                    listMode != null -> listMode = null
                    showTabs -> showTabs = false
                    panel != Panel.None -> closePanel()
                    web.canGoBack() -> web.goBack()
                    tabs.size > 1 -> closeTab(currentIndex)
                    else -> finish()
                }
            }
        })

        setContent { GeniusTheme { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }?.let { newTab(it) }
    }

    override fun onPause() {
        saveSession()
        super.onPause()
    }

    // ------------------------------------------------------------------ mémoire (onglets, cookies)

    /** Enregistre les onglets, leur historique, l'historique global et les cookies (connexions aux sites). */
    private fun saveSession() {
        if (tabs.isEmpty()) return
        val dir = BrowserStore.stateDir()
        dir?.listFiles()?.forEach { it.delete() }
        val pages = tabs.mapIndexed { i, t ->
            if (dir != null) {
                val state = t.pendingState ?: if (t.pendingUrl == null) Bundle().takeIf { t.web.saveState(it) != null } else null
                state?.let { writeBundle(File(dir, "$i.bin"), it) }
            }
            BrowserStore.Page(t.url.ifBlank { t.pendingUrl ?: HOME }, t.title)
        }
        BrowserStore.saveTabs(pages, currentIndex)
        BrowserStore.save()
        CookieManager.getInstance().flush()
    }

    private fun restoreSession(): Boolean {
        val saved = BrowserStore.loadTabs() ?: return false
        val dir = BrowserStore.stateDir()
        saved.tabs.forEachIndexed { i, page ->
            val tab = BrowserTab(createWebView())
            tab.title = page.title.ifBlank { "Nouvel onglet" }
            tab.url = page.url; tab.pageUrl = page.url; tab.pageTitle = page.title
            tab.pendingState = dir?.let { readBundle(File(it, "$i.bin")) }
            tab.pendingUrl = page.url
            tabs.add(tab)
        }
        selectTab(saved.current)
        return true
    }

    private fun writeBundle(file: File, bundle: Bundle) {
        val p = Parcel.obtain()
        try { p.writeBundle(bundle); file.writeBytes(p.marshall()) } catch (_: Throwable) { file.delete() } finally { p.recycle() }
    }

    private fun readBundle(file: File): Bundle? {
        if (!file.exists()) return null
        val p = Parcel.obtain()
        return try {
            val bytes = file.readBytes()
            p.unmarshall(bytes, 0, bytes.size); p.setDataPosition(0)
            p.readBundle(javaClass.classLoader)
        } catch (_: Throwable) { null } finally { p.recycle() }
    }

    /** Charge un onglet restauré au moment où il devient visible. */
    private fun wake(tab: BrowserTab) {
        val state = tab.pendingState
        val url = tab.pendingUrl ?: return
        tab.pendingState = null; tab.pendingUrl = null
        val ok = state != null && runCatching { tab.web.restoreState(state) != null }.getOrDefault(false)
        if (!ok) tab.web.loadUrl(url)
    }

    private fun refreshStar() { starred = tabs.isNotEmpty() && BrowserStore.isBookmarked(current.url) }

    /** Efface l'historique, les cookies, le cache et les données des sites. */
    private fun clearBrowsingData() {
        BrowserStore.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        tabs.forEach { it.web.clearCache(true); it.web.clearHistory(); it.web.clearFormData() }
        storeVersion++
        toast("Données de navigation effacées")
    }

    // ------------------------------------------------------------------ onglets

    private fun newTab(url: String = HOME) {
        val tab = BrowserTab(createWebView())
        tabs.add(tab)
        selectTab(tabs.lastIndex)
        tab.web.loadUrl(toUrl(url))
    }

    private fun selectTab(index: Int) {
        currentIndex = index.coerceIn(0, tabs.lastIndex)
        container.removeAllViews()
        container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        wake(current)
        address = current.url
        updateNav()
        refreshStar()
        panel = Panel.None
    }

    private fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        tabs.removeAt(index)
        tab.web.destroy()
        if (tabs.isEmpty()) newTab() else selectTab(if (currentIndex >= tabs.size) tabs.lastIndex else currentIndex.coerceAtMost(tabs.lastIndex))
    }

    private fun tabOf(view: WebView?): BrowserTab? = tabs.firstOrNull { it.web === view }

    private fun isCurrent(view: WebView?) = tabs.isNotEmpty() && view === web

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        val self = this
        addJavascriptInterface(Bridge(self), "GeniusSniffer")

        // Liens de fichiers (vidéo, zip, apk…) : proposés au téléchargement.
        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val tab = tabOf(self) ?: return@setDownloadListener
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val kind = if (mimeType?.startsWith("video") == true) "MP4" else "Fichier"
            addStream(tab, DetectedStream(url, kind, tab.pageUrl, name.substringBeforeLast('.')), front = true)
            if (tab === current) panel = Panel.Streams
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { tabOf(view)?.progress = newProgress }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tabOf(view)?.let { it.title = title.orEmpty().ifBlank { it.title }; it.pageTitle = it.title }
                view?.url?.let { BrowserStore.updateTitle(it, title.orEmpty()) }
            }

            // Nouvelle fenêtre demandée par un lien : ouverte dans un nouvel onglet (sauf pub).
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (!isUserGesture) { filtered++; return false }
                val probe = WebView(this@SnifferActivity)
                probe.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url?.toString().orEmpty()
                        if (target.startsWith("http") && !AdFilter.isAd(target)) newTab(target) else filtered++
                        v?.destroy()
                        return true
                    }
                }
                (resultMsg?.obj as? WebView.WebViewTransport)?.webView = probe
                resultMsg?.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                customView = view; customCallback = callback
                container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                fullscreen = true
            }

            override fun onHideCustomView() = exitFullscreen()
        }

        webViewClient = object : WebViewClient() {
            private fun onUrl(view: WebView?, url: String?) {
                val tab = tabOf(view) ?: return
                url?.let { tab.url = it; tab.pageUrl = it }
                if (isCurrent(view)) { url?.let { if (!editingAddress) address = it }; updateNav(); refreshStar() }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = onUrl(view, url)

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(AdFilter.COSMETIC_JS, null)
                view?.evaluateJavascript(SCAN_JS, null)
                onUrl(view, url)
                url?.let { BrowserStore.addVisit(it, view?.title.orEmpty()) }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) = onUrl(view, url)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme.orEmpty()
                if (scheme != "http" && scheme != "https") {
                    // intent://, market://… : uniquement si l'utilisateur a touché un lien (ex. ouvrir une appli).
                    if (scheme == "intent" || !request.hasGesture()) { filtered++; return true }
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
                // Redirection publicitaire de la page principale : ignorée (la page actuelle reste affichée).
                if (request.isForMainFrame && AdFilter.isAd(uri.toString())) { filtered++; return true }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val r = request ?: return null
                val url = r.url.toString()
                if (!r.isForMainFrame && AdFilter.isAd(url)) {
                    runOnUiThread { filtered++ }
                    return EMPTY()
                }
                inspect(view, url, r.requestHeaders["Referer"])
                return null
            }
        }
    }

    private fun updateNav() {
        if (tabs.isEmpty()) return
        canBack = web.canGoBack(); canForward = web.canGoForward()
    }

    private fun exitFullscreen() {
        customView?.let { container.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        fullscreen = false
    }

    private fun toUrl(text: String): String {
        val t = text.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            ' ' !in t && '.' in t -> "https://$t"
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        }
    }

    // ------------------------------------------------------------------ détection

    private fun classify(url: String): String? {
        val lower = url.lowercase()
        val path = lower.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".m3u8") || ".m3u8" in lower -> "HLS"
            path.endsWith(".mpd") -> "DASH"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "MP4"
            path.endsWith(".webm") -> "WebM"
            path.endsWith(".mkv") || path.endsWith(".mov") || path.endsWith(".flv") -> "Vidéo"
            else -> null
        }
    }

    /** Appelé depuis le thread réseau de la WebView : retrouve l'onglet sur le thread principal. */
    private fun inspect(view: WebView?, url: String, referer: String?, fallbackKind: String? = null) {
        if (!url.startsWith("http")) return
        val kind = classify(url) ?: fallbackKind ?: return
        if (AdFilter.isAd(url)) return
        runOnUiThread {
            val tab = tabOf(view) ?: return@runOnUiThread
            val s = DetectedStream(url, kind, referer ?: tab.pageUrl, tab.pageTitle)
            addStream(tab, s, front = kind == "HLS" && "master" in url.lowercase())
        }
    }

    private fun addStream(tab: BrowserTab, s: DetectedStream, front: Boolean) {
        val key = s.url.substringBefore('?')
        if (tab.streams.any { it.url.substringBefore('?') == key }) return
        if (front) tab.streams.add(0, s) else tab.streams.add(s)
    }

    private inner class Bridge(private val owner: WebView) {
        @JavascriptInterface
        fun found(url: String) = inspect(owner, url, null, fallbackKind = "Vidéo")
    }

    private fun headersFor(s: DetectedStream): Map<String, String> = buildMap {
        put("User-Agent", web.settings.userAgentString)
        s.referer?.let { ref ->
            put("Referer", ref)
            runCatching { java.net.URI(ref) }.getOrNull()?.let { put("Origin", "${it.scheme}://${it.host}") }
        }
        CookieManager.getInstance().getCookie(s.url)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
    }

    // ------------------------------------------------------------------ fenêtre flottante

    private fun open(s: DetectedStream) {
        analyzeJob?.cancel()
        if (s.kind == "Fichier") {
            val name = MediaSaver.sanitize(URLUtil.guessFileName(s.url, null, null))
            DownloadManager.enqueue(name, null, MediaKind.VIDEO, DownloadJob.DirectJob(s.url, headersFor(s), name))
            toast("Ajouté aux téléchargements")
            closePanel()
            return
        }
        panel = Panel.Loading(s)
        val headers = headersFor(s)
        analyzeJob = lifecycleScope.launch {
            panel = try {
                val hls = if (s.kind == "HLS") async(Dispatchers.IO) { Hls.inspect(s.url, headers) } else null
                val r = Analyzer.analyzeStream(s.url, headers, s.title.ifBlank { pageTitle }, currentPage)
                Panel.Ready(s, r, hls?.await())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Panel.Failed(s, Analyzer.cleanError(e))
            }
        }
    }

    private fun closePanel() {
        analyzeJob?.cancel()
        panel = Panel.None
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ interface

    @Composable
    private fun Screen() {
        val tasks by DownloadManager.tasks.collectAsState()
        val active = tasks.count { it.active }
        Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                if (!fullscreen) TopBar(active)
                if (!fullscreen && editingAddress) Suggestions()
                if (!fullscreen && pageProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Cyan, trackColor = Color.Transparent,
                    )
                }
                AndroidView({ container }, Modifier.fillMaxWidth().weight(1f))
                if (!fullscreen) BottomBar()
            }

            // Pastille lumineuse : vidéos détectées
            AnimatedVisibility(
                streams.isNotEmpty() && panel == Panel.None && !fullscreen,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 76.dp),
                enter = slideInVertically { it }, exit = slideOutVertically { it },
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.horizontalGradient(listOf(Cyan, Violet, Magenta)))
                        .clickable { if (streams.size == 1) open(streams[0]) else panel = Panel.Streams }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (streams.size == 1) "Télécharger la vidéo" else "${streams.size} vidéos détectées",
                        color = Color.Black, fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showTabs) TabsOverlay()
            listMode?.let { PagesOverlay(it) }
            if (confirmClear) AlertDialog(
                onDismissRequest = { confirmClear = false },
                containerColor = Surface1,
                title = { Text("Effacer les données ?", color = Color.White) },
                text = { Text("Historique, cookies (vous serez déconnecté des sites), cache et données des sites. Les favoris et les onglets sont conservés.", color = TextDim) },
                confirmButton = { TextButton(onClick = { confirmClear = false; clearBrowsingData() }) { Text("Effacer", color = Danger) } },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler", color = TextDim) } },
            )

            if (panel != Panel.None) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { closePanel() },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Surface1)
                            .border(1.dp, Brush.linearGradient(listOf(Cyan, Violet, Magenta)), RoundedCornerShape(24.dp))
                            .clickable(enabled = false) {}
                            .padding(18.dp),
                    ) {
                        when (val p = panel) {
                            Panel.Streams -> StreamList()
                            is Panel.Loading -> LoadingPanel(p.stream)
                            is Panel.Ready -> ReadyPanel(p)
                            is Panel.Failed -> FailedPanel(p)
                            Panel.None -> Unit
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TabsOverlay() {
        Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Onglets (${tabs.size})", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Cyan)
                        .clickable { showTabs = false; newTab() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nouvel onglet", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconButton(onClick = { showTabs = false }) { Icon(Icons.Rounded.Close, "Fermer", tint = TextDim) }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs.toList().withIndex().toList(), key = { System.identityHashCode(it.value) }) { (i, tab) ->
                    val selected = i == currentIndex
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Cyan.copy(alpha = 0.1f) else Surface1)
                            .border(1.dp, if (selected) Cyan else Color(0xFF22222F), RoundedCornerShape(16.dp))
                            .clickable { selectTab(i); showTabs = false }
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Public, null, tint = if (selected) Cyan else TextDim, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tab.title.ifBlank { "Nouvel onglet" }, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(displayAddress(tab.url), color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (tab.streams.isNotEmpty()) {
                            Text("${tab.streams.size} 🎬", color = Cyan, fontSize = 12.sp)
                        }
                        IconButton(onClick = { closeTab(i) }) { Icon(Icons.Rounded.Close, "Fermer l'onglet", tint = TextDim) }
                    }
                }
            }
        }
    }

    @Composable
    private fun TopBar(active: Int) {
        val focus = LocalFocusManager.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (editingAddress) address else displayAddress(address),
                onValueChange = { address = it },
                singleLine = true,
                placeholder = { Text("Rechercher sur Google ou saisir une adresse", fontSize = 13.sp, color = TextDim) },
                modifier = Modifier.weight(1f).height(52.dp).onFocusChanged {
                    if (it.isFocused && !editingAddress) address = current.url
                    editingAddress = it.isFocused
                },
                shape = RoundedCornerShape(26.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    streams.clear(); web.loadUrl(toUrl(address)); focus.clearFocus(); web.requestFocus()
                }),
                trailingIcon = {
                    if (!editingAddress) IconButton(onClick = {
                        val on = BrowserStore.toggleBookmark(current.url, current.title)
                        starred = on; storeVersion++
                        toast(if (on) "Ajouté aux favoris" else "Retiré des favoris")
                    }) {
                        Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favori", tint = if (starred) Cyan else TextDim)
                    } else if (address.isNotEmpty()) IconButton(onClick = { address = "" }) {
                        Icon(Icons.Rounded.Close, "Effacer", tint = TextDim)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF2A2A38),
                    focusedContainerColor = Surface2, unfocusedContainerColor = Surface2, cursorColor = Cyan,
                ),
            )
            Spacer(Modifier.width(6.dp))
            // Raccourci vers la liste des téléchargements
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Surface2).clickable {
                    startActivity(Intent(this@SnifferActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1))
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Download, "Téléchargements", tint = if (active > 0) Cyan else TextDim)
                if (active > 0) Text(
                    "$active", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Magenta).padding(horizontal = 5.dp),
                )
            }
        }
    }

    private fun displayAddress(a: String): String =
        if (a.startsWith("https://www.google.com/search")) Uri.parse(a).getQueryParameter("q") ?: a
        else a.removePrefix("https://").removePrefix("http://").removePrefix("www.")

    @Composable
    private fun BottomBar() {
        Row(
            Modifier.fillMaxWidth().background(Surface1).padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { web.goBack() }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Précédent", tint = if (canBack) Color.White else TextDim.copy(alpha = .4f))
            }
            IconButton(onClick = { web.goForward() }, enabled = canForward) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Suivant", tint = if (canForward) Color.White else TextDim.copy(alpha = .4f))
            }
            IconButton(onClick = { streams.clear(); web.loadUrl(HOME) }) { Icon(Icons.Rounded.Home, "Accueil", tint = Color.White) }
            IconButton(onClick = { web.reload() }) { Icon(Icons.Rounded.Refresh, "Recharger", tint = Color.White) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Rounded.Shield, null, tint = Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("$filtered", color = TextDim, fontSize = 12.sp)
            }
            // Onglets : carré avec le nombre d'onglets ouverts
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { showTabs = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(24.dp).border(2.dp, Color.White, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("${tabs.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Surface2) {
                    @Composable
                    fun item(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) =
                        DropdownMenuItem(
                            text = { Text(text, color = Color.White) },
                            leadingIcon = { Icon(icon, null, tint = Cyan) },
                            onClick = { menu = false; action() },
                        )
                    item("Nouvel onglet", Icons.Rounded.Add) { newTab() }
                    item(if (starred) "Retirer des favoris" else "Ajouter aux favoris", if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder) {
                        starred = BrowserStore.toggleBookmark(current.url, current.title); storeVersion++
                    }
                    item("Favoris", Icons.Rounded.Star) { listMode = ListMode.Bookmarks }
                    item("Historique", Icons.Rounded.History) { listMode = ListMode.History }
                    item("Onglets", Icons.Rounded.Tab) { showTabs = true }
                    item("Effacer les données", Icons.Rounded.DeleteSweep) { confirmClear = true }
                    item("Fermer le navigateur", Icons.Rounded.Close) { finish() }
                }
            }
        }
    }

    /** Suggestions sous la barre d'adresse (favoris + historique), comme Chrome. */
    @Composable
    private fun Suggestions() {
        val focus = LocalFocusManager.current
        val list = remember(address, storeVersion) { BrowserStore.suggest(address) }
        if (list.isEmpty()) return
        Column(Modifier.fillMaxWidth().background(Surface1).padding(vertical = 4.dp)) {
            list.forEach { p ->
                val fav = BrowserStore.isBookmarked(p.url)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        streams.clear(); web.loadUrl(p.url); focus.clearFocus(); web.requestFocus()
                    }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.History, null, tint = if (fav) Cyan else TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title.ifBlank { displayAddress(p.url) }, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(displayAddress(p.url), color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    /** Écran Historique ou Favoris. */
    @Composable
    private fun PagesOverlay(mode: ListMode) {
        var query by remember { mutableStateOf("") }
        val pages = remember(mode, storeVersion, query) {
            val all = if (mode == ListMode.History) BrowserStore.history else BrowserStore.bookmarks
            val q = query.trim().lowercase()
            if (q.isEmpty()) all else all.filter { (it.title + " " + it.url).lowercase().contains(q) }
        }
        Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mode == ListMode.History) "Historique" else "Favoris",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f),
                )
                if (mode == ListMode.History && pages.isNotEmpty()) TextButton(onClick = { BrowserStore.clearHistory(); storeVersion++ }) {
                    Text("Tout effacer", color = Danger)
                }
                IconButton(onClick = { listMode = null }) { Icon(Icons.Rounded.Close, "Fermer", tint = TextDim) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Rechercher", color = TextDim, fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF2A2A38),
                    focusedContainerColor = Surface2, unfocusedContainerColor = Surface2, cursorColor = Cyan,
                ),
            )
            if (pages.isEmpty()) {
                Text(
                    if (mode == ListMode.History) "Aucune page visitée." else "Aucun favori. Touchez ☆ dans la barre d'adresse pour en ajouter.",
                    color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                var lastDay = ""
                pages.forEach { p ->
                    if (mode == ListMode.History) {
                        val day = dayLabel(p.time)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "d$day") {
                                Text(day, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                            }
                        }
                    }
                    item(key = p.url) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { listMode = null; streams.clear(); web.loadUrl(p.url) }
                                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Public, null, tint = TextDim, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title.ifBlank { displayAddress(p.url) }, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (mode == ListMode.History) timeOf(p.time) + " · " else "") + displayAddress(p.url),
                                    color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                if (mode == ListMode.History) BrowserStore.removeVisit(p.url) else BrowserStore.removeBookmark(p.url)
                                storeVersion++; refreshStar()
                            }) { Icon(Icons.Rounded.Close, "Supprimer", tint = TextDim) }
                        }
                    }
                }
            }
        }
    }

    private fun dayLabel(time: Long): String {
        val c = Calendar.getInstance()
        val today = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        c.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        c.timeInMillis = time
        return when (c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)) {
            today -> "Aujourd'hui"
            yesterday -> "Hier"
            else -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date(time)).replaceFirstChar { it.uppercase() }
        }
    }

    private fun timeOf(time: Long) = SimpleDateFormat("HH:mm", Locale.FRENCH).format(Date(time))

    @Composable
    private fun ColumnScope.StreamList() {
        Text("Vidéos détectées", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Text("Choisissez le flux à télécharger.", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(streams.toList()) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(12.dp))
                        .clickable { open(s) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Movie, null, tint = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.friendlyKind, fontWeight = FontWeight.SemiBold, color = Cyan, fontSize = 13.sp)
                        Text(s.label, fontSize = 12.sp, color = TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelTitle(text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { closePanel() }) { Icon(Icons.Rounded.Close, "Fermer", tint = TextDim) }
        }
    }

    @Composable
    private fun ColumnScope.LoadingPanel(s: DetectedStream) {
        PanelTitle("Télécharger le fichier")
        Text(Hls.cleanTitle(s.title.ifBlank { pageTitle }), color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Analyse du flux (qualités, durée, taille)…", color = TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
    }

    @Composable
    private fun ColumnScope.FailedPanel(p: Panel.Failed) {
        PanelTitle("Flux illisible")
        Text(p.message, color = Danger, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Text("Essayez un autre flux de la liste, ou lancez d'abord la lecture de la vidéo.", color = TextDim, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (streams.size > 1) TextButton(onClick = { panel = Panel.Streams }) { Text("Autres flux", color = Cyan) }
            TextButton(onClick = { open(p.stream) }) { Text("Réessayer", color = Cyan) }
        }
    }

    @Composable
    private fun ColumnScope.ReadyPanel(p: Panel.Ready) {
        val r = p.result
        var name by remember(p) { mutableStateOf(Hls.cleanTitle(r.title).ifBlank { "Vidéo" }) }
        var selected by remember(p) { mutableStateOf(r.videoOptions.firstOrNull()) }
        PanelTitle("Télécharger le fichier")
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text("Enregistrer sous", color = TextDim, fontSize = 12.sp)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = Color.White),
                trailingIcon = { Text(".mp4", color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp)) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF2A2A38),
                    focusedContainerColor = Surface2, unfocusedContainerColor = Surface2, cursorColor = Cyan,
                ),
            )
            Spacer(Modifier.height(10.dp))
            // Infos du flux, comme IDM : taille, morceaux, durée
            val duration = p.hls?.durationSec?.takeIf { it > 0 } ?: r.durationSec
            val info = listOfNotNull(
                selected?.sizeBytes?.takeIf { it > 0 }?.let { (if (selected!!.sizeApprox) "≈ " else "") + DownloadManager.fmtSize(it) },
                p.hls?.segments?.takeIf { it > 0 }?.let { "$it morceaux" },
                duration.takeIf { it > 0 }?.let { "durée ${Hls.fmtDuration(it)}" },
            ).joinToString(" · ")
            if (info.isNotBlank()) Text(info, color = TextDim, fontSize = 13.sp)
            Text("${p.stream.friendlyKind} · ${p.stream.label}", color = TextDim.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            if (r.videoOptions.isNotEmpty()) {
                Text("Qualité", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                r.videoOptions.forEach { opt -> PanelQuality(opt, opt == selected) { selected = opt } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.audio != null) {
                TextButton(onClick = {
                    Enqueue.audio(r, name); toast("Audio ajouté aux téléchargements"); closePanel()
                }) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Magenta, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Audio", color = Magenta)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { closePanel() }) { Text("Annuler", color = TextDim) }
            val opt = selected
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(if (opt != null) Cyan else Surface2)
                    .clickable(enabled = opt != null) {
                        Enqueue.video(r, opt!!, name.ifBlank { "Vidéo" })
                        toast("Téléchargement lancé : ${opt.label}")
                        closePanel()
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Download, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Commencer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun PanelQuality(opt: VideoOption, selected: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                .border(1.dp, if (selected) Cyan else Color(0xFF262634), RoundedCornerShape(12.dp))
                .background(if (selected) Cyan.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) Cyan else TextDim, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(opt.label, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(80.dp))
            Text(opt.detail, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            if (opt.sizeBytes > 0) Text(
                (if (opt.sizeApprox) "≈ " else "") + DownloadManager.fmtSize(opt.sizeBytes), color = TextDim, fontSize = 12.sp,
            )
        }
    }

    override fun onDestroy() {
        analyzeJob?.cancel()
        BrowserStore.save()
        tabs.forEach { it.web.destroy() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val HOME = "https://www.google.com"

        private fun EMPTY() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

        /** Signale les sources des balises <video>, puis réessaie (lecteurs qui chargent tard). */
        private val SCAN_JS = """
            (function(){
              function scan(){
                document.querySelectorAll('video, video source').forEach(function(v){
                  var s = v.currentSrc || v.src;
                  if (s && s.indexOf('http') === 0) GeniusSniffer.found(s);
                });
              }
              scan();
              if (!window.__geniusScan) { window.__geniusScan = setInterval(scan, 2000); }
            })();
        """.trimIndent()
    }
}

