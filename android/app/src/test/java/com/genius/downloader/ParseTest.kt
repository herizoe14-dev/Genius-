package com.genius.downloader

import com.genius.downloader.engine.MediaResult
import com.genius.downloader.engine.PlaylistResult
import com.genius.downloader.engine.YtDlp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie l'interprétation de la sortie `yt-dlp -J` (structure réelle de yt-dlp). */
class ParseTest {

    @Test
    fun youtubeQualities() {
        val json = JSONObject(
            """{"id":"abc","title":"Clip","uploader":"Chan","duration":212.0,"webpage_url":"https://www.youtube.com/watch?v=abc",
            "thumbnail":"https://i.ytimg.com/vi/abc/maxres.jpg","formats":[
             {"format_id":"sb0","protocol":"mhtml","vcodec":"none","acodec":"none","height":45},
             {"format_id":"140","vcodec":"none","acodec":"mp4a.40.2","abr":129.5,"ext":"m4a","filesize":3400000,"protocol":"https"},
             {"format_id":"251","vcodec":"none","acodec":"opus","abr":135.0,"ext":"webm","filesize":3500000,"protocol":"https"},
             {"format_id":"18","vcodec":"avc1.42001E","acodec":"mp4a.40.2","height":360,"tbr":500,"ext":"mp4","protocol":"https"},
             {"format_id":"137","vcodec":"avc1.640028","acodec":"none","height":1080,"fps":30,"tbr":4000,"ext":"mp4","filesize":50000000,"protocol":"https"},
             {"format_id":"248","vcodec":"vp9","acodec":"none","height":1080,"fps":30,"tbr":3000,"ext":"webm","protocol":"https"},
             {"format_id":"313","vcodec":"vp9","acodec":"none","height":2160,"fps":60,"tbr":20000,"ext":"webm","protocol":"https"}
            ]}""",
        )
        val r = YtDlp.parse("https://youtu.be/abc", json) as MediaResult
        assertEquals(listOf(2160, 1080, 360), r.videoOptions.map { it.height })
        assertEquals("4K60", r.videoOptions[0].label)
        assertEquals("313+ba/313/b", r.videoOptions[0].ytdlpFormat)
        assertEquals("137+ba/137/b", r.videoOptions[1].ytdlpFormat) // H.264 préféré
        assertEquals("18/b", r.videoOptions[2].ytdlpFormat) // déjà avec audio
        assertEquals(53500000L, r.videoOptions[1].sizeBytes)
        assertTrue(r.audio!!.label.startsWith("OPUS"))
        assertEquals("https://www.youtube.com/watch?v=abc", r.videoOptions[0].ytdlpUrl)
    }

    @Test
    fun tiktokNoWatermark() {
        val json = JSONObject(
            """{"id":"7","title":"tt","formats":[
             {"format_id":"download","format_note":"watermarked","vcodec":"h264","acodec":"aac","height":1024,"tbr":2000,"protocol":"https"},
             {"format_id":"bytevc1_1080p_1","vcodec":"h265","acodec":"aac","height":1080,"tbr":1500,"protocol":"https"},
             {"format_id":"h264_540p_1","vcodec":"h264","acodec":"aac","height":540,"tbr":900,"protocol":"https"}
            ]}""",
        )
        val r = YtDlp.parse("https://www.tiktok.com/@a/video/7", json) as MediaResult
        assertEquals(listOf(1080, 540), r.videoOptions.map { it.height })
        assertFalse(r.videoOptions.any { "download" in it.ytdlpFormat!! })
        assertEquals("bytevc1_1080p_1/b", r.videoOptions[0].ytdlpFormat)
        assertTrue(r.audio != null)
    }

    @Test
    fun flatPlaylist() {
        val json = JSONObject(
            """{"_type":"playlist","title":"Mix","uploader":"U","entries":[
             {"_type":"url","ie_key":"Youtube","id":"a1","url":"https://www.youtube.com/watch?v=a1","title":"One","duration":61,
              "thumbnails":[{"url":"https://i.ytimg.com/a1/s.jpg","width":120,"height":90},{"url":"https://i.ytimg.com/a1/l.jpg","width":480,"height":360}]},
             {"_type":"url","ie_key":"Youtube","id":"a2","url":"a2","title":"Two"}
            ]}""",
        )
        val p = YtDlp.parse("https://www.youtube.com/playlist?list=X", json) as PlaylistResult
        assertEquals(2, p.entries.size)
        assertEquals("https://i.ytimg.com/a1/l.jpg", p.entries[0].thumbnail)
        assertEquals("https://www.youtube.com/watch?v=a2", p.entries[1].url)
    }

    @Test
    fun carousel() {
        val json = JSONObject(
            """{"_type":"playlist","title":"Post","uploader":"ig","entries":[
             {"id":"1","title":"Post","url":"https://cdn/x1.jpg","ext":"jpg","vcodec":"none","thumbnail":"https://cdn/x1.jpg"},
             {"id":"2","title":"Post","ext":"mp4","thumbnail":"https://cdn/t2.jpg","formats":[
               {"format_id":"a","vcodec":"avc1","acodec":"mp4a","height":720,"url":"https://cdn/v720.mp4","protocol":"https"},
               {"format_id":"b","vcodec":"avc1","acodec":"mp4a","height":1080,"url":"https://cdn/v1080.mp4","protocol":"https"}]}
            ]}""",
        )
        val r = YtDlp.parse("https://www.instagram.com/p/X/", json) as MediaResult
        assertEquals(2, r.gallery.size)
        assertFalse(r.gallery[0].isVideo)
        assertTrue(r.gallery[1].isVideo)
        assertEquals("https://cdn/v1080.mp4", r.gallery[1].url)
    }
}
