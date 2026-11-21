package com.genius.downloader.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Mémoire du navigateur Genius (comme Chrome) : onglets ouverts, historique et favoris,
 * conservés dans des fichiers JSON privés de l'appli.
 */
object BrowserStore {
    data class Page(val url: String, val title: String, val time: Long = System.currentTimeMillis())
    data class SavedTabs(val tabs: List<Page>, val current: Int)

    const val MAX_HISTORY = 2000

    private var dir: File? = null
    private val historyList = ArrayList<Page>()
    private val bookmarkList = ArrayList<Page>()
    private var loaded = false

    fun init(filesDir: File) {
        if (loaded) return
        dir = File(filesDir, "browser").apply { mkdirs() }
        historyList.clear(); historyList.addAll(readPages("history.json"))
        bookmarkList.clear(); bookmarkList.addAll(readPages("bookmarks.json"))
        loaded = true
    }

    // ------------------------------------------------------------------ historique

    val history: List<Page> get() = synchronized(this) { historyList.toList() }

    /** Ajoute une visite en tête (sans doublon : la visite précédente de la même adresse est retirée). */
    fun addVisit(url: String, title: String) = synchronized(this) {
        if (!isRecordable(url)) return
        val old = historyList.indexOfFirst { it.url == url }
        val t = title.ifBlank { historyList.getOrNull(old)?.title.orEmpty() }
        if (old >= 0) historyList.removeAt(old)
        historyList.add(0, Page(url, t))
        while (historyList.size > MAX_HISTORY) historyList.removeAt(historyList.lastIndex)
    }

    /** Met à jour le titre de la dernière visite de cette adresse (le titre arrive souvent après la page). */
    fun updateTitle(url: String, title: String) = synchronized(this) {
        if (title.isBlank()) return
        val i = historyList.indexOfFirst { it.url == url }
        if (i >= 0) historyList[i] = historyList[i].copy(title = title)
    }

    fun removeVisit(url: String) = synchronized(this) { historyList.removeAll { it.url == url }; save() }

    fun clearHistory() = synchronized(this) { historyList.clear(); save() }

    // ------------------------------------------------------------------ favoris

    val bookmarks: List<Page> get() = synchronized(this) { bookmarkList.toList() }

    fun isBookmarked(url: String) = synchronized(this) { bookmarkList.any { it.url == url } }

    /** Ajoute ou retire des favoris ; renvoie true si la page est maintenant en favori. */
    fun toggleBookmark(url: String, title: String): Boolean = synchronized(this) {
        val removed = bookmarkList.removeAll { it.url == url }
        if (!removed) bookmarkList.add(0, Page(url, title.ifBlank { url }))
        save()
        !removed
    }

    fun removeBookmark(url: String) = synchronized(this) { bookmarkList.removeAll { it.url == url }; save() }

    // ------------------------------------------------------------------ suggestions

    /** Suggestions de la barre d'adresse : favoris puis historique qui contiennent le texte tapé. */
    fun suggest(query: String, limit: Int = 6): List<Page> = synchronized(this) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ').filter { it.isNotBlank() }
        fun match(p: Page): Boolean {
            val hay = (p.title + " " + p.url).lowercase()
            return words.all { it in hay }
        }
        (bookmarkList.filter(::match) + historyList.filter(::match))
            .distinctBy { it.url }
            .take(limit)
    }

    // ------------------------------------------------------------------ onglets

    fun saveTabs(tabs: List<Page>, current: Int) {
        val arr = JSONArray()
        tabs.forEach { arr.put(it.toJson()) }
        write("tabs.json", JSONObject().put("current", current).put("tabs", arr).toString())
    }

    fun loadTabs(): SavedTabs? = runCatching {
        val o = JSONObject(read("tabs.json") ?: return null)
        val tabs = pagesOf(o.getJSONArray("tabs")).filter { it.url.startsWith("http") }
        if (tabs.isEmpty()) null else SavedTabs(tabs, o.optInt("current", 0).coerceIn(0, tabs.lastIndex))
    }.getOrNull()

    /** Dossier des états complets des onglets (historique Précédent/Suivant de chaque onglet). */
    fun stateDir(): File? = dir?.let { File(it, "tabstate").apply { mkdirs() } }

    // ------------------------------------------------------------------ fichiers

    fun save() = synchronized(this) {
        write("history.json", toJson(historyList))
        write("bookmarks.json", toJson(bookmarkList))
    }

    /** Efface historique, favoris non compris, et onglets enregistrés. */
    fun clearAll() = synchronized(this) {
        historyList.clear()
        save()
        dir?.let { File(it, "tabs.json").delete(); File(it, "tabstate").deleteRecursively() }
    }

    fun isRecordable(url: String) = url.startsWith("http://") || url.startsWith("https://")

    fun toJson(pages: List<Page>): String = JSONArray().apply { pages.forEach { put(it.toJson()) } }.toString()

    fun fromJson(text: String): List<Page> = runCatching { pagesOf(JSONArray(text)) }.getOrDefault(emptyList())

    private fun Page.toJson() = JSONObject().put("u", url).put("t", title).put("d", time)

    private fun pagesOf(arr: JSONArray): List<Page> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val u = o.optString("u").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Page(u, o.optString("t"), o.optLong("d"))
    }

    private fun readPages(name: String) = read(name)?.let(::fromJson).orEmpty()

    private fun read(name: String): String? = dir?.let { File(it, name) }?.takeIf { it.exists() }?.let {
        runCatching { it.readText() }.getOrNull()
    }

    /** Écriture atomique (fichier temporaire puis renommage) pour ne jamais corrompre les données. */
    private fun write(name: String, text: String) {
        val d = dir ?: return
        runCatching {
            val tmp = File(d, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, name))
        }
    }
}
