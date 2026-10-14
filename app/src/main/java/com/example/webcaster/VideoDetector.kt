package com.example.webcaster

import android.net.Uri

/** A video stream found on a web page. */
data class DetectedVideo(
    val url: String,
    val pageUrl: String,
    val pageTitle: String,
    val mimeType: String,
    val source: String // "network" (seen in traffic) or "page" (found in a <video> tag)
) {
    val label: String
        get() = when (mimeType) {
            MIME_HLS -> "HLS stream (m3u8)"
            MIME_DASH -> "DASH stream (mpd)"
            "video/mp4" -> "MP4 video"
            "video/webm" -> "WebM video"
            else -> "Video"
        }
}

const val MIME_HLS = "application/x-mpegurl"
const val MIME_DASH = "application/dash+xml"

/**
 * Decides whether a URL looks like a playable video or subtitle file.
 * This is the "sniffer" at the heart of apps like Web Video Caster:
 * it watches every request the page makes and picks out media URLs.
 */
object VideoDetector {

    private val videoExtensions = mapOf(
        "m3u8" to MIME_HLS,
        "mpd" to MIME_DASH,
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mov" to "video/mp4",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska"
    )

    // Segment files belong to a playlist we already caught, so they are ignored.
    private val ignoredExtensions = setOf("ts", "m4s", "aac", "key", "vtt.js")

    /** Returns the MIME type if the URL looks like a video, otherwise null. */
    fun videoMimeFor(url: String, responseMime: String? = null): String? {
        if (!url.startsWith("http")) return null // blob: and data: URLs cannot be cast
        responseMime?.lowercase()?.let { mime ->
            if (mime.contains("mpegurl")) return MIME_HLS
            if (mime.contains("dash+xml")) return MIME_DASH
            if (mime.startsWith("video/")) return mime.substringBefore(";")
        }
        val ext = extensionOf(url) ?: return guessFromQuery(url)
        if (ext in ignoredExtensions) return null
        return videoExtensions[ext] ?: guessFromQuery(url)
    }

    fun isSubtitle(url: String): Boolean {
        val ext = extensionOf(url) ?: return false
        return ext == "vtt" || ext == "srt"
    }

    private fun extensionOf(url: String): String? {
        val path = try { Uri.parse(url).path } catch (e: Exception) { null } ?: return null
        val last = path.substringAfterLast('/')
        if (!last.contains('.')) return null
        return last.substringAfterLast('.').lowercase()
    }

    private fun guessFromQuery(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.contains("mime=video%2fmp4") || lower.contains("mime=video/mp4") -> "video/mp4"
            lower.contains(".m3u8?") || lower.contains("format=m3u8") -> MIME_HLS
            else -> null
        }
    }
}
