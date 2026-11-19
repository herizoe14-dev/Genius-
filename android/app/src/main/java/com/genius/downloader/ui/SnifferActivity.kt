package com.genius.downloader.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.genius.downloader.ui.theme.Bg
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.GeniusTheme
import com.genius.downloader.ui.theme.Magenta
import com.genius.downloader.ui.theme.Surface1
import com.genius.downloader.ui.theme.TextDim
import com.genius.downloader.ui.theme.Violet
import org.json.JSONObject

/** Un flux vidéo repéré pendant la navigation. */
data class DetectedStream(val url: String, val kind: String, val referer: String?) {
    /** Nom compréhensible : une playlist « master » regroupe toutes les qualités. */
    val friendlyKind: String
        get() {
            val name = url.substringBefore('?').substringAfterLast('/').lowercase()
            return when {
                kind == "HLS" && ("master" in name || name == "playlist.m3u8") -> "Vidéo · toutes les qualités (recommandé)"
                kind == "HLS" && Regex("""(audio|-a\d+\.m3u8$|aac)""").containsMatchIn(name) && !Regex("""-v\d""").containsMatchIn(name) -> "Piste audio seule (HLS)"
                kind == "HLS" -> "Vidéo · une seule qualité (HLS)"
                kind == "DASH" -> "Vidéo · toutes les qualités (DASH)"
                else -> "Fichier vidéo ($kind)"
            }
        }

    val label: String
        get() = runCatching { java.net.URI(url) }.getOrNull()?.let { u ->
            "${u.host} · …${u.path.takeLast(40)}"
        } ?: url.take(60)
}

/**
 * Détecteur de flux : un navigateur intégré qui repère les vidéos chargées par la page
 * (MP4/WebM, HLS .m3u8, DASH .mpd), y compris dans les lecteurs en iframe.
 * Les pop-ups publicitaires sont bloquées.
 */
class SnifferActivity : ComponentActivity() {
    private lateinit var web: WebView
    private val streams = mutableStateListOf<DetectedStream>()
    private var address by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var pageTitle by mutableStateOf("")
    private var showList by mutableStateOf(false)
    @Volatile private var currentPage: String? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra(EXTRA_URL).orEmpty()
        address = start

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.setSupportMultipleWindows(true) // pour pouvoir refuser les pop-ups
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(Bridge(), "GeniusSniffer")

            webChromeClient = object : WebChromeClient() {
                // Pop-ups / nouvelles fenêtres (publicités) : refusées.
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) = false
                override fun onReceivedTitle(view: WebView?, title: String?) { pageTitle = title.orEmpty() }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loading = true
                    url?.let { address = it; currentPage = it }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                    view?.evaluateJavascript(SCAN_JS, null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme.orEmpty()
                    // Bloque intent://, market://, liens d'applis… souvent utilisés par les pubs.
                    return scheme != "http" && scheme != "https"
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    request?.let { inspect(it.url.toString(), it.requestHeaders["Referer"]) }
                    return null
                }
            }
            loadUrl(normalize(start))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    showList -> showList = false
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        setContent { GeniusTheme { Screen() } }
    }

    private fun normalize(u: String) = if (u.startsWith("http")) u else "https://$u"

    /** Analyse chaque requête réseau de la page (et des iframes). */
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

    private fun inspect(url: String, referer: String?, fallbackKind: String? = null) {
        if (!url.startsWith("http")) return
        val lower = url.lowercase()
        val kind = classify(url) ?: fallbackKind ?: return
        // Ignore les régies publicitaires connues.
        if (AD_HINTS.any { it in lower }) return
        val key = url.substringBefore('?')
        runOnUiThread {
            if (streams.none { it.url.substringBefore('?') == key }) {
                // Les playlists HLS « master » en premier.
                val s = DetectedStream(url, kind, referer ?: currentPage)
                if (kind == "HLS" && "master" in lower) streams.add(0, s) else streams.add(s)
            }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun found(url: String) = inspectVideoTag(url)
    }

    /** Sources trouvées dans les balises <video> (sans extension visible). */
    private fun inspectVideoTag(url: String) = inspect(url, currentPage, fallbackKind = "Vidéo")

    private fun choose(s: DetectedStream) {
        val headers = JSONObject()
        headers.put("User-Agent", web.settings.userAgentString)
        s.referer?.let { ref ->
            headers.put("Referer", ref)
            runCatching { java.net.URI(ref) }.getOrNull()?.let { headers.put("Origin", "${it.scheme}://${it.host}") }
        }
        CookieManager.getInstance().getCookie(s.url)?.takeIf { it.isNotBlank() }?.let { headers.put("Cookie", it) }
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(RES_STREAM, s.url)
                .putExtra(RES_HEADERS, headers.toString())
                .putExtra(RES_TITLE, pageTitle)
                .putExtra(RES_PAGE, currentPage),
        )
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        Box(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finish() }) { Icon(Icons.Rounded.Close, "Fermer") }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { streams.clear(); web.loadUrl(normalize(address.trim())) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF2A2A38),
                            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
                        ),
                    )
                    IconButton(onClick = { web.reload() }) { Icon(Icons.Rounded.Refresh, "Recharger") }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
                Text(
                    "Lancez la lecture de la vidéo : Genius détecte le flux automatiquement.",
                    color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
                AndroidView({ web }, Modifier.fillMaxWidth().weight(1f))
            }

            // Pastille lumineuse : nombre de vidéos détectées
            AnimatedVisibility(
                streams.isNotEmpty() && !showList,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.horizontalGradient(listOf(Cyan, Violet, Magenta)))
                        .clickable { if (streams.size == 1) choose(streams[0]) else showList = true }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (streams.size == 1) "Télécharger la vidéo détectée" else "${streams.size} vidéos détectées",
                        color = Color.Black, fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showList) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable { showList = false },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 460.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(Surface1).padding(16.dp),
                    ) {
                        Text("Vidéos détectées", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text("Les flux HLS « master » contiennent toutes les qualités.", color = TextDim, fontSize = 12.sp)
                        Spacer(Modifier.size(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(streams.toList()) { s ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF2A2A38), RoundedCornerShape(12.dp))
                                        .clickable { choose(s) }.padding(12.dp),
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
                }
            }
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val RES_STREAM = "stream"
        const val RES_HEADERS = "headers"
        const val RES_TITLE = "title"
        const val RES_PAGE = "page"

        private val AD_HINTS = listOf("doubleclick", "googlesyndication", "/ads/", "adserver", "vast", "preroll", "imasdk")

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
