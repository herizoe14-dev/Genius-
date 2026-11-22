package com.genius.downloader

import com.genius.downloader.engine.Hls
import com.genius.downloader.engine.StreamTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTrackerTest {
    private val a = "https://prx-1357.vmget.online/hls2/01/02994/3gzogvpnpn912_,n,l,.urlset/master.m3u8?t=1"
    private val aL = "https://prx-1357.vmget.online/hls2/01/02994/3gzogvpnpn912_l/index-v1-a1.m3u8?t=1"
    private val aN = "https://prx-1357.vmget.online/hls2/01/02994/3gzogvpnpn912_n/index-v1-a1.m3u8?t=1"
    private val b = "https://prx-1356.vmpx.online/hls2/04/02978/xz94mspdivoi_,n,l,.urlset/master.m3u8?t=2"
    private val bL = "https://prx-1356.vmpx.online/hls2/04/02978/xz94mspdivoi_l/index-v1-a1.m3u8?t=2"

    @Test
    fun keepsOnlyThePlayingStream() {
        val t = StreamTracker()
        val all = mutableListOf<String>()
        fun add(u: String) { all += u; t.add(u) }

        add(a); t.link(a, listOf(aN, aL))
        add(aL); add(aN)
        assertEquals(listOf(a, aL, aN), t.visible(all) { it })

        // Autre lecteur / autre épisode : seul le nouveau flux reste affiché
        add(b); add(bL)   // variante vue avant que le master soit lu
        t.link(b, listOf(bL))
        assertEquals(listOf(b, bL), t.visible(all) { it })

        // Les morceaux de A sont téléchargés : c'est A qui joue à l'écran
        t.link(aL, listOf("https://prx-1357.vmget.online/hls2/01/02994/3gzogvpnpn912_l/seg-1-v1-a1.ts?t=1"))
        assertTrue(t.touch("https://prx-1357.vmget.online/hls2/01/02994/3gzogvpnpn912_l/seg-7-v1-a1.ts?t=1"))
        assertEquals(listOf(a, aL, aN), t.visible(all) { it })

        t.reset()
        assertEquals(all, t.visible(all) { it })
    }

    @Test
    fun playlistLinks() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nindex-v1-a1.m3u8\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"audio/a.m3u8\"\n"
        assertEquals(
            listOf("https://x.com/v/index-v1-a1.m3u8", "https://x.com/v/audio/a.m3u8"),
            Hls.links("https://x.com/v/master.m3u8", master),
        )
        val media = "#EXTM3U\n#EXTINF:4,\nhttps://cdn.y.com/s/seg-1.ts\n#EXTINF:4,\nhttps://cdn.y.com/s/seg-2.ts\n"
        assertEquals(listOf("https://cdn.y.com/s/seg-1.ts"), Hls.links("https://x.com/v/index.m3u8", media))
    }
}
