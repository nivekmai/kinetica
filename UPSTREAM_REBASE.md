# Fork rebased onto Kinetica 3.0.1 (v28)

Upstream base: `71a6a9c` / tag `v3.0.1`, the latest 3.0 patch release fetched
on 2026-11-12. This includes the 3.0.1 fix for leaving the editing page with
Select enabled. The original fork is retained at
`backup/pre-v3-rebase-20261112` (`9d1928c`). All 13 fork commits were replayed.

The upstream engine, multilingual decoding, layouts, emoji catalog/search,
settings regrouping, and day/night appearance code are retained. Integration
hooks now use upstream's CommitPath, EditorCommands, KeyboardViews and WordState
instead of bringing back the pre-3.0 monolithic IME.

Retained fork features:

- Local streaming dictation, bundled punctuation, optional GGUF cleanup,
  model management, custom instructions and opt-in diagnostic recording.
- Spacebar swipe-up navigation and Shift-based selection. Upstream's editing
  page remains available by swiping up on ?123.
- Online Google GIF search, recents, automatic attachment after confirmation,
  search diagnostics and animated confirmation previews.
- Local active-time typing history, independent of the live WPM display.

`:voice_typing` opens this fork's local dictation. Upstream's `:dictate` still
hands off to an installed voice IME; these remain separate shortcuts.
`:gifs` and all existing named shortcut choices remain available.
The fork retains its explicit INTERNET/RECORD_AUDIO exception documented in
CONTRIBUTING.md. No upstream pull request has been opened.

The upgrade keeps the DEV application ID and signing key, uses version code 122
(previous fork 100, upstream 121), and reports `3.0.1-voice-dev`. Install over the
existing DEV app to preserve preferences, models, GIF recents and typing history.

## Validation

Required gate, with both local voice engines enabled:

```
JAVA_HOME=/home/nivekmai/.local/share/rea/jdk-21.0.12.1+1 \
./gradlew -PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint --max-workers=4
```

ForkV3IntegrationTest covers named shortcut conversion, mutually exclusive emoji
search/fork panels, and the statistics hook's new home in CommitPath. Existing
voice, navigation, GIF handoff, preview lifecycle and history tests remain in the
combined upstream/fork suite. Native Android UI and IME focus changes require
manual device verification; JVM/source checks are not a substitute.

## Pixel Fold manual checks

1. Upgrade over v27. Confirm existing model, shortcuts, GIF recents and typing
   history remain. Open the new settings groups and switch day/night appearance.
2. Tap and swipe words, use two thumbs, select a suggestion, undo/retype and use
   backspace. Check spacing/case and field switching in a chat and browser.
3. Open emoji search, then swipe up on Space to open navigation: no emoji search
   strip remains. Close navigation and type: text goes into the editor.
4. Start local dictation via `:voice_typing`, pause/resume, then type a key: voice
   stops and normal typing resumes. Test small punctuation with LLM off, and an
   optional cleanup run. `:dictate` should still invoke the external-IME flow.
5. Swipe up on Space, enable Select, move across text, copy/paste and return to
   letters. Separately open upstream's ?123 editing page, enable Select and
   return to ABC: no crash or stuck Shift selection.
6. Search GIFs, play a preview, choose Use this GIF: one attachment goes into the
   original composer. A new field opens letters. Recents and long-press removal
   still work. Use the diagnostics dialog if an editor rejects attachment.
7. Type with the live WPM display off. Settings > Typing > Typing statistics still
   gains samples. Long idle gaps, passwords and no-learning fields must not count.

## Validated artifact

The full gate passed: 1,946 tests per build variant, zero failures/errors,
three skipped per variant. Lint has zero errors. All 73 offline Google search
DOM fixture checks passed. Phone checks above remain manual.

APK: `kinetica-dev-v28-upstream-3.0.1-arm64.apk`

SHA-256: `b46edef48c7e5aada7219e2387167c768bdb9503fad91320678dc6fe2477d848`
