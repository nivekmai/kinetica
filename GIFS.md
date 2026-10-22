# Google GIF search (experimental v19)

Open the emoji picker and tap **GIFs**, or configure **GIFs** in any shortcut picker.
`:gifs` works in letter/punctuation/Enter alternate lists, like `:paste`.

1. Tap **Google GIF search**. This opens a visible Google Images browser with its
   animated-image filter. Enter a query and tap Search.
2. Tap a result, then hold its full-sized animated image. If the result is a static
   Google thumbnail, visit its source website and hold the original GIF instead.
3. After download and validation, tap **Use this GIF**. Return to your original
   message field if Android does not restore it automatically.
4. Tap the selected GIF in the keyboard to attach it. The messaging app still
   controls sending the message. If the keyboard did not reopen, tap the message
   field and reopen GIFs.

Accepted attachments appear in **Recents**, newest first. Tap one to attach it
again without another search. Clear removes the recent list and pending selection.
History contains up to 30 unique files, survives app restart, and is not included
in Android backup. Android may evict cached images when storage is low. Password
fields cannot open this picker; no-learning/incognito fields do not show or record
Recents. Selected private GIF bytes remain in the temporary cache long enough for
receiving apps to read them.

## What this version does and does not do

Search really uses Google's online image search; there is no GIPHY search provider
and no offline substitute. Google may index GIFs hosted on any source, including
GIPHY. We do not filter Google's ranking. Browsing requires internet; cached recent
attachments can work offline. Google/source websites receive the searches and web
requests made in this browser, and may display ads, consent pages, or CAPTCHA.
Cookies/site storage belong to this app's WebView, not the user's Chrome login.
Normal keyboard editor text, audio and voice transcripts are never search inputs.

This is a browser-based first implementation, **not Gboard's integrated results
panel**. Results animate on Google's/source pages; local previews are static first
frames. Some results are videos, WebP images, static thumbnails, or sites that block
direct downloads. These fail with an explicit message; the keyboard never inserts a
page URL or pretends that a thumbnail is an animated GIF. HTTPS GIF files up to
15 MiB, 2048 pixels per dimension and 1000 frames are accepted. No conversion is
performed. Google search markup is not scraped and no private Gboard keys are used.

The tradeoff is extra taps on first use. Google says its public Tenor API is no
longer available to new clients and its Custom Search JSON API is closed to new
customers. Gboard remains a Google-owned Tenor client. Decompiling Gboard would not
grant this fork API access, so no Gboard binary was needed for this implementation.

Sources checked during development:
- https://support.google.com/tenor/answer/10455265?hl=en
- https://developers.google.com/custom-search/v1/overview
- https://developer.android.com/develop/ui/views/touch-and-input/image-keyboard

## Attachment and lifetime rules

The browser cannot call into the IME through JavaScript. Choosing a file creates a
memory-only pending item tied to the originating package/field. Returning to that
field offers the file; it never sends automatically. An explicit tap requires the
same InputConnection that opened the panel, and rechecks MIME support and privacy.
A stale tap after an editor switch is rejected. FileProvider grants read access
only to the chosen cache content URI. Plain text fields report unsupported input;
apps that reject commitContent do not create a recent entry. An accepted handoff
means the app accepted the attachment, not that the user sent the message.

All downloads and thumbnail decoding run off the UI thread. Downloads have connection/read timeouts, a 60-second overall deadline (checked
between blocking reads), redirect and byte limits; HTTPS is checked again after each redirect. The parser
checks GIF structure, canvas size, frame count and an animation with at least two
frames. Cache storage is capped at 200 MiB. Files no longer in history are pruned
after 24 hours, preserving asynchronous access for receivers; a full protected
cache refuses new downloads instead of deleting files the recipient may still need.
File cache is excluded from Android backup; the history index is in noBackupFilesDir.

## Validation / phone checklist

Automated checks cover animation versus static thumbnail/HTML/WebP, malformed and
truncated blocks, dimensions/size/frame limits, HTTPS targets, query escaping,
MIME compatibility, history ordering/deduplication/cap and shortcut dispatch.
Build/test/lint results and artifact details are recorded after the gate completes.
No Android device or emulator is attached to this development environment.

Manual checks required on the Pixel:
- In Messages, open emoji → GIFs → Google GIF search, search a phrase, open a GIF
  result, hold the actual GIF, confirm, return and attach. Expect an animated
  attachment in the composer; sending remains the app's Send button.
- Repeat in another messaging app. Test a plain text field too: no false success
  or pasted URL when the editor does not support GIFs.
- Cancel browsing/downloads; rotate the browser; switch apps before returning.
  Expect no unsolicited attachment and no crash or stale download confirmation.
- Select a Google thumbnail or video: expect rejection, with guidance to open
  the original GIF. Test a disconnected network and a blocked source site.
- Attach A, B, A: Recents should show A then B once each. Restart the keyboard,
  go offline and reuse a recent GIF. Clear should empty the visible history.
- In a no-learning field, attach a GIF and return to a normal field: it should
  not appear in Recents. Password fields must not open the GIF panel.
- Configure `:gifs` on Enter and a letter alternate, then test both; also test the
  action-row/chord picker. ABC and Back should return from GIFs to the keys.
- Retest voice dictation and space-swipe navigation/select after opening GIFs.

### Online verification boundary

The public Google search endpoint responded, but a real Chromium browser run from
this development network was redirected to Google’s unusual-traffic CAPTCHA. We
did not solve or bypass that challenge. Search result selection remains unverified
until the phone test; network connectivity alone is not proof of that flow.

A separate HTTPS download of Wikimedia’s animated rotating-earth GIF returned
1,001,718 bytes. The actual compiled `GifPolicy.animatedGif` accepted that file.
This validates a real online GIF against the production parser, not the Android
WebView interaction or the destination app’s `commitContent` behavior.

### v19 artifact and automated gate

- Package: `com.kinetica.keyboard.dev`, version `2.0.18-voice-dev`, code 92.
- APK: `kinetica-dev-v19-google-gifs-arm64.apk`, 53,313,268 bytes.
- SHA-256: `bfdcf2ad96d876545bf672066221698c412963bff5e85665b099a97ec3d699b5`.
- Signing certificate matches v18; both voice backends remain packaged.
- `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`:
  passed. Debug and release each ran 1,203 tests, zero failures/errors, two existing
  skips. Lint has zero errors. The eight GIF tests cover the policies listed above,
  including stale editor identity. No Android UI/device test was run.
