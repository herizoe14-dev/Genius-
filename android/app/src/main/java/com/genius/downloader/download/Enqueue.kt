package com.genius.downloader.download

import com.genius.downloader.Prefs
import com.genius.downloader.engine.GalleryItem
import com.genius.downloader.engine.MediaResult
import com.genius.downloader.engine.PlaylistEntry
import com.genius.downloader.engine.VideoOption

/** Transforme un choix de l'utilisateur en tâche de téléchargement. */
object Enqueue {

    private val VIDEO_ARGS = listOf("--merge-output-format", "mp4", "--embed-metadata")

    fun video(r: MediaResult, opt: VideoOption, fileTitle: String = r.title) {
        val job = if (opt.directUrl != null) {
            DownloadJob.DirectJob(opt.directUrl, opt.headers, fileName(fileTitle, "", opt.ext))
        } else {
            DownloadJob.YtDlpJob(
                opt.ytdlpUrl ?: r.sourceUrl,
                listOf("-f", opt.ytdlpFormat ?: "bv*+ba/b") + VIDEO_ARGS,
                opt.headers,
                fileTitle,
            )
        }
        DownloadManager.enqueue("$fileTitle · ${opt.label}", r.thumbnail, MediaKind.VIDEO, job, r.thumbnailHeaders)
    }

    fun audio(r: MediaResult, fileTitle: String = r.title) {
        val a = r.audio ?: return
        // Toujours via yt-dlp + ffmpeg : extrait la piste audio quel que soit le conteneur source.
        val job = DownloadJob.YtDlpJob(a.directUrl ?: a.ytdlpUrl ?: r.sourceUrl, audioArgs(), a.headers, fileTitle)
        DownloadManager.enqueue("$fileTitle · audio", r.thumbnail, MediaKind.AUDIO, job, r.thumbnailHeaders)
    }

    fun gallery(r: MediaResult, items: List<GalleryItem>) {
        items.forEachIndexed { i, item ->
            val idx = r.gallery.indexOf(item).takeIf { it >= 0 } ?: i
            DownloadManager.enqueue(
                title = "${r.title} · ${idx + 1}/${r.gallery.size}",
                thumbnail = item.thumb,
                kind = if (item.isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                job = DownloadJob.DirectJob(item.url, item.headers, fileName(r.title, "_${idx + 1}", safeExt(item.ext, item.isVideo))),
                thumbHeaders = item.headers,
            )
        }
    }

    /** [maxHeight] = 0 → qualité max. [audio] → audio seulement. */
    fun playlist(entries: List<PlaylistEntry>, audio: Boolean, maxHeight: Int) {
        entries.forEach { e ->
            val args = if (audio) audioArgs() else listOf(
                "-f", "bv*+ba/b",
                "-S", if (maxHeight > 0) "res:$maxHeight,fps,vcodec:h264,acodec:m4a" else "res,fps,vcodec:h264,acodec:m4a",
            ) + VIDEO_ARGS
            DownloadManager.enqueue(
                e.title, e.thumbnail,
                if (audio) MediaKind.AUDIO else MediaKind.VIDEO,
                DownloadJob.YtDlpJob(e.url, args, fileTitle = e.title),
            )
        }
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
}
