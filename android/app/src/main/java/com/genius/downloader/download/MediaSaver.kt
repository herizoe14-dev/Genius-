package com.genius.downloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

enum class MediaKind { VIDEO, AUDIO, IMAGE }

/** Copie un fichier terminé vers le stockage public (Films/Musique/Images › Genius). */
object MediaSaver {

    private val AUDIO_EXT = setOf("m4a", "mp3", "opus", "ogg", "aac", "flac", "wav", "weba")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif", "bmp")

    fun kindOf(file: File): MediaKind = when (file.extension.lowercase()) {
        in AUDIO_EXT -> MediaKind.AUDIO
        in IMAGE_EXT -> MediaKind.IMAGE
        else -> MediaKind.VIDEO
    }

    fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "opus" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "weba" -> "audio/webm"
            "mkv" -> "video/x-matroska"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } ?: when (kindOf(file)) {
            MediaKind.AUDIO -> "audio/*"
            MediaKind.IMAGE -> "image/*"
            MediaKind.VIDEO -> "video/*"
        }
    }

    /** Renvoie l'URI (content://) du fichier publié. */
    fun publish(context: Context, file: File): Uri {
        val kind = kindOf(file)
        val (dir, collection) = when (kind) {
            MediaKind.VIDEO -> Environment.DIRECTORY_MOVIES to collectionVideo()
            MediaKind.AUDIO -> Environment.DIRECTORY_MUSIC to collectionAudio()
            MediaKind.IMAGE -> Environment.DIRECTORY_PICTURES to collectionImages()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                insert(context, file, collection, "$dir/Genius")
            } catch (e: IllegalArgumentException) {
                // Type MIME refusé par cette collection : on range dans Téléchargements.
                insert(context, file, MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${Environment.DIRECTORY_DOWNLOADS}/Genius")
            }
        } else {
            @Suppress("DEPRECATION")
            val target = File(Environment.getExternalStoragePublicDirectory(dir), "Genius").apply { mkdirs() }
            val out = uniqueFile(target, file.name)
            file.copyTo(out, overwrite = false)
            file.delete()
            MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(mimeOf(out)), null)
            FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        }
    }

    private fun insert(context: Context, file: File, collection: Uri, relativePath: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(file))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("MediaStore insert a échoué")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        file.delete()
        return uri
    }

    private fun collectionVideo(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun collectionAudio(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private fun collectionImages(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        while (f.exists()) f = File(dir, "${name.substringBeforeLast('.')} ($i).${name.substringAfterLast('.')}").also { i++ }
        return f
    }

    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(90).ifBlank { "genius" }
}
