# Vibe Video Cast (starter project)

A small Android app that works like Web Video Caster: browse any website, it spots the
video files the page loads, and you either play them full screen on the phone or send
them to a Chromecast or Google TV.

## What is in here

| File | What it does |
|---|---|
| `MainActivity.kt` | Built in browser, address bar, the "videos found" button, casting |
| `VideoDetector.kt` | The sniffer rules: which URLs count as video (MP4, HLS m3u8, DASH mpd, WebM) or subtitles (VTT, SRT) |
| `PlayerActivity.kt` | Full screen phone player using Media3 (ExoPlayer), sends the page's Referer so sites accept it |
| `AdBlocker.kt` | Blocks requests to known ad, pop up and tracker servers, and hides empty ad boxes. Switch it on or off in the menu |
| `CastOptionsProvider.kt` | Google Cast set up, using Google's free Default Media Receiver |
| `res/menu/main_menu.xml` | The standard cast button |

## Get the APK without installing anything (GitHub builds it for you)

1. Create a free account at github.com, then click **New repository**, give it a name, and create it.
2. On the new repository page click **uploading an existing file**, drag in everything from inside
   this `webcaster` folder (including the hidden `.github` folder), and click **Commit changes**.
   Tip: on Windows and Mac, hidden folders may not show. If `.github` is missing, create the file
   by hand: **Add file > Create new file**, name it `.github/workflows/build-apk.yml` and paste in
   the contents of that file.
3. Open the **Actions** tab. A job called **Build APK** starts by itself (about 5 to 10 minutes).
4. When it shows a green tick, click it and download **VibeVideoCast-apk** under **Artifacts**.
   It is a zip containing `app-debug.apk`.
5. Copy the APK to your phone, open it, and allow **Install unknown apps** when Android asks.

If the job shows a red cross, open it, copy the error text and send it to Claude to fix.

## How to build and install with Android Studio

1. Install **Android Studio** (free) on a Windows, Mac or Linux computer.
2. Unzip this folder and choose **File > Open**, then pick the `webcaster` folder.
3. Let Android Studio sync. It downloads Gradle and the libraries the first time (a few minutes).
   If it offers to upgrade the Android Gradle Plugin or library versions, accepting is normally fine.
4. On your phone: **Settings > About phone**, tap **Build number** seven times, then turn on
   **USB debugging** under **Developer options**.
5. Plug the phone in, pick it in the device list at the top of Android Studio and press **Run**.
   To get an APK file instead: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.

## How to use it

1. Make sure the phone and the Chromecast or Google TV are on the same Wi-Fi.
2. Open a page with a video and press play on the page.
3. A round button with a number appears bottom right. Tap it to see the videos found.
4. Tap the cast icon at the top to connect to your TV, then choose **Cast to TV**.

## Ad blocker

* On by default. Menu (three dots) > **Ad blocker** turns it on or off and shows how many requests were blocked on the page.
* It blocks around 70 common ad, pop up and tracking networks out of the box.
* For much stronger blocking, download a hosts list such as the StevenBlack unified hosts file,
  save it as `app/src/main/assets/adblock_hosts.txt` and rebuild. It loads automatically.
* If a site's video stops working, turn the blocker off for a moment. Some players wait for an advert to load first.

## Known limits (same as the real app has)

* DRM protected services (Netflix, Disney+, Prime Video and similar) cannot be cast this way, by design.
* Videos that only exist as `blob:` URLs are caught through their network playlist instead; if a
  site hides that too, it will not show up.
* The Default Media Receiver on the TV cannot send custom headers, so sites that insist on a
  Referer or cookies may play on the phone but fail on the TV. The fix the big apps use is a small
  proxy server inside the phone app (see roadmap).
* Only Google Cast is built in. Roku, Fire TV, DLNA and AirPlay each need their own protocol.

## Roadmap ideas

1. Local proxy (for example with NanoHTTPD) so the TV fetches through the phone, adding headers and cookies.
2. DLNA / UPnP casting for Samsung, LG and Sony TVs.
3. Roku support via its External Control Protocol.
4. Bookmarks, history and a playback queue (Room database).
5. Casting local files from the phone's gallery (needs the local proxy too).

Only use it for content you have the right to watch.
