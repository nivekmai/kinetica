# Typing statistics (v27)

Open **Settings → Typing → Typing statistics** (or search Settings for WPM/history).
Choose 7 days, 30 days or all time. The overview shows average WPM, best sustained
WPM, active typing time and standard-word count. Bars compare average speed across
four six-hour time-of-day groups and up to 14 recent active days; each row also
shows its active duration so a brief sample is distinguishable from a busy day.

Collection starts with this version and defaults to enabled, as requested. The
existing live spacebar display keeps its own switch. Turn off **Collect typing
history** to pause collection without deleting history; **Clear typing history**
asks for confirmation before deleting all aggregates. Old instantaneous WPM
readings were never saved and cannot be reconstructed.

## Measurement

- Normal typing uses the existing finalized-word path and requires real tap/swipe
  touches. It counts Unicode code points plus one conventional separator per word.
  Literal/peck mode counts each typed letter directly. Pasted text, voice output,
  shortcuts and untouched predicted words do not generate samples.
- One standard word is five characters. Average WPM is `characters × 12000 /
  active milliseconds`, computed from summed counts and durations, not an average
  of per-word speeds. It is gross typing throughput, not accuracy or net document
  growth: rewriting a word counts as more typing. Unfinalized/discarded words do
  not contribute; numeric/symbol actions outside the word-entry path are excluded.
- Active time uses monotonic gesture timestamps. Touch spans and inter-touch gaps
  up to four seconds count. Longer gaps, including within a word, are excluded.
  Overlapping thumbs never double-count elapsed time. Hiding the keyboard,
  changing input sessions or changing collection state resets the timing window.
- Best sustained speed uses a recent roughly 15-active-second window, requiring
  at least 10 active seconds and 25 characters. Pauses reset that window. A word
  that spans a long pause contributes to the average, but cannot establish a peak.
  A dash means no qualifying sustained sample yet; it is not a zero-speed reading.
- Day/hour buckets use local time at word finalization. A word spanning midnight
  belongs to its completion day. DST's repeated hour combines into one bucket;
  changing timezone affects future samples without relabeling previous history.
  Wall-clock changes affect bucket labels, never the speed calculation.

## Storage and scope

Only `(local date, hour, character count, active milliseconds, peak WPM)` are saved.
There are no typed words, key identities, precise touch timestamps or app names in
the stats database. Password and no-personalized-learning fields are excluded.
All reads/writes/deletions are serialized on a dedicated worker; word writes are
transactional, and reading the page waits behind earlier writes. SQLite is kept
under `noBackupFilesDir`, outside Android backup and this fork's settings exports.
History remains until explicitly cleared or app data is removed. No network calls
are made for statistics.

## Validation and phone checks

JVM regressions cover weighted averages, inter-word and intra-word pauses, overlap,
input resets, missing/consumed touch data, sustained peak thresholds/windows and
invalid timestamps. The UI and Android database/lifecycle still require phone checks:

1. Install the update and find the page via both Settings → Typing and Settings
   search for “WPM”. Empty history should explain that collection starts now.
2. With the live WPM display OFF, type several sentences and finish each word with
   a space/punctuation. The page should show counts, active time and a nonzero
   average. Keep typing without long pauses for a qualifying sustained peak.
3. Pause for 10 seconds with the keyboard open, including halfway through a word;
   finish typing. Idle time must not be added. Hide/reopen and repeat.
4. Switch to literal mode and type letters; verify that counts increase. Paste a
   paragraph, dictate text or tap an untouched prediction: no added typing volume.
5. Type in password and no-learning fields: history must not change. Disable
   collection and type normally: history stays unchanged; re-enable to resume.
6. Restart the app/device: persisted totals remain. Compare 7/30/all-time ranges
   and verify daily/time-of-day averages use their displayed active durations.
7. Clear history, cancel once (unchanged), then confirm (empty). New typing should
   start new totals, with no pre-clear writes returning later.
8. Check folded/unfolded layouts, dark/light mode and larger font sizes. The page
   should scroll; average values remain readable and the range controls reachable.

## v27 artifact and gate

- Package `com.kinetica.keyboard.dev`, version `2.0.26-voice-dev`, code 100.
- `kinetica-dev-v27-typing-statistics-arm64.apk`, 53,335,964 bytes.
- SHA-256: `372bfd6b0ef9e8b553f710a427fbba9a50e1a835cdb4e853d96df6f14e0bd09b`.
- Final `-PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4`
  passed in 1m 50s. Debug/release each report 1,241 tests, zero failures/errors,
  two existing skips. All ten new history regressions pass; lint has zero errors.
- A host SQLite check executed the production CREATE/INSERT/UPDATE/SELECT SQL:
  counts/time add, peak takes the maximum, date bounds filter, reopening preserves
  data, and deletion remains empty after reopening. Android UI/database lifecycle
  checks remain manual; no device or emulator test was run.
- Signature matches v26. Statistics Activity, native LLM and unchanged punctuation
  model are packaged; previous GIF/voice/navigation features remain included.
