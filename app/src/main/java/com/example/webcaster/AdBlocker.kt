package com.example.webcaster

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Simple domain based ad blocker.
 *
 * Every request the page makes passes through MainActivity.shouldInterceptRequest.
 * If the request goes to a known ad or tracking server, we answer it ourselves with
 * an empty response, so the advert never downloads.
 *
 * A small built in list is always used. For much stronger blocking, put a hosts file
 * (for example the StevenBlack "unified hosts" list) at app/src/main/assets/adblock_hosts.txt
 * and it is loaded automatically on start up.
 */
object AdBlocker {

    @Volatile var enabled = true
    @Volatile var blockedCount = 0
        private set

    private val blockedHosts = HashSet<String>()

    private val builtIn = listOf(
        // Ad networks
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "adnxs.com", "adsrvr.org", "advertising.com",
        "amazon-adsystem.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
        "media.net", "revcontent.com", "mgid.com", "zedo.com", "adform.net", "adcolony.com",
        "applovin.com", "unityads.unity3d.com", "moatads.com", "yieldmo.com", "sharethrough.com",
        "teads.tv", "33across.com", "indexww.com", "bidswitch.net", "contextweb.com",
        "lijit.com", "sovrn.com", "spotxchange.com", "springserve.com", "imasdk.googleapis.com",
        // Pop up and redirect networks often found on streaming sites
        "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com",
        "juicyads.com", "trafficjunky.net", "hilltopads.net", "adcash.com", "clickadu.com",
        "onclickads.net", "onclkds.com", "a-ads.com", "galaksion.com", "monetag.com",
        "highperformanceformat.com", "realsrv.com", "pushwhy.com",
        // Trackers
        "google-analytics.com", "googletagmanager.com", "scorecardresearch.com",
        "quantserve.com", "hotjar.com", "mixpanel.com", "facebook.net", "connect.facebook.net",
        "ads-twitter.com", "analytics.tiktok.com", "bat.bing.com", "clarity.ms"
    )

    private val emptyBytes = ByteArray(0)

    fun init(context: Context) {
        blockedHosts.addAll(builtIn)
        // Optional larger list in hosts file format: "0.0.0.0 ads.example.com"
        try {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.substringBefore('#').trim()
                    if (line.isEmpty()) return@forEach
                    val parts = line.split(Regex("\\s+"))
                    val host = if (parts.size >= 2) parts[1] else parts[0]
                    if (host.contains('.') && host != "localhost") blockedHosts.add(host.lowercase())
                }
            }
        } catch (e: Exception) {
            // No extra list supplied; the built in list is used on its own.
        }
    }

    /** True if this URL points at a blocked host or any subdomain of one. */
    fun isBlocked(url: String): Boolean {
        if (!enabled) return false
        var host = Uri.parse(url).host?.lowercase() ?: return false
        while (true) {
            if (host in blockedHosts) return true
            val dot = host.indexOf('.')
            if (dot < 0 || host.indexOf('.', dot + 1) < 0) return false // stop at "example.com"
            host = host.substring(dot + 1)
        }
    }

    /** An empty reply that the page receives instead of the advert. */
    fun emptyResponse(): WebResourceResponse {
        blockedCount++
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(emptyBytes))
    }

    fun resetCount() { blockedCount = 0 }

    /** Hides common ad containers that are left behind as empty boxes. */
    val COSMETIC_JS = """
        (function () {
          if (document.getElementById('__vibe_adhide')) return;
          var s = document.createElement('style');
          s.id = '__vibe_adhide';
          s.textContent = [
            'ins.adsbygoogle', '[id^="google_ads"]', '[id^="div-gpt-ad"]', 'iframe[src*="doubleclick"]',
            '.adsbox', '.ad-banner', '.ad-container', '.ad-slot', '.advert', '.advertisement',
            '[class*="sponsored-"]', '[id*="taboola"]', '[class*="outbrain"]'
          ].join(',') + '{display:none !important;}';
          (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
}
