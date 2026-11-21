package com.genius.downloader

import com.genius.downloader.engine.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BrowserStoreTest {
    @Test
    fun historyFavoritesTabsPersist() {
        val dir = Files.createTempDirectory("genius").toFile()
        BrowserStore.init(dir)
        BrowserStore.clearHistory()
        BrowserStore.addVisit("https://a.com/", "Site A")
        BrowserStore.addVisit("https://b.com/", "")
        BrowserStore.updateTitle("https://b.com/", "Site B")
        BrowserStore.addVisit("https://a.com/", "") // revisite : remonte en tête, garde le titre
        BrowserStore.addVisit("about:blank", "x")   // ignoré
        assertEquals(listOf("https://a.com/", "https://b.com/"), BrowserStore.history.map { it.url })
        assertEquals("Site A", BrowserStore.history[0].title)

        assertTrue(BrowserStore.toggleBookmark("https://b.com/", "Site B"))
        assertTrue(BrowserStore.isBookmarked("https://b.com/"))
        assertEquals("https://b.com/", BrowserStore.suggest("site")[0].url) // favori d'abord
        assertEquals(1, BrowserStore.suggest("site a").size)

        BrowserStore.saveTabs(listOf(BrowserStore.Page("https://a.com/", "A"), BrowserStore.Page("https://b.com/", "B")), 1)
        BrowserStore.save()
        val tabs = BrowserStore.loadTabs()
        assertNotNull(tabs); assertEquals(2, tabs!!.tabs.size); assertEquals(1, tabs.current)

        // Relecture depuis les fichiers
        val again = BrowserStore.fromJson(java.io.File(dir, "browser/history.json").readText())
        assertEquals(2, again.size)
        assertFalse(BrowserStore.toggleBookmark("https://b.com/", "Site B"))
        dir.deleteRecursively()
    }
}
