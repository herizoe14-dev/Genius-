package com.genius.downloader.engine

/**
 * Filtre de publicités « doux » : les requêtes vers les régies connues reçoivent une réponse vide
 * (la page continue de fonctionner normalement), les redirections et pop-ups publicitaires sont ignorées.
 * Les pages elles-mêmes ne sont jamais bloquées.
 */
object AdFilter {
    private val HOSTS = setOf(
        // Régies classiques
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "amazon-adsystem.com", "adnxs.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
        "adform.net", "teads.tv", "moatads.com", "scorecardresearch.com", "zedo.com", "revcontent.com", "mgid.com",
        // Pop-unders / redirections (sites de streaming)
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "adsterra.com", "adsterratools.com",
        "exoclick.com", "exosrv.com", "exdynsrv.com", "juicyads.com", "trafficjunky.net", "trafficjunky.com",
        "adcash.com", "hilltopads.net", "clickadu.com", "onclickads.net", "onclickalgo.com", "onclickperformance.com",
        "monetag.com", "a-ads.com", "yllix.com", "ad-maven.com", "admaven.com", "adskeeper.com", "bidgear.com",
        "realsrv.com", "tsyndicate.com", "ero-advertising.com", "plugrush.com", "trafficstars.com", "magsrv.com",
        "acscdn.com", "highperformanceformat.com", "profitablegatecpm.com", "effectivegatecpm.com",
        "displayvertising.com", "dtscout.com", "whos.amung.us", "histats.com", "adspyglass.com", "adreactor.com",
        "pushwhy.com", "pushnest.com", "richpush.co", "evadav.com", "galaksion.com", "clickaine.com", "zeydoo.com",
        "admitad.com", "betterads.co", "streamads.net", "vidcrunch.com", "aniview.com", "pemsrv.com",
    )

    private val URL_HINTS = listOf(
        "/afu.php", "zoneid=", "scrwidth=", "popunder", "clickunder", "/pop.js", "invoke_layer", "/ads.js?",
        "adsbygoogle", "/pagead/", "prebid", "/vast.", "vast.xml",
    )

    /** CSS discret qui masque les emplacements publicitaires courants. */
    const val COSMETIC_JS = """
        (function(){
          if (document.getElementById('genius-adfilter')) return;
          var css = 'ins.adsbygoogle,[id^="google_ads"],[id^="div-gpt-ad"],iframe[src*="doubleclick"],' +
                    'iframe[src*="googlesyndication"],[class*="ad-banner"],[class*="adsbox"],[id*="taboola"],' +
                    '[class*="outbrain"],[id^="ad_"],.advertisement,.adsbygoogle{display:none!important;}';
          var s = document.createElement('style'); s.id = 'genius-adfilter'; s.textContent = css;
          (document.head || document.documentElement).appendChild(s);
        })();
    """

    fun host(url: String): String = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.").orEmpty()

    fun isAd(url: String): Boolean {
        val h = host(url)
        if (h.isEmpty()) return false
        if (HOSTS.any { h == it || h.endsWith(".$it") }) return true
        val lower = url.lowercase()
        return URL_HINTS.any { it in lower }
    }
}
