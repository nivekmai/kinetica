# Google GIF search (experimental v25)

Open the emoji picker and tap **GIFs**, or configure **GIFs** in any shortcut picker.
`:gifs` works in letter/punctuation/Enter alternate lists, like `:paste`.

1. Tap the **magnifying glass** on the right. A bottom sheet opens at 150% of the keyboard’s height with
   Google Images and its animated-image filter. The query field is focused and the
   typing keyboard opens below the sheet. Enter a query and tap the magnifying glass; the keys
   hide, leaving the search sheet at the bottom of the screen.
2. Tap a result, then hold its full-sized animated image. If the result is a static
   Google thumbnail, visit its source website and hold the original GIF instead.
3. After download and validation, tap **Use this GIF**. The browser closes and the
   GIF attaches directly when the original message field regains focus. There is
   no second tap in Recents. The receiving app controls sending the message.
4. If the field changed, the input did not become ready within 2.5 seconds, or the app refused
   the attachment, a retry message appears. Open GIFs in the original field to
   retry the selected GIF manually.

The sheet keeps Kinetica’s query field and attempts to hide Google’s duplicate search
bar and header controls. A remaining logo is acceptable; phone layouts that the
filter does not recognize can be diagnosed through the report below. The native toolbar contains
**Search**, **?** (instructions) and **X** (close). Instructions no longer occupy a
permanent row; progress/errors appear when needed and can be tapped for details.
The sheet is clamped to available screen space while typing or in a small window.
The picker’s **ABC** button is on the left and **Search** on the right, with wider
rounded backgrounds and padding around the toolbar.

Text keys are the default when opening a field, including fields that accept GIFs.
Choosing **Use this GIF** authorizes one attachment on return to the originating
field. An editor restart or hide/show cannot attach twice or reopen Recents. An
unsent selection remains available at the front of the manually opened picker.

Accepted attachments appear in **Recents**, newest first. Tap one to attach it
again without another search. Tiles show only the image, without repeated attachment
labels. Long-press a tile, then confirm **Remove** to remove just that GIF. Cancel
leaves it unchanged; releasing the long press does not attach it. There is no
clear-all control. Removing a selected, unsent GIF also dismisses that selection.
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
performed. No result URLs are scraped and no private Gboard keys are used. A local
presentation script hides Google's header controls on `https://[www.]google.com/search`
only. It observes replaced headers, preserves result/preview controls, and leaves
source, account, consent and CAPTCHA pages usable. There is no JavaScript bridge.
Google can change its markup; an unrecognized header may remain visible rather
than risking hiding results or a challenge.

The tradeoff is extra taps on first use. Google says its public Tenor API is no
longer available to new clients and its Custom Search JSON API is closed to new
customers. Gboard remains a Google-owned Tenor client. Decompiling Gboard would not
grant this fork API access, so no Gboard binary was needed for this implementation.

Sources checked during development:
- https://support.google.com/tenor/answer/10455265?hl=en
- https://developers.google.com/custom-search/v1/overview
- https://developer.android.com/develop/ui/views/touch-and-input/image-keyboard

## Attachment and lifetime rules

The browser cannot call into the IME through JavaScript. Starting search creates
a memory-only request token bound to the originating package, field ID/name,
input type, selection and up to 128 characters before/after the cursor. That small
context stays in memory; it never goes to WebView, URLs, disk or logs. **Use this
GIF** confirms the request once and directly wakes the IME's return check, including
when the old input session resumes without a new callback. Readiness is checked
every 100 ms for up to 2.5 seconds: browser focus, no connection, or temporarily
unreadable context leave the request pending. On a matching readable destination,
the IME consumes the request before attempting attachment. A different destination,
changed context, or readiness timeout requires a manual retry. Android 12+ uses
the editor's atomic surrounding-text/selection snapshot when available, falling
back to the existing context reads on editors that do not implement it.
The editor may reuse IDs across empty conversations; this snapshot is an extra
check, not a universal conversation identifier supplied by Android.

The handoff checks the current InputConnection, MIME support and privacy. Repeated
start-input callbacks cannot replay it, and failure does not rearm it. Manual
picker taps still require the same InputConnection that opened that panel.
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

If either the duplicate Google query bar or attachment handoff still fails,
open **GIFs → Search → ? → Copy diagnostics** and paste the report into the bug
report. It includes bounded in-memory attachment decisions (waiting, destination
comparison booleans, MIME/connection guards, commit accepted/rejected) and the
current Google page's structural tags, roles, geometry and hide state. It does
not collect message text, query values, page text, HTML, image URLs or account
details. It is copied only on request; nothing is automatically uploaded.

Manual checks required on the Pixel:
- In Messages, open emoji → GIFs → Google GIF search, search a phrase, open a GIF
  result, hold the actual GIF, confirm and return. Expect an animated
  attachment in the composer; sending remains the app's Send button.
- Repeat in another messaging app. Test a plain text field too: no false success
  or pasted URL when the editor does not support GIFs.
- Cancel browsing/downloads; rotate the browser; switch apps before returning.
  Expect no unsolicited attachment and no crash or stale download confirmation.
- Select a Google thumbnail or video: expect rejection, with guidance to open
  the original GIF. Test a disconnected network and a blocked source site.
- Attach A, B, A: Recents should show A then B once each. Restart the keyboard,
  go offline and reuse a recent GIF. Long-press A → Cancel should retain A/B;
  long-press A → Remove should leave only B, including after restart. Neither
  long-press nor confirmation should attach anything. The cached file remains
  available to receivers that already have its URI.
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

### v20 UX changes and additional phone checks

- Removed the clear-all control and the repeated attachment captions beneath tiles.
- Search entry and query submission both use a magnifying-glass icon with an
  accessible Search label. Recents uses RecyclerView/StaggeredGridLayoutManager
  with three columns and each GIF’s canvas aspect ratio. Extremely wide/tall
  media keeps a bounded, tappable card while the preview fits without cropping.
  Metadata is read before displaying cards; bitmap loading cannot shift tile
  positions. Recycled cells discard old decode callbacks.
- Test mixed portrait, square and landscape GIFs: expect staggered heights,
  intact previews and no repeated captions. Scroll away and back, then remove
  a GIF; expect the clicked item to match the displayed preview.
- Long press opens Remove/Cancel confirmation within the picker. Removal is per
  GIF and persists via an atomic history-index replacement. Unconfirmed presses
  never modify the index, and confirmed removal does not delete granted media.
  A tile can still be removed from history in a field that cannot accept GIFs.
- Search opens as a translucent bottom sheet with the message visible behind it.
  Initial height comes from the actual keyboard; live keyboard insets update it
  after resizing/folding/rotation. The sheet is clamped to the usable window.
  Query focus and keyboard display happen after window focus is obtained.
- Submit a query: expect the typing keys to hide and the sheet to move down.
  Return from a GIF preview: the query keyboard should not reopen on its own.
  Tap the query field to edit again: expect the sheet above the keys. Tap the
  space above the sheet or Cancel to return to the message.
- Test folded/unfolded, portrait/landscape and increased text size: the query row
  should remain reachable and the sheet must not cover system bars or the IME.
  The instruction/error row uses at most two lines; tap it for the complete text.

The previous user report confirms v19's basic flow worked on their device.
The v20 focus, insets and long-press interaction still need the phone checks above;
JVM tests cover persistence, retained media and sizing arithmetic, not Android UI.

### v20 artifact and automated gate

- Package: `com.kinetica.keyboard.dev`, version `2.0.19-voice-dev`, code 93.
- APK: `kinetica-dev-v20-gif-ux-arm64.apk`, 53,296,359 bytes.
- SHA-256: `9f0809bdde94596d3bcf3eeeacd41c5a63e0c2b8faf0dbbab93d7eb6b66a6e7a`.
- Signing certificate matches v19; both voice backends remain packaged.
- `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`: passed.
  Debug and release each ran 1,208 tests, zero failures/errors and two existing skips.
  Lint has zero errors. New tests cover per-GIF removal persistence, retained
  recipient media, removal of a pending-only selection, sheet height bounds,
  GIF header dimensions and staggered card proportions/limits. Android focus,
  layout, recycled preview correctness and touch checks remain manual.

### v21 search chrome and phone checks

- Open GIFs: **ABC** is left, **Search** is right, both 64 × 48 dp with 12 dp
  corners and 8 dp outer padding. Tap ABC to return to letters; reopen GIFs and
  tap Search. Query focus still opens the keyboard.
- Submit a query: the sheet should be 1.5× the keyboard height, including its
  toolbar. When typing, it stays above the IME and clamps to the available space.
  Repeat folded/unfolded, rotated and with larger text.
- On Google results, expect no duplicate search field, Google logo, hamburger,
  sign-in button or Images/Videos tabs. Scroll, load more, open/close a result and
  navigate Back: late/replaced header controls should also disappear. Result
  images, preview close controls and Visit-source links must remain usable.
- Open a source website: its UI must remain intact. Any Google consent/sign-in or
  unusual-traffic challenge must remain usable; do not bypass it.
- The idle sheet has no instruction row. **?** opens the full instructions in a
  dismissible dialog. Search and **X** remain on the same toolbar; X returns to
  the message. A download still shows progress, and a failed download/page load
  still shows a tappable error with its complete text.

`tools/test_gif_search_chrome.cjs` exercises the production script in Chromium
against local representative DOM fixtures, including replacement headers, preview
controls, host/path restrictions and late CAPTCHA insertion. It requires
`playwright-core` plus Chromium (optionally supplied through `CHROMIUM_PATH`);
all fixture requests are fulfilled locally. These are not snapshots of Google's
current mobile page. Phone verification of Google's actual layout is still needed.

### v21 artifact and automated gate

- Package: `com.kinetica.keyboard.dev`, version `2.0.20-voice-dev`, code 94.
- APK: `kinetica-dev-v21-gif-search-arm64.apk`, 53,299,812 bytes.
- SHA-256: `74305769eb9552eb8d5dd97ee82b9b97abbe316ee164c661050d8770af15862d`.
- Signing certificate matches v20; the native LLM and unchanged punctuation model
  remain packaged. The packaged page-presentation script matches the tested source.
- `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`:
  passed in 3m 34s. Debug and release each ran 1,209 tests with zero failures/errors
  and two existing skips. Lint has zero errors. Regression checks cover 150% sheet
  sizing/clamping/overflow and exact search-page URL scoping.
- Chromium: 37 local DOM checks passed. No Android UI/device test was run; the
  phone checklist above remains required for Google's live mobile layout.

### v22 default-keyboard regression

Historical v22 behavior; v23 replaces its automatic picker offer with attachment.

Previously, every `onStartInputView` treated an unsent browser selection as a new
request to show GIFs. Returning to text did not clear that trigger. The selection
now has a separate one-time return offer, consumed before showing the picker;
retaining the file for manual attachment cannot rearm it. Manual entry also
acknowledges the offer. Input-view starts restore text even when Android reuses
the existing input session without another `onStartInput` callback. A missing file,
private editor, or unsupported MIME type cannot trigger an automatic panel.

Phone checks:
- Open a GIF-capable message field with no new selection: expect text keys.
- Choose a GIF in search, return to the message: the picker appears once. Tap ABC,
  hide/reopen the keyboard, refocus the field and change conversations: expect text.
- Open GIFs manually: the unsent selected GIF is still first and can be attached.
- Open GIFs manually, then hide/reopen the keyboard: expect text keys again.
- Choose another GIF (including the same file): it may offer the picker once again.
- Search and cancel without choosing: returning to the message must show text.

JVM regressions cover consuming an offer once, capability alone not opening GIFs,
unrelated editors preserving the intended return, another explicit selection,
manual acknowledgement, and clearing a selection. Android lifecycle/visibility
checks still require the phone; no Android UI test was run.

### v22 artifact and automated gate

- Package: `com.kinetica.keyboard.dev`, version `2.0.21-voice-dev`, code 95.
- APK: `kinetica-dev-v22-gif-default-arm64.apk`, 53,299,808 bytes.
- SHA-256: `44b43075bc9ccffc7224cf9c3eb6eb9baac997e1a05cd5cd1f845c8738c11d3d`.
- Signing certificate matches v21; native LLM and unchanged punctuation model
  remain packaged. This build includes all v21 search-page changes.
- `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`:
  passed in 2m 16s. Debug and release each ran 1,215 tests, zero failures/errors,
  two existing skips. Lint has zero errors. The six new return-offer regressions
  passed in both variants; the Android phone checks above remain manual.

### v23 screenshot fixes and phone checks

- The GIF panel takes the suggestion row's height as content. The suggestion row
  is GONE rather than INVISIBLE; overall IME height stays unchanged and the unused
  strip above ABC/Search disappears. Navigation keeps its existing sizing.
- Header filtering no longer treats an enclosing `main` or `role=main` as proof
  that every descendant is a result. It recognizes mobile search-type navigation,
  SVG logos and query controls, preserves actual result/preview regions and
  pagination, and uses reversible CSSOM declarations instead of an inline style
  element that a page CSP can reject. A challenge restores the original styles.
- GIF confirmation and help use AppCompat dialogs. Action buttons use readable
  theme primary text instead of the dark accent, in both light and dark modes.
- **Use this GIF** confirms one direct attachment on returning to the original
  editor; the picker no longer opens as an intermediate confirmation screen.

Phone checks, based on the three screenshots:
1. Open Recents in the same message field: ABC/Search should sit at the top with
   only their intentional padding; the editor must not jump when changing modes.
2. Open search in portrait/folded and unfolded layouts. Google logo, query input
   and type tabs should disappear while results, image preview controls and
   pagination remain usable. Open a source site: its header should remain intact.
3. Hold a GIF and inspect the confirmation in dark and light modes. Cancel and
   Use this GIF must have clearly readable text. Check the ? help dialog too.
4. Tap Use this GIF: expect one attachment in the original composer and text keys,
   with no second Recents tap. Hide/show, switch fields and trigger an editor
   restart: no duplicate attachment or automatic picker reopening.
5. Repeat in another GIF-capable app. Cancel a preview/search: no attachment.
   Change the target text/field, return after 30 seconds, or use an app that
   rejects content: expect a retry notice, no automatic retry, and a manually
   available pending GIF in the original field.

A fresh live mobile-browser check from this development network again received
Google's unusual-traffic challenge, so the exact mobile DOM in the screenshots
was not retrievable. Local regressions reproduce the enclosing-main and strict-CSP
failure cases; Google's live layout, dialog contrast and Android handoff still
require the phone checks above. No Android device or emulator test was run.

### v23 artifact and automated gate

- Package: `com.kinetica.keyboard.dev`, version `2.0.22-voice-dev`, code 96.
- APK: `kinetica-dev-v23-gif-fixes-arm64.apk`, 53,301,512 bytes.
- SHA-256: `5da6b0dbe5c32b9d08e579c2c03181486787a3f59a621f7a705277c85f2c0907`.
- Signing certificate matches v22. Native LLM and unchanged punctuation model
  remain packaged; the packaged header script matches the browser-tested source.
- Final `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`:
  passed in 2m 1s. Debug/release each ran 1,220 tests, zero failures/errors, two
  existing skips. Lint has zero errors. Tests include panel footprint arithmetic,
  confirmation-token validity, direct one-time handoff, duplicate callbacks,
  changed/unreadable editors, expiry, cancellation, and retained manual retries.
- Browser: all 46 local DOM checks passed. The v22 script fails the added mobile
  main-wrapper regression. This does not replace the live Pixel checks above.

### v24 query-bar container fix

The v23 phone screenshot still shows an empty rounded Google query bar with its
magnifier and clear icon. Hiding the query field or form alone can leave those
outer wrappers visible. The presentation script now follows each query field to
its outermost query-only container and hides that container as a whole, including
wrappers outside the form. It uses DOM structure rather than generated class
names or dimensions that disappear once an element is hidden. It stops at page
regions, results, preview dialogs, links, media and unrelated text. A results
region can enclose the query bar without protecting the bar itself.

This update focuses on the query bar; a remaining Google logo is acceptable.
Native search controls, result cards, attachment behavior and keyboard sizing
are unchanged. Late wrappers are handled and original styles are restored if a
container acquires result content or a security challenge appears.

Browser regressions: 61 offline DOM checks pass. The saved v23 script fails the
new rounded-container fixture. A live mobile Chromium request from this network
still redirects to Google's traffic challenge, so these fixtures do not prove
that the exact DOM on the phone is covered.

Phone checks:
1. Open GIF search: the native query input remains usable; Google's rounded query
   bar, magnifier and clear icon should disappear together with no blank strip.
   The logo may remain. Results must still be visible and scrollable.
2. Search again using the native input, then go Back: a replacement bar should
   also disappear. Check folded/unfolded layouts.
3. Open a GIF preview and source page: their controls must remain usable. Confirm
   Use this GIF still attaches directly to the original composer.

v24 artifact and gate:
- Package `com.kinetica.keyboard.dev`, version `2.0.23-voice-dev`, code 97.
- `kinetica-dev-v24-gif-search-bar-arm64.apk`, 53,302,156 bytes.
- SHA-256: `f15baa479ecc84fbe8955cf2de0226a115db70b909a42d5c12587e516dd23d5c`.
- `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`
  passed in 52 seconds. Debug/release each report 1,220 tests, zero failures/errors,
  two existing skips. Lint reports zero errors. The packaged query-bar script
  matches the browser-tested source; native LLM and unchanged punctuation model
  remain included. No Android device/emulator test was run.

### v25 return readiness and query-bar diagnostics

Two code paths could lose a confirmed attachment: consuming it during a temporary
unreadable input connection, and relying entirely on start-input callbacks after
closing search. Confirmation now triggers a bounded readiness check directly.
The browser no longer queues a separate hide-IME request while closing, which
could race the destination's restored keyboard. The commit operation is never
retried automatically; pending manual retry and all destination checks remain.

The query filter now recognizes compact rounded bars containing non-button text
or icon spans, and semantic combobox/searchbox controls without `name=q`. This
covers additional representative cases but is not verification of the phone's
Google DOM. The new copyable report exposes whether the script was installed,
whether a challenge suppressed filtering, what query controls were found, which
ancestors were hidden, and the surviving compact rows. Existing security/source
page guards and result/preview protection remain.

Phone checks:
1. Search, select a GIF, confirm Use this GIF: expect one attachment after returning
   to the original message, both with keys visible and hidden before confirmation.
2. Repeat after tapping a native search query, after a Google image preview, and
   after a source-page download. No extra Recents tap should be needed.
3. Switch to a different input/conversation or modify the destination: no stale
   attachment. Input restart/hide/show must not attach a second time. A rejected
   commit must leave the GIF available for manual retry.
4. If the bar survives or attachment fails, copy diagnostics using the steps above.
   Verify the report has no message/query text and include it with the screenshot.

v25 artifact and gate:
- Package `com.kinetica.keyboard.dev`, version `2.0.24-voice-dev`, code 98.
- `kinetica-dev-v25-gif-return-diagnostics-arm64.apk`, 53,320,464 bytes.
- SHA-256: `50cc27dad6252f28f17d4687bd5233e8ab020a8420e6c6368392e8e883d2a4bd`.
- Final `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`
  passed in 1m 31s. Debug/release each report 1,225 tests, zero failures/errors,
  two existing skips. Lint reports zero errors. The five new regressions cover
  readiness recovery, notification without a lifecycle callback, bounded waits,
  different unreadable destinations, and exclusion of editor text from diagnostics.
- All 73 offline DOM checks pass; v24 fails the added plain-text rounded-bar case.
  Packaged script matches the tested source. Signature matches v24; native LLM and
  unchanged punctuation model remain included. No Android device/emulator test
  was run; the phone's exact Google layout and return lifecycle remain unverified.
