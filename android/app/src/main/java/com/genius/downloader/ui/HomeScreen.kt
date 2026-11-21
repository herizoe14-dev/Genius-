package com.genius.downloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.TravelExplore
import android.content.Intent
import com.genius.downloader.engine.Analyzer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genius.downloader.download.DownloadManager
import com.genius.downloader.engine.MediaResult
import com.genius.downloader.engine.PlaylistResult
import com.genius.downloader.engine.VideoOption
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.Danger
import com.genius.downloader.ui.theme.Magenta
import com.genius.downloader.ui.theme.Surface1
import com.genius.downloader.ui.theme.Surface2
import com.genius.downloader.ui.theme.TextDim
import com.genius.downloader.ui.theme.Violet

@Composable
fun HomeScreen(vm: MainViewModel, padding: PaddingValues) {
    val state by vm.state.collectAsState()
    val input by vm.input.collectAsState()
    val engineReady by vm.engineReady.collectAsState()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val openSniffer: () -> Unit = {
        val url = Analyzer.extractUrl(input) ?: "https://www.google.com"
        ctx.startActivity(Intent(ctx, SnifferActivity::class.java).putExtra(SnifferActivity.EXTRA_URL, url))
    }
    val playlistSel = remember(state) {
        ((state as? UiState.Done)?.analysis as? PlaylistResult)?.let { p ->
            mutableStateListOf<Int>().apply { addAll(p.entries.indices) }
        } ?: mutableStateListOf()
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 18.dp, end = 18.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlowButton(
                    loading = state is UiState.Loading,
                    onClick = {
                        // Pas de champ de saisie : on prend le lien copié (ou celui partagé vers l'app).
                        val clip = clipboard.getText()?.text.orEmpty()
                        vm.analyze(if (Analyzer.extractUrl(clip) != null) clip else input)
                    },
                )
                Text(
                    when {
                        state is UiState.Loading -> "Analyse en cours…"
                        !engineReady -> "Préparation du moteur (1er lancement)…"
                        else -> "Appuyez pour analyser"
                    },
                    color = TextDim, fontSize = 13.sp,
                )
                TextButton(onClick = openSniffer) {
                    Icon(Icons.Rounded.TravelExplore, null, tint = Cyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Navigateur Genius (détecteur de vidéos)", color = Cyan, fontSize = 13.sp)
                }
            }
        }

        when (val s = state) {
            is UiState.Error -> item { ErrorCard(s.message, onSniff = openSniffer) { vm.analyze() } }
            is UiState.Done -> when (val a = s.analysis) {
                is MediaResult -> mediaResult(a, vm)
                is PlaylistResult -> playlistResult(a, vm, playlistSel)
            }
            else -> item { PlatformsHint() }
        }
    }
}

@Composable
private fun PlatformsHint() {
    Text(
        "TikTok · YouTube · Instagram · X · Pixiv · Facebook\net plus de 1000 sites — sans filigrane, sans serveur",
        color = TextDim.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ErrorCard(message: String, onSniff: (() -> Unit)? = null, onRetry: () -> Unit) {
    val unsupported = message.contains("Unsupported URL", true) || message.contains("Aucun média", true) ||
        message.contains("Unable to extract", true) || message.contains("No video", true)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Danger)
            Spacer(Modifier.width(10.dp))
            Text(
                if (unsupported) "Ce site n'est pas reconnu directement." else message,
                Modifier.weight(1f), fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
        }
        if (unsupported && onSniff != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Ouvrez la page dans le navigateur Genius et lancez la vidéo : une fenêtre de téléchargement apparaît.",
                color = TextDim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            BigAction("Ouvrir dans le navigateur Genius", Icons.Rounded.TravelExplore, onClick = onSniff)
        }
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Réessayer", color = Cyan) }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Color(0xFF22222F), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Thumb(url: String?, headers: Map<String, String>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(url).apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Surface2),
    )
}

private enum class Tab(val label: String, val icon: ImageVector) {
    VIDEO("Vidéo", Icons.Rounded.Videocam),
    AUDIO("Audio", Icons.Rounded.MusicNote),
    IMAGES("Médias", Icons.Rounded.Image),
}

private fun LazyListScope.mediaResult(r: MediaResult, vm: MainViewModel) {
    item(key = "head-${r.sourceUrl}") {
        AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 3 }) {
            Card {
                Box {
                    Thumb(r.thumbnail, r.thumbnailHeaders, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
                    Chip(r.platform.label, Modifier.align(Alignment.TopStart).padding(8.dp))
                    if (r.durationSec > 0) Chip(fmtDuration(r.durationSec), Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                r.uploader?.let { Text(it, color = TextDim, fontSize = 13.sp) }
            }
        }
    }
    item(key = "body-${r.sourceUrl}") { MediaBody(r, vm) }
}

@Composable
private fun MediaBody(r: MediaResult, vm: MainViewModel) {
    val tabs = buildList {
        if (r.videoOptions.isNotEmpty()) add(Tab.VIDEO)
        if (r.gallery.isNotEmpty()) add(Tab.IMAGES)
        if (r.audio != null) add(Tab.AUDIO)
    }
    if (tabs.isEmpty()) {
        ErrorCard("Aucun média téléchargeable trouvé") { vm.analyze() }
        return
    }
    var tab by remember(r) { mutableStateOf(tabs.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tabs.size > 1) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEach { t ->
                    val sel = t == tab
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Brush.horizontalGradient(listOf(Cyan.copy(alpha = .25f), Violet.copy(alpha = .25f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable { tab = t }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, null, Modifier.size(18.dp), tint = if (sel) Cyan else TextDim)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (t == Tab.IMAGES) "${t.label} (${r.gallery.size})" else t.label,
                            color = if (sel) Color.White else TextDim, fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        when (tab) {
            Tab.VIDEO -> VideoPanel(r, vm)
            Tab.AUDIO -> AudioPanel(r, vm)
            Tab.IMAGES -> GalleryPanel(r, vm)
        }
    }
}

@Composable
private fun VideoPanel(r: MediaResult, vm: MainViewModel) {
    var selected by remember(r) { mutableStateOf(r.videoOptions.first()) }
    Card {
        Text("Choisir la qualité", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        r.videoOptions.forEach { opt -> QualityRow(opt, opt == selected) { selected = opt } }
        Spacer(Modifier.height(10.dp))
        BigAction("Télécharger ${selected.label}", Icons.Rounded.Download) { vm.downloadVideo(r, selected) }
    }
}

@Composable
private fun QualityRow(opt: VideoOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) Cyan else Color(0xFF262634), RoundedCornerShape(12.dp))
            .background(if (selected) Cyan.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
            tint = if (selected) Cyan else TextDim, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(opt.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(76.dp))
        Text(opt.detail, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        if (opt.sizeBytes > 0) Text((if (opt.sizeApprox) "≈ " else "") + DownloadManager.fmtSize(opt.sizeBytes), color = TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun AudioPanel(r: MediaResult, vm: MainViewModel) {
    val a = r.audio ?: return
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Magenta, Violet))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AudioFile, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Audio · meilleure qualité disponible", fontWeight = FontWeight.SemiBold)
                Text(
                    a.label + if (a.sizeBytes > 0) " · ${DownloadManager.fmtSize(a.sizeBytes)}" else "",
                    color = TextDim, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BigAction("Télécharger l'audio", Icons.Rounded.MusicNote, Magenta) { vm.downloadAudio(r) }
    }
}

@Composable
private fun GalleryPanel(r: MediaResult, vm: MainViewModel) {
    val selected = remember(r) { mutableStateListOf<Int>().apply { addAll(r.gallery.indices) } }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.size}/${r.gallery.size} sélectionné(s)", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = {
                if (selected.size == r.gallery.size) selected.clear()
                else { selected.clear(); selected.addAll(r.gallery.indices) }
            }) { Text(if (selected.size == r.gallery.size) "Aucun" else "Tout", color = Cyan) }
        }
        Spacer(Modifier.height(6.dp))
        r.gallery.indices.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i ->
                    val item = r.gallery[i]
                    val sel = i in selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(2.dp, if (sel) Cyan else Color.Transparent), RoundedCornerShape(12.dp))
                            .clickable { if (sel) selected.remove(i) else selected.add(i) },
                    ) {
                        Thumb(item.thumb, item.headers, Modifier.matchParentSize())
                        if (item.isVideo) Icon(Icons.Rounded.PlayCircle, null, Modifier.align(Alignment.Center).size(34.dp), tint = Color.White)
                        Icon(
                            if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                                .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                            tint = if (sel) Cyan else Color.White,
                        )
                        Text(
                            "${i + 1}", fontSize = 11.sp, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        BigAction(
            "Télécharger (${selected.size})", Icons.Rounded.Download, enabled = selected.isNotEmpty(),
        ) { vm.downloadGallery(r, selected.sorted().map { r.gallery[it] }) }
    }
}

private fun LazyListScope.playlistResult(p: PlaylistResult, vm: MainViewModel, selected: SnapshotStateList<Int>) {
    item(key = "pl-${p.sourceUrl}") { PlaylistHeader(p, vm, selected) }
    item(key = "pl-sel") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Éléments", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = {
                if (selected.size == p.entries.size) selected.clear()
                else { selected.clear(); selected.addAll(p.entries.indices) }
            }) { Text(if (selected.size == p.entries.size) "Aucun" else "Tout", color = Cyan) }
        }
    }
    itemsIndexed(p.entries, key = { i, e -> "e$i-${e.url}" }) { i, e ->
        val sel = i in selected
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1)
                .clickable { if (sel) selected.remove(i) else selected.add(i) }
                .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                sel, { if (it) selected.add(i) else selected.remove(i) },
                colors = CheckboxDefaults.colors(checkedColor = Cyan, checkmarkColor = Color.Black),
            )
            if (e.thumbnail != null) {
                Thumb(e.thumbnail, emptyMap(), Modifier.width(80.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("${i + 1}. ${e.title}", fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.durationSec > 0) Text(fmtDuration(e.durationSec), color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PlaylistHeader(p: PlaylistResult, vm: MainViewModel, selected: SnapshotStateList<Int>) {
    var audio by remember(p) { mutableStateOf(false) }
    var maxH by remember(p) { mutableStateOf(1080) }
    val caps = listOf(0 to "Max", 2160 to "4K", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Cyan, Violet))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.Black) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(p.platform.label, p.uploader, "${p.entries.size} éléments").joinToString(" · "),
                    color = TextDim, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle("Vidéo", !audio, Icons.Rounded.Videocam, Modifier.weight(1f)) { audio = false }
            Toggle("Audio", audio, Icons.Rounded.MusicNote, Modifier.weight(1f)) { audio = true }
        }
        if (!audio) {
            Spacer(Modifier.height(10.dp))
            Text("Qualité maximale", color = TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            caps.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (h, label) -> Toggle(label, maxH == h, null, Modifier.weight(1f)) { maxH = h } }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigAction(
            "Tout télécharger (${selected.size})",
            Icons.Rounded.Download,
            if (audio) Magenta else Cyan,
            enabled = selected.isNotEmpty(),
        ) { vm.downloadPlaylist(p, selected.sorted().map { p.entries[it] }, audio, maxH) }
    }
}

@Composable
private fun Toggle(label: String, selected: Boolean, icon: ImageVector?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) Cyan else Color(0xFF2A2A38), RoundedCornerShape(12.dp))
            .background(if (selected) Cyan.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Cyan else TextDim)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) Color.White else TextDim, fontSize = 14.sp)
    }
}

@Composable
fun BigAction(
    text: String,
    icon: ImageVector,
    color: Color = Cyan,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, style = TextStyle(fontSize = 16.sp))
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

fun fmtDuration(s: Int): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
