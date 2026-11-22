package com.genius.downloader.engine

/**
 * Suit les flux vidéo d'une page pour n'afficher que celui qui joue à l'écran :
 * - les variantes d'une playlist « master » (480p, 720p, audio…) sont rangées sous leur master ;
 * - le flux dont les morceaux (.ts/.m4s) sont téléchargés en dernier est le flux « actif » ;
 * - un nouveau flux (autre épisode, autre lecteur) devient actif dès qu'il apparaît.
 */
class StreamTracker {
    /** Adresse d'un flux (sans paramètres) → groupe (adresse du master ou du flux lui-même). */
    private val groupOf = HashMap<String, String>()
    /** Dossier des morceaux → groupe. */
    private val dirs = HashMap<String, String>()
    var active: String? = null
        private set

    @Synchronized
    fun reset() { groupOf.clear(); dirs.clear(); active = null }

    /** Nouveau flux détecté. Renvoie son groupe. */
    @Synchronized
    fun add(url: String): String {
        val k = key(url)
        val g = groupOf.getOrPut(k) { k }
        dirs.putIfAbsent(dirOf(k), g)
        if (g == k) active = g
        return g
    }

    /** Liens trouvés dans une playlist : variantes d'un master, ou premier morceau d'une variante. */
    @Synchronized
    fun link(playlistUrl: String, children: List<String>) {
        val g = groupOf[key(playlistUrl)] ?: key(playlistUrl)
        for (c in children) {
            val ck = key(c)
            val old = groupOf[ck]
            groupOf[ck] = g
            dirs[dirOf(ck)] = g
            // La variante avait été vue avant son master : on la range dessous.
            if (old != null && old != g) {
                groupOf.entries.filter { it.value == old }.forEach { it.setValue(g) }
                dirs.entries.filter { it.value == old }.forEach { it.setValue(g) }
                if (active == old) active = g
            }
        }
    }

    /** Une requête de la page (morceau vidéo…) : renvoie true si le flux actif a changé. */
    @Synchronized
    fun touch(url: String): Boolean {
        if (dirs.isEmpty()) return false
        val k = key(url)
        val g = groupOf[k] ?: dirs[dirOf(k)] ?: return false
        if (g == active) return false
        active = g
        return true
    }

    @Synchronized
    fun groupOf(url: String): String = groupOf[key(url)] ?: key(url)

    /** Flux à afficher : uniquement ceux du groupe actif, le master en premier. */
    @Synchronized
    fun <T> visible(all: List<T>, urlOf: (T) -> String): List<T> {
        val a = active ?: return all
        val shown = all.filter { groupOf(urlOf(it)) == a }
        if (shown.isEmpty()) return all
        return shown.sortedBy { if (key(urlOf(it)) == a) 0 else 1 }
    }

    companion object {
        fun key(url: String) = url.substringBefore('#').substringBefore('?')
        fun dirOf(k: String) = k.substringBeforeLast('/') + "/"
    }
}
