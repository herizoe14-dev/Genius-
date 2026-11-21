package com.genius.downloader

import com.genius.downloader.engine.AdFilter
import com.genius.downloader.engine.Hls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserToolsTest {
    @Test
    fun cleanTitles() {
        assertEquals("Film Turbo (2013)", Hls.cleanTitle("Film Turbo (2013) en streaming complet"))
        assertEquals("Mon Clip", Hls.cleanTitle("Regarder Mon Clip | MonSite"))
        assertEquals("Vidéo de vacances 2024", Hls.cleanTitle("Vidéo de vacances 2024 - Blog"))
    }

    @Test
    fun adFilter() {
        assertTrue(AdFilter.isAd("https://securepubads.g.doubleclick.net/tag/js/gpt.js"))
        assertTrue(AdFilter.isAd("https://cruzswim.org/4/e75aa56?refer=x&kw=%5B%5D&scrWidth=393&sub3=invoke_layer"))
        assertTrue(AdFilter.isAd("https://a.exoclick.com/tag.php"))
        assertFalse(AdFilter.isAd("https://www.google.com/search?q=chat"))
        assertFalse(AdFilter.isAd("https://www.youtube.com/watch?v=abc"))
        assertFalse(AdFilter.isAd("https://cdn.example.org/hls/master.m3u8"))
    }
}
