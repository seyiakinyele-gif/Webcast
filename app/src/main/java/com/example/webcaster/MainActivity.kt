package com.example.webcaster

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var fab: ExtendedFloatingActionButton

    private var castContext: CastContext? = null

    // Everything the sniffer has found on the current page.
    private val videos = LinkedHashMap<String, DetectedVideo>()
    private val subtitles = LinkedHashSet<String>()

    @Volatile private var currentPageUrl = ""
    @Volatile private var currentPageTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progress = findViewById(R.id.progress)
        fab = findViewById(R.id.fabVideos)

        // Throws on phones without Google Play services; the app still works for local playback.
        AdBlocker.init(this)
        AdBlocker.enabled = getPreferences(MODE_PRIVATE).getBoolean(PREF_ADBLOCK, true)

        castContext = try { CastContext.getSharedInstance(this) } catch (e: Exception) { null }

        setUpWebView()
        setUpUrlBar()
        fab.setOnClickListener { showVideoList() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        val shared = sharedUrlFrom(intent)
        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else loadInput(shared ?: HOME_PAGE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUrlFrom(intent)?.let { loadInput(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        if (castContext != null) {
            CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.media_route_menu_item)
        } else {
            menu.findItem(R.id.media_route_menu_item).isVisible = false
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_adblock)?.apply {
            isChecked = AdBlocker.enabled
            title = if (AdBlocker.enabled) "Ad blocker (${AdBlocker.blockedCount} blocked)" else "Ad blocker (off)"
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reload -> { webView.reload(); true }
        R.id.action_home -> { loadInput(HOME_PAGE); true }
        R.id.action_adblock -> {
            AdBlocker.enabled = !AdBlocker.enabled
            getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_ADBLOCK, AdBlocker.enabled).apply()
            Toast.makeText(this, if (AdBlocker.enabled) "Ad blocker on" else "Ad blocker off", Toast.LENGTH_SHORT).show()
            webView.reload()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------- browser

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false) // blocks most pop up adverts
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.addJavascriptInterface(SnifferBridge(), "VibeSniffer")

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentPageUrl = url
                urlBar.setText(url)
                clearDetected()
                AdBlocker.resetCount()
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentPageTitle = view.title.orEmpty()
                view.evaluateJavascript(PAGE_SCANNER_JS, null)
                if (AdBlocker.enabled) view.evaluateJavascript(AdBlocker.COSMETIC_JS, null)
            }

            // Only allow normal web links. This stops ad redirects into the Play Store and so on.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            // Runs on a background thread for EVERY request the page makes, including inside iframes.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                // Ad blocker runs first: adverts are answered with an empty reply and never load.
                if (AdBlocker.isBlocked(url)) return AdBlocker.emptyResponse()
                when {
                    VideoDetector.isSubtitle(url) -> runOnUiThread { subtitles.add(url) }
                    else -> VideoDetector.videoMimeFor(url)?.let { mime -> onVideoFound(url, mime, "network") }
                }
                return null // we only watch traffic; the page still loads normally
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                currentPageTitle = title.orEmpty()
            }
        }
    }

    private fun setUpUrlBar() {
        urlBar.setOnEditorActionListener { v, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || isEnter) {
                loadInput(v.text.toString())
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                webView.requestFocus()
                true
            } else false
        }
    }

    /** Turns whatever was typed into a URL, or a web search if it does not look like one. */
    private fun loadInput(raw: String) {
        val text = raw.trim()
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            text.contains('.') && !text.contains(' ') -> "https://$text"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(text)
        }
        webView.loadUrl(url)
    }

    private fun sharedUrlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://\\S+").find(text)?.value
    }

    // ---------------------------------------------------------------- sniffer

    /** Called from JavaScript injected into the page (see PAGE_SCANNER_JS). */
    inner class SnifferBridge {
        @JavascriptInterface
        fun onVideo(src: String) {
            if (!src.startsWith("http")) return // blob: URLs are handled by the network sniffer
            // A <video> tag with an unknown extension is most often plain MP4.
            val mime = VideoDetector.videoMimeFor(src) ?: "video/mp4"
            onVideoFound(src, mime, "page")
        }

        @JavascriptInterface
        fun onSubtitle(src: String) {
            if (src.startsWith("http")) runOnUiThread { subtitles.add(src) }
        }
    }

    private fun onVideoFound(url: String, mime: String, source: String) {
        runOnUiThread {
            if (videos.containsKey(url) || videos.size >= MAX_VIDEOS) return@runOnUiThread
            videos[url] = DetectedVideo(url, currentPageUrl, currentPageTitle, mime, source)
            fab.text = videos.size.toString()
            if (fab.visibility != View.VISIBLE) fab.show()
        }
    }

    private fun clearDetected() {
        videos.clear()
        subtitles.clear()
        fab.hide()
    }

    // ---------------------------------------------------------------- actions

    private fun showVideoList() {
        val list = videos.values.toList().reversed() // newest first; usually the one you just pressed play on
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_videos, Toast.LENGTH_LONG).show()
            return
        }
        val labels = list.map { "${it.label}\n${shorten(it.url)}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.videos_found, list.size))
            .setItems(labels) { _, which -> showVideoActions(list[which]) }
            .show()
    }

    private fun showVideoActions(video: DetectedVideo) {
        val actions = arrayOf(
            getString(R.string.cast_to_tv),
            getString(R.string.play_on_phone),
            getString(R.string.copy_link)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(video.label)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> castVideo(video)
                    1 -> playLocally(video)
                    2 -> copyLink(video.url)
                }
            }
            .show()
    }

    private fun playLocally(video: DetectedVideo) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, video.url)
            putExtra(PlayerActivity.EXTRA_REFERER, video.pageUrl)
            putExtra(PlayerActivity.EXTRA_USER_AGENT, webView.settings.userAgentString)
            putExtra(PlayerActivity.EXTRA_MIME, video.mimeType)
        })
    }

    /**
     * Sends the video URL to the TV. The TV then downloads the video itself,
     * straight from the website, exactly like Web Video Caster does.
     * The phone is just a remote control from this point on.
     */
    private fun castVideo(video: DetectedVideo) {
        val session = castContext?.sessionManager?.currentCastSession
        val client = session?.remoteMediaClient
        if (session == null || !session.isConnected || client == null) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_LONG).show()
            return
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, video.pageTitle.ifBlank { "Web video" })
            putString(MediaMetadata.KEY_SUBTITLE, Uri.parse(video.pageUrl).host.orEmpty())
        }

        // Attach the first WebVTT subtitle file found on the page, if any.
        val vtt = subtitles.firstOrNull { it.lowercase().substringBefore('?').endsWith(".vtt") }
        val tracks = vtt?.let {
            listOf(
                MediaTrack.Builder(1L, MediaTrack.TYPE_TEXT)
                    .setContentId(it)
                    .setContentType("text/vtt")
                    .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                    .setName("Subtitles")
                    .setLanguage("en")
                    .build()
            )
        }

        val mediaInfo = MediaInfo.Builder(video.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(video.mimeType)
            .setMetadata(metadata)
            .apply { if (tracks != null) setMediaTracks(tracks) }
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .apply { if (tracks != null) setActiveTrackIds(longArrayOf(1L)) }
            .build()

        client.load(request).setResultCallback { result ->
            if (!result.status.isSuccess) {
                Toast.makeText(
                    this,
                    "The TV could not play this video (code ${result.status.statusCode}). " +
                        "Some sites block playback outside their own page.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        Toast.makeText(this, "Sending to TV...", Toast.LENGTH_SHORT).show()
    }

    private fun copyLink(url: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Video link", url))
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }

    private fun shorten(url: String): String = if (url.length > 70) url.take(67) + "..." else url

    companion object {
        private const val HOME_PAGE = "https://duckduckgo.com"
        private const val MAX_VIDEOS = 40
        private const val PREF_ADBLOCK = "adblock_enabled"

        /**
         * Injected after each page loads. Finds <video>, <source> and <track> tags,
         * and listens for any video starting to play, including ones added later.
         */
        private val PAGE_SCANNER_JS = """
            (function () {
              if (window.__vibeSniffer) return;
              window.__vibeSniffer = true;
              function report(v) {
                try {
                  var s = v.currentSrc || v.src;
                  if (s) VibeSniffer.onVideo(s);
                  v.querySelectorAll('source').forEach(function (e) { if (e.src) VibeSniffer.onVideo(e.src); });
                  v.querySelectorAll('track').forEach(function (t) { if (t.src) VibeSniffer.onSubtitle(t.src); });
                } catch (e) {}
              }
              function scan() { document.querySelectorAll('video').forEach(report); }
              scan();
              document.addEventListener('play', function (e) {
                if (e.target && e.target.tagName === 'VIDEO') report(e.target);
              }, true);
              var pending = null;
              new MutationObserver(function () {
                if (pending) return;
                pending = setTimeout(function () { pending = null; scan(); }, 1500);
              }).observe(document.documentElement, { childList: true, subtree: true });
            })();
        """.trimIndent()
    }
}
