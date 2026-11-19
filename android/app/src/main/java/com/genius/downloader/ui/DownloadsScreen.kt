package com.genius.downloader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genius.downloader.download.DownloadManager
import com.genius.downloader.download.DownloadTask
import com.genius.downloader.download.MediaKind
import com.genius.downloader.download.Status
import com.genius.downloader.ui.theme.Cyan
import com.genius.downloader.ui.theme.Danger
import com.genius.downloader.ui.theme.Success
import com.genius.downloader.ui.theme.Surface2
import com.genius.downloader.ui.theme.TextDim

@Composable
fun DownloadsScreen(padding: PaddingValues) {
    val tasks by DownloadManager.tasks.collectAsState()
    val ctx = LocalContext.current

    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.DownloadDone, null, Modifier.size(64.dp), tint = TextDim.copy(alpha = 0.4f))
                Spacer(Modifier.height(10.dp))
                Text("Aucun téléchargement", color = TextDim)
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val active = tasks.count { it.active }
                Text(
                    if (active > 0) "$active en cours · ${tasks.size} au total" else "${tasks.size} téléchargement(s)",
                    color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                if (tasks.any { !it.active }) {
                    TextButton(onClick = { DownloadManager.clearFinished() }) { Text("Effacer terminés", color = Cyan) }
                }
            }
        }
        items(tasks, key = { it.id }) { t -> TaskRow(t, ctx) }
    }
}

@Composable
private fun TaskRow(t: DownloadTask, ctx: Context) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Thumb(t.thumbnail, t.thumbHeaders, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Icon(
                    when (t.kind) {
                        MediaKind.VIDEO -> Icons.Rounded.Videocam
                        MediaKind.AUDIO -> Icons.Rounded.MusicNote
                        MediaKind.IMAGE -> Icons.Rounded.Image
                    },
                    null,
                    Modifier.align(Alignment.BottomEnd).padding(3.dp).size(18.dp)
                        .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(2.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val (label, color) = when (t.status) {
                    Status.QUEUED -> "En attente" to TextDim
                    Status.RUNNING -> "${(t.progress * 100).toInt()} %" to Cyan
                    Status.DONE -> "Terminé" to Success
                    Status.FAILED -> "Échec" to Danger
                    Status.CANCELED -> "Annulé" to TextDim
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (t.info.isNotBlank() && t.status != Status.FAILED) {
                        Text("  ·  ${t.info}", color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                t.error?.let { Text(it, color = Danger.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
            Column {
                when (t.status) {
                    Status.QUEUED, Status.RUNNING ->
                        IconButton(onClick = { DownloadManager.cancel(t.id) }) { Icon(Icons.Rounded.Close, "Annuler", tint = TextDim) }
                    Status.DONE -> {
                        IconButton(onClick = { open(ctx, t) }) { Icon(Icons.Rounded.OpenInNew, "Ouvrir", tint = Cyan) }
                        IconButton(onClick = { share(ctx, t) }) { Icon(Icons.Rounded.Share, "Partager", tint = TextDim) }
                    }
                    Status.FAILED, Status.CANCELED ->
                        IconButton(onClick = { DownloadManager.retry(t.id) }) { Icon(Icons.Rounded.Refresh, "Réessayer", tint = Cyan) }
                }
                if (!t.active) {
                    IconButton(onClick = { DownloadManager.remove(t.id) }) { Icon(Icons.Rounded.DeleteOutline, "Retirer", tint = TextDim) }
                }
            }
        }
        if (t.status == Status.RUNNING || t.status == Status.QUEUED) {
            Spacer(Modifier.height(10.dp))
            if (t.status == Status.RUNNING && t.progress > 0f) {
                LinearProgressIndicator(
                    progress = { t.progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Cyan, trackColor = Surface2,
                )
            } else {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Cyan, trackColor = Surface2,
                )
            }
        }
    }
}

private fun open(ctx: Context, t: DownloadTask) {
    val uri = t.uris.firstOrNull()?.let(Uri::parse) ?: return
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, t.mime ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(i)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Aucune application pour ouvrir ce fichier", Toast.LENGTH_SHORT).show()
    }
}

private fun share(ctx: Context, t: DownloadTask) {
    val uris = t.uris.map(Uri::parse)
    if (uris.isEmpty()) return
    val i = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.setType(t.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
