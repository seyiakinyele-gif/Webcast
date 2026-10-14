package com.example.webcaster

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/** Full screen player on the phone itself, using Media3 (ExoPlayer). */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var resumePosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        resumePosition = savedInstanceState?.getLong("pos") ?: 0L
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        player?.let { resumePosition = it.currentPosition; it.release() }
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("pos", player?.currentPosition ?: resumePosition)
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val referer = intent.getStringExtra(EXTRA_REFERER).orEmpty()
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT)
        val mime = intent.getStringExtra(EXTRA_MIME)

        // Many sites refuse video requests that lack the page's Referer, so we send it.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply { if (userAgent != null) setUserAgent(userAgent) }
            .setDefaultRequestProperties(if (referer.isNotEmpty()) mapOf("Referer" to referer) else emptyMap())

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory))
            .build()

        val item = MediaItem.Builder().setUri(url).apply {
            when (mime) {
                MIME_HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
                MIME_DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()

        findViewById<PlayerView>(R.id.playerView).player = exo
        exo.setMediaItem(item)
        exo.seekTo(resumePosition)
        exo.playWhenReady = true
        exo.prepare()
        player = exo
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_USER_AGENT = "ua"
        const val EXTRA_MIME = "mime"
    }
}
