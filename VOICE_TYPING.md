# Voice typing development fork

**Kinetica Voice DEV** installs beside the released keyboard. Open Settings > Typing >
Voice typing to edit cleanup instructions, configure continuous audio, and manage models.
Configure microphone shortcuts through the usual shortcut settings.
Android 12+ and an installed on-device speech recognition language are required.

## Current v16: small local punctuation model

Voice setup now offers three mutually exclusive punctuation routes: **Small local
model**, **Android punctuation**, and **No punctuation pass**. The model is bundled
and defaults on in this experimental build. English only; other languages show
unavailable in this mode and can use Android instead. Existing AI-cleanup and
Fast-formatting preferences are retained. No GGUF download or prompt reset is
needed for the small model. Settings changes take effect next voice session.

The model is Edge-Punct-Casing, distributed as sherpa-onnx's
`sherpa-onnx-online-punct-en-2024-08-06`: 7,490,500 bytes of int8 ONNX weights plus
149,430 bytes of vocabulary. sherpa-onnx 1.13.8 supplies the Android CPU runtime,
which adds separate APK size. Model, vocabulary, source archive and AAR hashes
are pinned by `tools/voice-smoke/prepare_punctuation.py`. The publisher identifies
the model as Apache-2.0; model/runtime/transitive notices ship in the existing
open-source licenses screen. Downloaded build inputs stay in ignored `build/`.

### How it handles pauses

Android still supplies words, but the app does not request Android formatting in
small-model mode. The model receives normalized words across **all uncleaned
utterances plus the current partial**, rather than one utterance at a time. It
also sees up to 40 preceding context words that it cannot insert into the editor.
Original recognizer text is retained for Undo and the richer LLM cleanup.

Updates start at most every 250 ms without waiting for silence. One inference is
active and only the newest pending input is retained. Inference and model loading
run on a separate worker with one CPU thread. Each result must exactly match the
current source/context/generation and preserve every word and number, in order.
Only predicted casing and comma/period/question-mark labels are transferred back
to owned text. Names on the protected list, acronyms, mixed-case names, numbers,
abbreviations and structural source separators are preserved. Fast formatting,
if enabled, then normalizes remaining mid-sentence capitals and comma spacing.

Terminal model punctuation remains provisional while the microphone is armed:
it is hidden until later words make it internal, or until the user pauses capture.
When speech continues, the entire uncleaned passage is reconsidered; a prior
question mark after "pause for" is not fixed at an utterance boundary. A missing
model prediction is not replaced with a hard-coded period. Abbreviation dots
such as `p.m.` are retained. This experiment can still miss endings or choose an
incorrect comma/question mark; it is not a guarantee of grammatical correctness.

Accepted richer LLM cleanup retains ownership of its covered prefix, including
lists/newlines. The small model works on the new suffix with that prefix as
read-only context, preventing it from flattening a list. The LLM can still revise
the entire finalized session on subsequent runs. With AI off, all uncleaned
utterances remain jointly editable up to 6,000 characters; oversized passages or
pathological single words show Punctuation limit rather than silently freezing
an earlier prefix. Undo disables small-model and Fast formatting for the session
and invalidates pending edits. Closing dictation releases its worker and model.

The voice bar distinguishes Punctuating from the richer AI Fixing state, and
shows model failures/skips/limits when AI cleanup is off. A ten-second watchdog
clears a stuck working indicator and rejects late output; it cannot forcibly
interrupt a native call. Recording never waits on inference. Diagnostics include
mode, normalized model input, output, elapsed inference/load time, source/context,
generation and apply/rejection. The replay tool reconstructs these edits without
requiring the native model or executing anything from the report archive.

### Reproducible builds and checks

```
python3 tools/voice-smoke/prepare_punctuation.py --host
./gradlew -PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint
```

The ordinary build still works without either flag and uses an unavailable
punctuation backend. The model preparation script verifies cached/downloaded
artifacts and extracts only the two named model files. The optional host download
provides matching CPU inference for `tools/voice-smoke/PunctuationQuality.java`.
That runner calls the real model and applies its results through production
`PunctuationText` and `VoiceDraft` code; it reports model choices separately from
fidelity/context assertions. It does not treat every observed prediction as a
correct punctuation golden.

### v16 manual acceptance checks

1. Upgrade DEV, select Small local model in Voice setup, and keep **AI cleanup off**.
   Dictate "whenever I pause for", wait, then "a second". Expect a joined phrase
   without a pinned question mark or capital A at the pause. Repeat with several
   pauses. A model-inferred internal boundary may still be wrong; mark examples.
2. Say "how are you I am fine thank you" and another question. Compare live text
   with the text after pressing pause. Final punctuation is shown only if the
   model predicts it; "where are we meeting tomorrow" is a known weak example.
3. Resume while a prediction is running, change the partial recognition, type a
   key, move the cursor, switch editors, close voice mode, and reopen it. No old
   result may overwrite new/manual text. Check no stuck Punctuating label.
4. Test NASA, iPhone, a protected name, 4:15 p.m., 3.14, quotes and a numbered list.
   Turn AI on to format a list, then dictate another phrase. Its list structure
   should remain while new speech gets punctuation. Press Undo during inference:
   exact original recognition must return and pending changes must be rejected.
5. Switch to Android and Off, starting a new session after each change. Small
   inference should stop in both modes; only Android mode requests its formatting.
   Repeat in compatibility audio mode. Non-English small mode shows unavailable.
6. Arm/export diagnostics and replay. Confirm punctuation timing/input/results
   and the actual editor renders are included alongside existing audio events.
   Check a long dictation reaching the 6,000-character limit reports the limit
   while still inserting recognized speech.

### v16 validation and artifact

Final native-enabled `assembleDebug test lint` passed in 1m58s. Debug and release
each ran 1,182 tests, zero failures/errors, two existing skips; lint zero errors.
Eleven new unit regressions cover pause continuation, stale partials, extending
cached punctuation, rich-cleanup interaction, Undo, read-only context, exact word
and number fidelity, source structure/names, native input bounds, abbreviation
periods and revision across multiple utterances. The ordinary speech-only build
also passed. Four synthetic diagnostic replay tests passed, including a new
18-operation small-model trace with a stale result and Undo.

Nine real-model host checks passed through production draft/guard code. Reported
inference times were 9–11 ms for the short cases and 30 ms for a 180-word passage
on this development CPU, excluding loading; these are **not phone measurements**.
The observed model output keeps "whenever I pause for a second" together but
leaves it without a final period, and misses the question mark in "where are we
meeting tomorrow". It correctly punctuates the simple "how are you I am fine
thank you" example. The model does not turn spoken list numbers into list layout;
that remains richer-cleanup behavior. These known misses are preserved in the
quality runner output, not hidden by adding punctuation heuristics.

Bundled model/vocabulary hashes, native runtime, unchanged LLM prompt and complete
license notices were verified inside the APK. The signature matches v15. Native
ELF segments and uncompressed APK libraries are aligned for 16 KB pages. Phone
latency, live capture and settings touch/lifecycle still need the manual checks.

Version 2.0.15-voice-dev (89), same DEV package/signing certificate.
APK: `build/voice-artifacts/kinetica-voice-dev-v16-arm64.apk` (53,279,168 bytes).
SHA-256: `6f7e35e9a1bb78be29cf7b413aee241099deba5893314c9ffe740862262edf41`.

## v15: Android punctuation experiment

On Android 13+, recognition now requests `EXTRA_ENABLE_FORMATTING` with
`FORMATTING_OPTIMIZE_LATENCY`, plus `EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION`.
This applies to both continuous audio and compatibility recognition, independently
of automatic AI cleanup and Fast formatting. Android 12 keeps the previous path.
The new **Android punctuation** switch in Voice setup defaults on; changing it
takes effect in the next voice session. No new model or prompt reset is required.

The app always selects the first recognition hypothesis, for partial, segment and
final callbacks. When the provider honors formatting, that is the formatted text
and the second hypothesis is raw; when ignored, the first is still its best result.
We do not interpret two alternatives as proof of formatting support. Diagnostics
record the requested mode, selected text and all returned hypotheses. Existing
replay events use the selected text, so the Android formatting edits are replayed
without guessing which punctuation came from which model.

The selected recognizer transcript is the draft's original text and the LLM input.
Undo removes our Fast/AI edits but retains punctuation supplied by Android. The
unformatted alternative, when supplied, is available in armed diagnostics. Fast
formatting still checks capitals/spacing and respects supplied sentence endings;
it cannot tell whether an Android-inserted period reflects a genuine sentence end.
The LLM can still revise punctuation during its later whole-draft cleanup.

### v15 manual acceptance checks

1. Upgrade DEV, open Voice setup, and confirm Android punctuation is on. Turn AI
   cleanup off in the voice bar. Dictate "where are we meeting tomorrow" and
   "this is the first sentence this is the second sentence". Check whether the
   question mark/periods appear from recognition without a Fixing job.
2. Pause mid-sentence, then continue; repeat with a genuine sentence boundary.
   Watch whether partial trailing punctuation stays hidden and whether final
   segments still introduce premature periods. Record this rather than assuming
   pauses always mean sentence endings.
3. Turn Android punctuation off, start a new session, and repeat. Keep AI cleanup
   off and the Fast-formatting setting unchanged for this comparison. Then try
   with Fast formatting off too to inspect Android's own capitalization.
4. Repeat in compatibility mode (Continuous audio off), then restore continuous
   audio. Check partial/final text, pause/resume, and no duplicated full-session
   final after segments. Android 12 should show the disabled punctuation switch.
5. Enable AI cleanup, speak during a cleanup, and Undo. New speech must survive;
   Undo should retain Android's transcript while removing our later changes.
6. Arm diagnostics and repeat one question and a paused sentence. Verify
   `android_formatting_requested` in config/start/hypothesis events, the full
   hypotheses array, and selected/rendered text. A request alone is not proof
   that the device honored it. Export only through the existing manual flow.

### v15 validation

Native-enabled `assembleDebug test lint` passed in 3m24s. Debug and release each
ran 1,171 tests with zero failures/errors and two existing skips; lint zero errors.
The packaged APK contains the Android formatting request constants, arm64 native
cleanup runtime and unchanged bundled prompt. Signature verification matches v14.
No connected Pixel/emulator was available; the manual checks above establish
provider support, actual punctuation quality and phone latency.

Version 2.0.14-voice-dev (88), same DEV application id and signing certificate.
APK: `build/voice-artifacts/kinetica-voice-dev-v15-arm64.apk` (19,139,104 bytes).
SHA-256: `3351fee33be5216a65429baf6a78980502b58ebc675ee8d0540573a061595301`.

### Next comparison: small punctuation model

The requested follow-up is Edge-Punct-Casing via sherpa-onnx's English online
punctuation model (`sherpa-onnx-online-punct-en-2024-08-06`). Its quantized weights
are approximately 7.1 MB plus a 146 KB vocabulary; the Android inference runtime
would add separate size. It predicts punctuation and casing from text and does
not replace speech recognition or perform the richer list/word cleanup. Before
integration, evaluate recorded transcripts against expected punctuation and
measure latency, then offer it as a separate selectable punctuation route with
Android formatting off for the comparison. Give one fast route ownership of
punctuation/casing, preserve revision checks across pauses and new speech, and
keep the larger cleanup optional. It is not bundled or downloaded in v15.

References: [Android formatting](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_ENABLE_FORMATTING),
[punctuation model and example](https://k2-fsa.github.io/sherpa/onnx/punctuation/pretrained_models.html).

## v14: immediate casing with protected-word exceptions

The user reported that "Taking", "Long" and "Clean" still waited for AI cleanup,
and explicitly requested a denylist instead of an allowlist. V13 only lowercased
selected words; it was a coverage problem rather than a slow fast-pass timer.
V14 removes that allowlist entirely. With Fast formatting enabled, ordinary
title-case words inside a sentence are now lowercased on the next display render,
including partial results, new recognition segments and accepted AI output.
No AI call or debounce is needed for those casing edits.

A new recognizer utterance is not treated as a new sentence. Its opening word
uses preceding displayed text, including read-only editor context for the first
dictated chunk. If a late AI result changes the previous sentence's punctuation,
newer chunks and partials are rendered again with the updated boundary. Explicit
sentence/list punctuation and newlines are still respected; the formatter does
not infer whether an ASR/model-inserted period was actually intended.

Protected exceptions are clear sentence starts, I/contractions, all-caps acronyms,
single-letter initials, mixed-case names, quoted spans, ambiguous abbreviation
periods, and an editable word denylist. The starter list includes dates, languages,
titles and a few names/products. It is not a proper-name recognizer. Unknown
title-case names will be lowercased inside sentences unless added to the list.
Ambiguous entries such as May/Will can also preserve an unwanted capital; users
can remove them. No new dictionary, model or inference dependency was added.

Voice setup > **Protected capitals** opens the list editor. Save replaces the list
for subsequent sessions, Cancel leaves it unchanged, and Restore default previews
the starter list until saved. Empty lists are valid. Matching is case-insensitive
with straight/curly apostrophe normalization and protects possessives too. Each
word of a multiword name is a separate entry, protected wherever it occurs. This
preserves existing case; it does not force canonical name spelling or prevent the
AI from editing wording. The editor bounds input to 8,000 characters and preserves
unsaved text across recreation. Existing model/prompt and Fast-formatting toggle
preferences are kept; no prompt reset is required.

Diagnostics record `case_policy=denylist` and the effective protected-word set;
replay uses that set, including an explicitly empty list. Fast formatting still
retains original ASR for model input and Undo. Undo disables both passes for the
session, so restored raw capitals are not immediately changed again.

### v14 manual acceptance checks

1. Upgrade DEV and leave Fast formatting on. With AI cleanup **off**, dictate
   "It seems like it is still", pause, "Taking a Long time", pause, "To Clean up
   the capitalizations". Expect lowercase joins while partial text arrives,
   without waiting for a final recognition result or an AI job. Repeat as one
   utterance and with different words such as Need, Maintained and Complicated.
2. Dictate a genuine sentence ending, then another sentence. Check sentence starts
   stay capitalized. Test I/I'm, NASA, IT, iPhone/eBay, quoted titles, Dr. May and
   4:15 p.m. tomorrow. Pause alone must not create a sentence boundary.
3. In Protected capitals add an uncommon name (for example Zelda), Save, and start
   a new voice session. Check its existing capital and possessive are kept amid
   ordinary lowercase text. Remove it, save and start again: expect lowercase.
   Check Cancel, Restore default then Save, a deliberately empty list, long pasted
   input validation and rotating with unsaved edits.
4. Repeat with AI enabled and speak while an older cleanup runs. Check new words
   survive, ordinary capitals cannot reappear after cleanup, and punctuation from
   the older result updates casing at the join. Undo must restore exact raw text.
5. Arm diagnostics with a custom list, export and replay. The list is included in
   the explicitly recorded report so its casing choices can be reproduced. The
   recorder's existing consent, storage limits and manual-export behavior remain.

### v14 validation

Native-enabled `assembleDebug test lint` passed. Debug and release each ran 1,171
tests, zero failures/errors, two existing skips; lint zero errors. Six new named
regressions cover the user's Taking/Long/Clean example without an AI job, arbitrary
words beyond the old allowlist, partial and final recognition boundaries, late AI
punctuation changes, editable/empty denylists, name possessives, per-session
isolation, accepted AI casing and exact raw Undo. Three synthetic diagnostic
replays passed (15, 11 and 11 operations), including the effective custom denylist.
The actual-model eight-case quality gate also passed through the new display
formatter. No additional native inference changes or speed claims in this version.
Phone recording, settings touch/rotation and live latency need the manual checks.

Version 2.0.13-voice-dev (87), same DEV package/signing certificate as v13.
APK: `build/voice-artifacts/kinetica-voice-dev-v14-arm64.apk` (19,136,360 bytes).
SHA-256: `5e54288b80539508c5fdfffae631e17406c237f8373fc98c44da750dc1fe197d`.

## v13: cleanup latency and capitalization follow-up

The user reports v12 is working well overall, with slow follow-up cleanup and
remaining stray capitals. This update keeps its model, prompt, audio path and
whole-session editable scope. Saved cleanup instructions need no reset.

- Cleanup throttling now measures the 3.5-second minimum between **starts**, not
  from the previous completion. A slow job no longer adds another idle 3.5 seconds
  before newer speech can be processed. The 1.6-second final-result debounce and
  one-active-job limit remain; failures still require new speech or explicit retry.
- Native inference reuses only the identical token prefix of a successful request.
  Before reuse, it removes generated output and the changed input suffix from KV
  memory, then decodes at least the last input token for fresh logits. Failure,
  cancellation, model replacement, context-size change and release invalidate the
  cache. Models that cannot remove a partial sequence fall back to full prefill.
  Raw ASR remains the authoritative model input; no cleaned response is fed back.
- Fast English formatting now checks common words throughout chunks and model
  results, including contractions and common adverbs. It restores lowercase starts
  after clear sentence/list boundaries. It preserves quoted spans, I, acronyms and
  words outside the correction set. Abbreviation periods (including p.m.) are
  treated as ambiguous, so the following word's case is preserved. It does not
  infer sentence boundaries from pauses, correct spelling or format lists/times.
  Proper names overlapping the common-word set remain a heuristic limitation.
- The display is still derived from raw/model chunks. Late model results recheck
  newer joins; Undo restores original ASR and disables both passes for the session.
  Fast-format diagnostics include the post-model display changes.

### v13 manual acceptance checks

1. Upgrade DEV, leave Fast formatting on and keep the existing cleanup prompt.
   With AI off, say "It's also regularly missing capitalizations so I'm not sure
   what's going on with that and they're sneaking in", pausing in several places.
   Check common interior capitals are removed, including inside one ASR callback.
2. Enable AI and repeat. Check accepted AI text cannot restore those stray capitals
   and clear sentence starts are capitalized. Test May, Will, NASA, I, quoted
   titles, Dr. May, 4:15 p.m. tomorrow, comma spacing and the numbered-list example.
3. Speak another phrase while cleanup is running, then pause the microphone. After
   a job lasting over 3.5 seconds, expect queued speech to start without the old
   extra delay once its final-result debounce has elapsed. Recording must continue
   independently. Confirm the bar returns to the completed state after the last job.
4. Undo during and after cleanup, edit/type normally, toggle Fast formatting off,
   and retry after a timeout. Confirm no late job overwrites raw restored text.
5. Arm/export diagnostics to compare job-start/job-result timing and displayed
   capitalization. Host speed is not a phone benchmark; battery/thermal effects,
   Android lifecycle, microphone behavior and touch UI still need phone testing.

Host cache regression: compile/run `tools/voice-smoke/PrefixReuse.java` with the
same shim/classpath as ModelQuality. It compares exact generated bytes against
fresh contexts for growing, shrinking and identical input, changed instructions,
and timeout recovery. Fresh timings include loading and are not a pure cache
speed comparison. The existing native status smoke also checks cancellation,
input/output limits and release. ModelQuality now validates the displayed draft
through the actual fast formatter as well as model acceptance and content.

### v13 validation

Final native-enabled `assembleDebug test lint` passed. Debug and release each ran
1,165 tests, zero failures/errors, two existing skips; lint zero errors. The
synthetic report tests replayed 15 and 11 operations, and the user's second report
replayed all 109 operations. New regressions cover interior common-word capitals,
contractions, accepted AI casing, clear sentence/list starts, quotes across chunks,
abbreviation periods, mixed-case names, late jobs, raw Undo, and throttle/debounce.

Native exact-output cache checks passed all five cases and timeout recovery.
The native status smoke passed stale tickets, cancellation during inference,
input/output limits, timeout, subsequent success, and release. The model quality
gate passed all eight cases, including lists, ordinary quantities, time formatting,
continuation, word correction, question punctuation, and names/acronyms. Missing
initial capitals in two model outputs are now corrected in the displayed draft.
The model still sometimes splits or changes wording, and a separate smoke example
left "four fifteen" unchanged. This update does not train or replace the model.

Matched host comparison rebuilt the v12 native source against the same pinned
llama.cpp libraries and ran both backends with the identical Qwen 2.5 1.5B Q4_K_M,
prompt, request builder, ordered eight-case corpus and displayed-output checks.
Both passed 8/8. Excluding the first model-loading case, v12 median was 2.49 s
(range 2.29–3.03 s); v13 median was 0.87 s (0.80–1.21 s), a 65% reduction in this
single host trial. First-case times were 5.63 s and 5.45 s. These exclude Android
scheduling and are neither repeated statistical trials nor phone measurements.

Version 2.0.12-voice-dev (86), same DEV package/signing certificate as v12.
APK: `build/voice-artifacts/kinetica-voice-dev-v13-arm64.apk` (19,134,016 bytes).
SHA-256: `758aba249759fb3737f30928a66c01052e410e04d86d94ee108ca4b0efd2ccfa`.

## v12: fast recognition formatting and model list evaluation

Voice setup now has **Fast formatting (English)**, enabled by default and independent
of the AI toggle. It fixes spaces around commas followed by letters, preserving
numeric separators, and lowercases a small set of common English function words
at recognition joins when the preceding text lacks sentence punctuation. It keeps
the initial recognition capital at a session/sentence start, after a colon/newline,
and for names outside that set, the pronoun I, and acronyms. Other languages pass
through unchanged. This is a conservative heuristic: it cannot infer an unpunctuated
sentence boundary or recognize every proper name. It does not change spelling,
homophones, times, list structure, or invent sentence-ending punctuation.

The fast pass is derived from the current draft and runs only over unedited
recognition chunks and partials. It recomputes joins after a late model result,
without rewriting accepted model output or losing newer speech. Original ASR text
is retained for model requests, diagnostics, and Undo. Undo restores the raw draft,
cancels AI cleanup, and disables fast formatting for the rest of the session.
The AI toggle stays off until re-enabled; the fast preference is restored on the
next session. Details show whether fast formatting is active. Diagnostic exports
include its configuration and before/after edits; replay understands both old and
new captures. No private audio or full diagnostic trace is committed.

The new bundled prompt and Structured preset include a numbered-list example with
an introduction. The extra runtime wrapper is reduced to "Edit across recognition
pauses." Longer wrappers caused regressions in the small model's word correction
or phrase preservation during host evaluation. The user's second report established that ASR captured the list
markers but the model returned a paragraph. The user also supplied a separate
audio-based assessment confirming the enumeration; audio was not heard directly
in this coding session. The expected fixture preserves the introduction and uses
numbered lines, correcting the assessment's inconsistent bullet-list rendering.
Saved custom instructions are preserved: **Cleanup instructions > Restore default
> Save** is required to adopt this prompt when a custom prompt is saved. Initial
host experiments with the older custom prompt still produced paragraph lists.

The fidelity guard now checks retained source words as well as whether output
words were grounded in the input. This rejects the observed model failure that
returned only the list and dropped its introduction. Filler repetition and clear
word substitutions remain allowed. The check is conservative, not a guarantee of
semantic accuracy. It can reject valid extensive edits; the current text is kept.

After a result, queued newer speech now displays **Cleanup waiting**, not **Updated**.
The model still edits all finalized speech within the existing budget. There is no
deterministic list or time formatter and no new inference/audio thread.

### v12 manual acceptance checks

1. Upgrade the existing DEV install. In Voice setup, verify Fast formatting is on.
   Select Cleanup instructions > Restore default > Save to use the tested prompt.
2. With AI off, dictate "I was relieved because", pause, then "It arrived", pause,
   then "And I could leave". Check the joined common words are lowercase and that
   May, Will, NASA and I are preserved. Check comma spacing without changing 1,000.
3. Turn Fast formatting off, start a new session, and verify raw recognition is
   retained while AI is off. Re-enable it for the next checks.
4. With AI on, dictate the three-item test and pause the microphone. Keep voice
   mode open until the final cleanup finishes. Expect the introduction and three
   numbered lines. Repeat with groceries and ordinary quantities in one sentence.
5. Speak during cleanup, then Undo. Check new speech survives, the raw draft is
   restored, and neither pass immediately changes it again. Verify Details opens
   and the waiting/running/final status matches pending work.
6. Arm diagnostics and repeat the test. Export and replay; inspect fast-format,
   AI-result and editor-readback events separately. No device/emulator is attached
   here, so touch behavior, provider behavior and phone latency still need testing.

Host quality gate (after compiling the Android debug classes): compile
`tools/voice-smoke/ModelQuality.java` alongside the existing host shim with the app
Kotlin classes and Kotlin stdlib on the classpath. Run
`com.kinetica.keyboard.voice.ModelQuality` with the host JNI library, model path,
and `app/src/main/assets/voice_cleanup_prompt.txt`. It asserts actual model output
for the recorded list, a different list, quantities, time, continuation capitals,
a recognition error, a question and names. Unlike draft/guard unit tests, this
checks that the model actually produces the required edits.

### v12 validation

The final `-PvoiceLlm=true assembleDebug test lint` gate passed: 1,161 tests per
debug/release variant, zero failures/errors, two existing skips per variant, and
zero lint errors. Both synthetic diagnostic replays passed (15 and 11 operations),
and the second shared report replayed 109 operations successfully.

All eight `ModelQuality` cases passed with the Qwen 2.5 1.5B Q4_K_M model, actual
compiled request builder, native backend, result decoder and acceptance guard.
The recorded example returned both introductory sentences and all three numbered
lines. The time case retained "a time like" and returned `4:15 PM.`; the
continuation case returned "because it arrived, and I could leave"; the error
case changed "should and" to "should end". Runs took about 2.2–5.7 seconds on
the host, not the phone. These are narrow regression checks, not a general accuracy
guarantee. Known remaining imperfections include a lowercase initial word in the
question/error probes and rewriting "and I work" into a separate sentence in the
names probe. The fast pass does not override those accepted model choices.

APK: `build/voice-artifacts/kinetica-voice-dev-v12-arm64.apk` (19,132,988 bytes),
package `com.kinetica.keyboard.dev`, versionCode 85 / `2.0.11-voice-dev`.
SHA-256: `9038977d0c475f8710a207f029a69bae31d1ec03b2cdd0d51322c9f4d96c7cb7`.
Signature verified with the existing DEV certificate; packaged prompt, formatter
class and arm64 native library verified. Physical-device checks above remain open.

## Historical v11 changes, driven by the first phone report

The shared diagnostic replayed 322 operations exactly against v10. Its editor
readback matched the requested text. The report showed three model outcomes:
following-context words copied into an earlier snapshot, an overly short result
rejected by the guard, and another context word prepended to the text. A fourth
job was still running when the session closed and cancelled it. No native timeout
was recorded. No numbers appeared in the ASR alternatives or finalized segments.
This does not independently establish what was spoken: audio playback was not
available to the assistant in this environment. Capture timestamps and PCM delivery
were inspected; the delivery events report 508 bytes not written at session exit.

Model requests now contain one authoritative original finalized transcript, without
a second edited copy or the following partial. Existing typed context before the
session remains explicitly read-only when present. Earlier finalized speech is
still editable; accepted model edits remain visible, and original speech remains
available for Undo. Both original and edited lengths must fit the session budget.
The short structural instruction emphasizes continuous speech and complete output.
Saved custom instructions remain in effect.

A new acceptance check rejects words found only in unowned surrounding context.
It would reject both context-copying edits in the captured report. It does not
strip words or perform automatic formatting. Number checking now treats standalone
English zero through twenty as equivalent to their digit forms, allowing model-
generated numbered lists/times when those number words were recognized. It still
rejects changed values and invented numbering. Compound numbers remain conservative.

The synthetic regressions reproduce the failure shapes without committing the
private report/audio. The report analyzer now identifies jobs unfinished when
capture ended, distinguishing session closure from simply stopping diagnostics.
Because the acceptance behavior intentionally changed, replaying an old bad result
against the new implementation can report an acceptance mismatch; v10 replay above
established the baseline, and the new regression tests assert rejection instead.

Host probes using the captured custom instructions and original text now returned
complete punctuated text with the question mark and lowercased interior capitals,
without the inserted initial “Is” or duplicate following fragment. Model limitations
remain: some probes removed a connecting phrase, and list structure is not reliably
inferred when recognition omits the spoken enumeration. This update does not claim
to recover words/numbers absent from recognition or to have retested speech on a phone.

Manual follow-up: replay the same dictation with diagnostics armed; inspect requested
model data, boundary rejection reasons and final output. Dictate explicit number
words in a list, pause the microphone, and allow the final cleanup to finish before
leaving voice mode. Typing still exits and cancels pending work to protect manual edits.

## Historical v10 behavior

The microphone and cleanup have separate toolbar labels. The status cell directly
opens a persistent keyboard-owned details popup: job/revision, elapsed seconds,
latest speech revision, actual failure code/stage, Retry, Mark problem and Stop
recording. No hidden-button forwarding or toast is used for cleanup details.

Cleanup uses one active job and one coalesced pending revision. Final recognition
callbacks debounce for 1.6 seconds; after a result there is a 3.5-second interval
before the next automatic pass. The completed result remains visible during that
interval. Partial speech still updates independently while the model works.
Failures stop automatic retries of accumulated text; new finalized speech or Retry
can start another attempt. Cancellation/timeout invalidates late callbacks.

Requests include both the current edited transcript and its original recognition.
The fidelity guard also compares against original recognition. All finalized speech
remains editable, up to the existing 3,000-character budget; input tokens (including
instructions, original and edited text) are limited to 3,072. Output is limited to
1,536 tokens. Whole-session inference still grows with dictation length: this is
not an unbounded long-document editor. No deterministic formatting was reintroduced.

The native worker caches model weights and inference context for the voice session.
KV contents are cleared between requests, so model conversations cannot bleed into
later requests. Model identity includes file identity/size/mtime to invalidate the
cache on replacement. Session exit cancels work and queues cache release. Android memory pressure also
suspends active/pending cleanup and releases the cache, with explicit Retry available. Native
results distinguish cancellation, timeout, input/output limits, model errors and
engine errors. A separate 35-second UI watchdog covers executor wait as well.

### Recording a diagnostic example

1. Open Voice settings > Voice diagnostics and enable **Record the next voice
   session**. This is a one-session opt-in and resets when capture starts.
2. Return to an ordinary text field and invoke voice typing. **REC** appears in the
   status cell. Reproduce the problem. Tap status > **Mark problem in recording**
   to place a timestamped marker without ending speech or cleanup.
3. Type a key to end dictation, or tap status > **Stop diagnostic recording** to
   stop capture while dictation continues. Then open Voice diagnostics.
4. Export the completed report using the Android document picker. Select Drive
   there if desired, then explicitly share the resulting ZIP for investigation.
   Export does not upload automatically or grant anyone access.

The ZIP contains `audio.wav`, `events.jsonl`, `manifest.json` and `README.txt`.
It includes private microphone audio, ASR callbacks with alternatives/confidence when supplied, original/current draft text,
exact system/user model requests, outputs and failure codes, job/revision IDs,
requested editor changes and bounded readback, nearby/selected editor context,
recording mode transitions, device/app/model metadata, and user problem markers.
It contains no model weights. No diagnostic text is written to logcat.

Audio is copied from the same PCM stream supplied to Android recognition, without
opening a second microphone. Each chunk has source/chunk IDs, sample offsets and a monotonic read-end
timestamp. Samples concatenate in the WAV; timestamps identify pauses/restarts.
Pipe-delivery events report bytes sent, not bytes consumed by the service. Legacy
compatibility mode cannot provide this stream; those gaps are explicitly recorded.
Editor readback is a bounded suffix returned by InputConnection, not a screenshot
or a guarantee about the target application's rendered pixels.

Reports live in `noBackupFilesDir`, excluded from Android backup. Capture is bounded
at 10 minutes / approximately 64 MB and stops on storage or queue failure without
stopping dictation. The latest three sessions are retained; starting another prunes
the oldest. Setup can delete all reports. Process death can leave an unfinished
capture; currently only finalized reports are exportable. Do not commit real reports
or promote private recordings to fixtures without reviewing/redacting them first.

### Reconstructing a report

`tools/voice-smoke/replay_report.py REPORT.zip OUTPUT_DIR --replay` writes an exact
text timeline, WAV, summary of markers/failures/editor mismatches and a replay TSV.
The optional replay compiles `ReplayTrace.java` against the app's debug Kotlin
classes and runs actual `VoiceDraft` transitions, recorded cleanup outputs and
expected rendered drafts without Android or an LLM. Set JAVA_HOME and run the app
build first. It does not extract arbitrary archive paths or make network requests.
The synthetic `test_replay_report.py` exercises the complete ZIP-to-JVM path.

This verifies event/application behavior, not recognition quality. To create a
semantic regression, listen to the aligned audio, label the intended wording,
inspect the exact recorded prompt/result, and add an explicitly reviewed fixture.
A model error can be faithfully replayed; that alone does not make its wording right.

### v10 manual acceptance checklist

- Start speech; confirm microphone and cleanup labels remain distinct while live
  words change. Resume during cleanup; earlier edits must preserve the new speech.
- Tap status after timeout and while processing; verify a persistent popup, usable
  Retry after completion/failure, and no editor focus change or stopped recording.
- Capture one session, mark a problem, pause/resume, type to exit. Export ZIP to
  Drive; play WAV, inspect markers, requests/results and editor readback timeline.
- Disable continuous audio; confirm the report marks unavailable audio rather than
  implying a complete recording. Check recorder stop, limits and deletion.
- With capture unarmed, verify no new diagnostic directory is created. Password
  fields remain blocked by the existing private-field gate. Check Undo, cursor
  movement, ordinary typing, app switching, and model changes between sessions.
- Measure cold/warm model latency and memory on the phone. No device/emulator is
  attached to this development workspace; host tests cannot establish touch behavior.

## Historical v9 behavior (superseded by v10)

Cleanup is model-only: the capitalization allowlist and time-formatting rules have
been removed. Recognition text is displayed unchanged until the local model returns
an accepted edit. Without a model, voice typing works but no cleanup runs.

Every request includes ALL finalized text in the active dictation, up to 3,000
characters of current edited text. Earlier utterances and earlier model edits stay
editable. Above that budget, cleanup explicitly stops with a limit message instead
of quietly selecting only the newest utterance. Text typed before this voice session
and partial speech still in recognition are read-only context. Speech finalized
while a job runs is preserved and included in the next whole-session request.

Instructions now use the model chat template's system role; transcript and context
are escaped JSON data in the user role. Restore default loads concise instructions
without embedded examples or a trailing Raw marker. Saved custom instructions are
preserved; Restore default + Save is needed to try the new bundled wording if an
older custom prompt is saved. This is prompt tuning, not retraining model weights.

The native budgets are 2,048 input tokens, 1,536 output tokens and 30 seconds. The
UI deadline is 35 seconds including queue wait. Longer sessions can still time out
on-device; incomplete output is rejected. No word substitutions or spacing repairs
are performed by a rule-based postprocessor. The existing fidelity guard and Undo
remain. The guard cannot prove a correction is faithful.

## Using voice typing

1. Open voice typing in a text field. In **Models / setup**, allow microphone access.
2. Select **Download model** under Qwen 2.5 1.5B (1.12 GB) or 0.5B (491 MB).
   Wi-Fi only is selected initially. Turn it off to allow mobile data. Downloads
   continue outside setup; reopen setup to verify and activate a completed file.
   Allow twice the model size in free storage during installation.
3. Return to the text field and invoke voice typing. Partial words appear directly
   in the editor and are revised as recognition improves. On supported Android 13+
   providers, audio stays open across pauses and results arrive in segments.
   **Pause / Resume** is one icon in the suggestion strip. Touch a keyboard key to
   return to typing with the dictated text already inserted.
4. **Automatic cleanup** edits finished speech without another button press.
   It removes fillers/repetitions, resolves explicit spoken corrections and adds
   grammar, punctuation, lists and asides. It preserves dictated requests as text:
   “send this to Sam and say…” should not become a message addressed to Sam.
   **Undo cleanup** restores the latest cleaned group and disables automatic cleanup
   so it does not immediately repeat the edit. Repeated Undo restores earlier groups.

The 1.5B model has more capacity but uses more memory and takes longer; neither
model guarantees faithful edits. Review the text before sending. Recognition and
cleanup never press Send/Search. Speech works without a cleanup model. Provider
support for continuous audio must be checked on the phone; the compatibility route
still restarts the recognizer and can leave capture gaps. Tap the microphone to check
the active capture mode. See the v3 notes below for the streamed-audio lifecycle.

## Models and privacy

Catalog entries pin official Qwen Hugging Face revisions, byte counts and SHA-256
hashes in `VoiceModels.kt`. Both models are Apache 2.0 licensed; the app links their
model cards. Models are not bundled in the APK. Downloads use Android DownloadManager
and HTTPS, followed by streaming verification and atomic installation. A failed or
cancelled download does not replace the active model. The downloaded staging copy
is removed after installation. DownloadManager may retain normal system download
metadata. Setup also supports local GGUF imports up to 2 GiB; imported files have
format/size checks but cannot be authenticated against a publisher hash.

This fork now requests INTERNET **only for user-initiated model downloads**.
Speech uses `createOnDeviceSpeechRecognizer`, with no generic/cloud fallback.
The system provider is trusted to implement Android’s on-device contract. The
keyboard creates no audio/transcript files and uploads neither audio nor text.
Cleanup sees the editable dictated words plus read-only context: up to 240 characters
preceding them (including text already in the input when dictation starts), and up to
120 characters of following partial speech. This context stays on-device and is not
part of the replacement range. Voice insertion bypasses dictionary learning and decode tracing. Ordinary
manual typing afterward still follows the upstream keyboard’s behavior.

Models live in `noBackupFilesDir`. Previously imported models continue to work.
Installed catalog models can be selected without downloading again; Remove model
removes the active file. Inference is CPU-only, four threads, with a 20-second
loading/inference deadline, 1,536 context tokens and at most 512 output tokens.
Models are unloaded after each call. Native metadata limits weights and parameter
count to 2.2 GB / 2.2 billion, respectively. Unsupported models or incomplete/failed
inference keep the dictated text and display a status.

## Editor and recognition lifecycle

`on-device recognition → committed live text → automatic local cleanup → suffix replacement`

No composing spans are used. A changing partial replaces its preceding partial.
Cleanup snapshots contain finalized utterances only; later final/partial speech
survives an asynchronous cleanup result. Cleanup operates on a bounded trailing
group (1,500 characters). Very long individual utterances remain raw. A lexical
sanity check rejects obvious unrelated output and invented/reordered numeric tokens;
it is a heuristic, not a guarantee against semantic changes.

The IME verifies the text suffix it owns before replacing it. Moving the cursor,
switching fields/apps, hiding the keyboard or recreating input cancels recording and
pending cleanup. Already inserted text remains. Password fields reject voice input;
private/incognito modes do not persist drafts. Single-line fields flatten newlines.
Permission grants and returning from setup never start the microphone automatically.

## Build and automated checks

The native build targets arm64-v8a (including Pixel 9 Pro Fold):

```sh
git submodule update --init --recursive
sdkmanager 'ndk;29.0.14206865' 'cmake;4.1.2'
./gradlew -PvoiceLlm=true assembleDebug test lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The ordinary `./gradlew assembleDebug test lint` builds speech support without
llama.cpp. The pinned submodule and licenses are in THIRD_PARTY_NOTICES.
The Android toolchain retains the upstream pinned AGP/Kotlin versions.

JVM regressions cover evolving partials, late cleanup preserving new speech,
Undo/close invalidation, bounded snapshots, Unicode suffix edits, cleanup guards,
and download corruption/truncation/cancellation. The JNI host smoke test exercises
real inference, UTF-8, missing files, stale jobs and cancellation:

```sh
cmake -S app/src/main/cpp -B build/voice-host -DCMAKE_BUILD_TYPE=Release
cmake --build build/voice-host -j4
javac -d build/voice-smoke tools/voice-smoke/LocalCleanup.java
java -cp build/voice-smoke com.kinetica.keyboard.voice.LocalCleanup \
  "$PWD/build/voice-host/libkinetica_voice.so" /absolute/path/to/model.gguf \
  app/src/main/assets/voice_cleanup_prompt.txt
```

## Phone checks still required before a PR

1. Upgrade the previous DEV APK; verify imported model/settings survive. Deny mic
   permission, allow it through setup, return and explicitly start recording.
2. Download both catalog models; check progress, Wi-Fi wait, mobile-data opt-in,
   cancellation, network loss, low storage, activity rotation and process restart.
   Complete a download while setup is closed, reopen, and verify automatic selection.
   Switch installed models; remove the active one; import valid and invalid GGUFs.
3. With models and speech language installed, enable airplane mode. Dictate and
   automatically clean a sentence; expect local results or a useful failure status.
4. Dictate across pauses, restart boundaries and Pause/Resume in a native editor
   and browser. Check partial replacement, existing text/selection, single-line
   fields, no duplicate words, and no Send/Search action.
5. Speak more while cleanup runs. Earlier cleanup must preserve the newer speech.
   Undo must preserve newer words and disable auto cleanup. Toggle auto on/off.
6. While listening/finishing/cleaning, move the cursor, edit externally, hide the
   keyboard, rotate/fold, switch apps or change fields. Expect mic cancellation and
   no late text. Repeat rapid open/close, test mic revocation and recognizer busy.
7. Check passwords, incognito, missing languages/recognizer and Android below 12.
   Expect no cloud fallback and no voice transcript learning/files/logs.
8. Evaluate fillers, spoken corrections, lists, asides, names/numbers, multilingual
   speech, and instruction-like dictation. Measure latency, memory and heat on the
   Pixel 9 Pro Fold; host CPU timings are not phone benchmarks.
9. Check large fonts, TalkBack, light/dark themes and portrait/landscape. All setup
   and dictation controls must remain accessible.

No hosted fork or PR has been published. Model downloads intentionally diverge from
upstream’s no-network policy; discuss that and the optional native dependency with
maintainers before proposing an upstream PR.

## Validation of the v2 development APK

- Native ARM64 `assembleDebug test lint` passed. Debug and release each ran 1,118
  tests with zero failures/errors and two existing optional geometry-probe skips.
  Lint has zero errors and 59 warnings, including unused resources from the old
  manual-insert panel. The APK signature verifies and the native library and
  updated prompt are present in the package.
- JNI loading, UTF-8 output, missing-file handling, stale requests and cancellation
  passed on the host with both catalog models. The 1.5B artifact’s SHA-256 matches
  the catalog. This is not an Android microphone or DownloadManager test.
- With the current prompt, 1.5B returned useful list/asides and corrected a new
  Monday/Thursday example in about 5–6 seconds on the development CPU. It also
  chose the abandoned date in another example; that observed failure now has a
  rejection regression. Instruction-like input failed to complete within its budget.
- The 0.5B probes took about 3 seconds for short outputs, but frequently copied
  unrelated example text or chose the wrong date. Those observed outputs are
  rejected by the sanity checks. It also omitted a meaningful word in a French
  probe that the heuristic would not catch. Prefer 1.5B for evaluation; neither
  model is a guarantee of faithful editing, and the automatic switch/Undo matter.
- Pixel 9 Pro Fold microphone, live editor interaction, background download lifecycle,
  foldable layout, latency and thermals still need the phone checklist above.
- APK: `build/voice-artifacts/kinetica-voice-dev-v2-arm64.apk`, version
  `2.0.1-voice-dev` / code 75, package `com.kinetica.keyboard.dev`.
  SHA-256: `1372f40096301a16d65bbc4188e08f6f0359fa5fb0e60d7b7bc409b2447886a8`.

## v3: continuous audio, compact controls and discoverable shortcuts

On Android 13+, voice typing now supplies a live 16 kHz mono PCM stream through
`EXTRA_AUDIO_SOURCE` and requests segmented recognition with
`EXTRA_SEGMENTED_SESSION = EXTRA_AUDIO_SOURCE`. `onSegmentResults` finalizes each
piece without destroying the recognizer or closing the microphone. Silence no
longer starts the old five-second final-result timer, and the 60-second legacy
utterance watchdog does not run on this path. Pause closes the audio stream to
request the final segment; resuming opens a fresh stream. Audio exists only in
memory/OS pipes, never in a recording file.

The audio writer uses nonblocking pipe writes so a stalled service cannot trap a
microphone worker indefinitely after cancellation. Typing, moving the cursor,
hiding the IME or changing fields cancels both audio and recognition, and rejects
late callbacks. No foreground service or background microphone session is added.

Android providers may ignore these optional extras. If the provider rejects the
stream, fails to become ready or unexpectedly finishes an active streamed request,
this session switches to the old local recognition route and displays a compatibility
notice. This fallback can still have gaps. Android 12 also uses that route. In
**Settings > Typing > Voice typing**, Continuous audio is enabled by default on Android 13+
and can be disabled for comparison. Tap the toolbar microphone to see the active
capture mode and cleanup status. Device/provider support still needs phone testing;
the API request alone is not proof of gapless capture on a particular phone.

References: [Android audio-source and segmented-session contract](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_AUDIO_SOURCE),
[segment result callbacks](https://developer.android.com/reference/android/speech/RecognitionListener#onSegmentResults(android.os.Bundle)).

The former full panel is replaced by an icon row above the visible keyboard:
microphone/status, pause-or-resume, automatic cleanup, Undo cleanup, and a gear with
a small microphone for settings. Tooltips and accessibility descriptions explain
each action. Touching any keyboard key ends dictation before processing that key;
there is no separate Keyboard button. Already inserted text stays in the editor.

**Settings > Typing > Voice typing** offers buttons to add/promote the microphone to the
first slot in the idle suggestion bar and `?123` hold menu, preserving other selected
shortcuts. The same action is available in chord/edge-swipe/expansion pickers,
comma/period key roles, and letter alternate lists as `:voice_typing`. Search terms
include microphone, mic, voice, speech, dictation and models. The idle suggestion
bar shows shortcut actions when it has no word suggestions, as in upstream.

### v3 phone acceptance checks

1. Upgrade v2 without uninstalling; expect existing models and preferences retained.
   Open Settings > Typing > Voice typing and put the microphone first in the suggestion bar.
   Invoke it in an empty editor: keyboard remains visible and only the top strip changes.
2. With cleanup off and Continuous audio on, dictate a numbered sequence with pauses
   of 2, 5, 10 and 20 seconds. Expect every phrase once, including its first words.
   Tap the mic to verify Continuous audio remains active. Compare with the toggle off.
3. Dictate for over 60 seconds with pauses. Expect no periodic stop/restart in continuous
   mode. Pause and resume rapidly and slowly; check final words and absence of duplicates.
4. Type, swipe, backspace, shift, long-press or use a chord while listening or cleaning.
   Expect voice mode to exit, the first key/action to work, and no later voice rewrite.
5. Hide the keyboard, change apps/fields, move the cursor, fold/rotate, or revoke mic
   permission during capture. Expect mic release and no stale insertion. Repeat fast
   entry/exit and ensure later sessions can acquire the microphone.
6. Test all voice shortcut paths: bar, ?123, chord, edge swipe, comma/period, and
   `:voice_typing` in a letter list. Verify existing shortcuts and punctuation alternates
   survive. Check large fonts, TalkBack, light/dark modes and folded/landscape widths.
7. If the compatibility notice appears, report that plus the Android speech provider
   and version; do not classify that run as a successful continuous-capture test.

### v3 build validation

`assembleDebug test lint` passed with `-PvoiceLlm=true`. Debug and release each ran
1,125 tests, zero failures/errors and two existing optional geometry-probe skips.
Lint reports zero errors and 47 warnings. New regressions cover uninterrupted
segment accumulation across silence, aggregate-final deduplication, preserving the
last partial on audio close, legacy finals, shortcut selection/serialization, and
comma/period voice actions retaining punctuation alternates.

The signed ARM64 APK is `build/voice-artifacts/kinetica-voice-dev-v3-arm64.apk`,
version `2.0.2-voice-dev`, version code 76. Its certificate matches v2 for an in-place
upgrade; the native cleanup library is included. SHA-256:
`fa16c024f532948144873e61bba1c73dae02608511a0dbf2b600096cfebc5c4a`.
Android AudioRecord/recognizer integration, actual pause-gap behavior and toolbar
interaction still require the phone checks above; JVM tests do not exercise them.

## v4: editable cleanup instructions and Enter shortcuts

Open **Settings > Typing > Voice typing > Cleanup instructions**, or open the
voice gear and select **Cleanup instructions**. Light keeps wording and fillers;
Clean removes fillers and fixes grammar/corrections; Structured also asks for lists
and parentheses. Presets load editable text. Tap **Save** to apply a preset, your
edits, or **Restore default**. Cancel discards changes; rotation preserves the draft.
Saved instructions replace the bundled instructions for subsequent cleanup jobs.
The transcript is appended automatically in the model input; these instructions
use the existing user-message chat template, not a separate system-role message.
The existing cleanup checks and token/time limits still apply.

The shortcut promotion buttons have been removed. Explicit named actions such as
`:voice_typing` and `:paste` now dispatch on Enter and punctuation alternates as
well as letters. Popup labels show their icons while retaining the action token.
Literal punctuation stays literal.

Phone acceptance checks (not covered by JVM tests):
1. Set Enter alternates to `:voice_typing :paste ?`. Slide up on Enter: voice
   starts; the next alternate pastes; the third inserts a question mark.
2. Open Cleanup instructions, select Light, edit and Save. Reopen and verify the
   saved text. Dictate with a model installed and check that cleanup follows it.
3. Load Structured then Cancel: the prior saved text remains. Restore default
   then Save: the bundled instructions return. Rotate with an unsaved edit and
   verify the draft survives.
4. Verify the voice gear still exposes models, and neither shortcut promotion
   button appears. Test normal Enter, punctuation and letter alternates.

### v4 build validation

`-PvoiceLlm=true assembleDebug test lint` passed. Debug and release each ran
1,130 tests with zero failures and two existing skips. Lint has zero errors.
Phone acceptance checks above remain unverified. Version 2.0.3-voice-dev (77)
uses the same signing certificate and application ID as v3 for in-place updates.
APK: `build/voice-artifacts/kinetica-voice-dev-v4-arm64.apk`; SHA-256: `4e4d4aee394a50f6a7ad93287921c733c6b5a16fa0d27c5d949493b57c994bbb`.

## v5: sentence context and recognition corrections

Cleanup requests now carry bounded read-only neighbors separately from the raw
editable snapshot (up to 1,100 characters, reduced to reserve input space for context). Earlier cleaned chunks contribute displayed text to the preceding
context. The snapshot still owns only its finalized chunks: new final/partial speech
and text outside the dictation session remain outside its replacement range.

Runtime instructions explain that recognition segments and pauses are not sentence
boundaries, capitals may be recognition artifacts, and the final thought may still
be incomplete. They apply alongside saved custom instructions. Default, Clean and
Structured instructions also explicitly permit strongly supported misheard-word
corrections. Light remains conservative about wording. Existing custom instructions
are preserved: choose Clean/Structured or Restore default and Save to get the new
word-correction wording. No automatic lowercase heuristic is used.

Phone checks:
1. Dictate “I was relieved because”, pause, then “it arrived and I could leave”.
   With cleanup enabled and a model installed, check that internal capitals disappear
   without losing words. A natural sentence break should still permit a capital.
2. Type “I was relieved because ”, activate dictation and speak the continuation.
   Check that preceding typed text is unchanged and the continuation fits it.
3. Dictate a sentence with a likely misrecognition, then add the rest of the sentence.
   Check that cleanup can revisit earlier words. Compare with valid uses of “cut”.
4. Dictate an unfinished thought, names and acronyms; verify cleanup does not finish
   your thought or indiscriminately lowercase names. Undo should restore raw speech.
5. Save custom instructions and upgrade: confirm they remain saved. Restore default
   and Save when testing the updated bundled instructions.

Host quality probe: compile `tools/voice-smoke/ContextCleanup.java` with the app’s
compiled Kotlin classes and Kotlin standard library, with `build/voice-smoke` first
on the runtime classpath (its JNI-only LocalCleanup shim avoids Android dependencies).
Pass host JNI library, local model and bundled prompt paths. The runner calls the
actual app prompt builder and guard; outputs are quality observations, not golden
assertions or phone performance measurements.

### v5 validation and limitations

`-PvoiceLlm=true assembleDebug test lint` passed on the final app source. Debug
and release each ran 1,134 tests, zero failures, two existing skips; lint zero errors.
The signed APK version is 2.0.4-voice-dev (78), with the same certificate as v4.
APK: `build/voice-artifacts/kinetica-voice-dev-v5-arm64.apk`; SHA-256: `57d3dd6e8abf2dcf90c378bd75840e6034fa222ab22646e025fc47e683d12d0b`.

Host probes with the installed 1.5B model and actual app prompt builder took about
7–8 seconds each without a competing build. It corrected internal capitals in
“I was relieved because It arrived And I could leave” and kept a valid “cut a slice
of bread”. However, it still left “cut home” unchanged and capitalized “It” when
the preceding clause was supplied as read-only context. Invented endings in two
unfinished-phrase probes were rejected by the existing guard. This validates context
plumbing and some capitalization behavior, not reliable recognition-error repair.
A shorter prompt trial made other outputs worse (including an invented ending
accepted by the guard), so it was not shipped. Phone behavior remains unverified.
The current small model can still ignore context or miss substitutions; this release
does not claim to solve those consistently.

## v6: independent capitalization repair

With automatic cleanup enabled, a deterministic English pass repairs a conservative
allowlist of title-cased function words/adverbs inside finalized dictated text. It
runs without a downloaded model and after model results, so known stray capitals
do not depend on LLM compliance. Partial speech stays untouched until finalized.
Raw chunks remain available to Undo, which disables automatic cleanup as before.

The pass uses preceding text across recognition segments and respects explicit
punctuation, line breaks, list markers and opening quotes. It leaves all-uppercase
words, I, unknown words and other languages unchanged. A small title-phrase rule
preserves examples such as The Who. This is deliberately conservative, not a general
proper-name classifier: names/titles made from common words can still be ambiguous,
and unlisted capitals or recognizer-inserted sentence punctuation can remain.

Phone checks: with automatic cleanup enabled, dictate the reported “testing With
the new setup It seems that Sometimes…” example across pauses; wait for finalized
results and verify the known capitals are lowered. Repeat with no model installed,
with automatic cleanup off, after Undo, and with new sentences, names, NASA and I.
Check that typing and cursor movement still stop voice edits.

### v6 validation

Native-enabled `assembleDebug test lint` passed. Debug and release each ran 1,140
tests with zero failures and two existing skips. Lint has zero errors. Six new
capitalization regressions cover the reported example, sentence/context boundaries,
names and acronyms, language scope, Undo and late model output. Phone gestures and
provider behavior remain unverified. No new model-quality claim is made.

Version 2.0.5-voice-dev (79) retains the v5 signing certificate and package.
APK: `build/voice-artifacts/kinetica-voice-dev-v6-arm64.apk`; SHA-256: `2a85ced7142528745cfa8a7a96d28723a598598d3a7e5ae70d748eff95abb975`.

## v7: visible cleanup status, punctuation guidance and time formatting

The left side of the voice suggestion strip shows Queued, then a spinner with
“Fixing… Keep speaking”, followed by Updated / Up to date / Text kept. Tap the
status for details. The pause/resume, automatic cleanup, Undo and setup buttons
remain available. While paused, progress labels say “Mic paused” instead of
“Keep speaking”. Turning automatic cleanup off cancels pending model work and
clears the spinner. Typing or leaving voice mode also cancels pending cleanup;
continue speaking during cleanup, but wait for completion if you want its edits
before leaving the keyboard or sending.

English basic formatting now recognizes digit-based 12-hour times with explicit
AM/PM, for example `4 15 p.m.` → `4:15 p.m.`. It validates hours and minutes and
does not interpret bare number pairs, invalid times or spelled-out number words.
This runs on finalized chunks without a model and is Undoable. Split times spanning
separate recognition chunks may remain unformatted until model cleanup joins them.
The conservative casing list now includes Doing and several common participles;
unknown words and explicit punctuation boundaries remain protected.

Runtime model instructions now explicitly request periods for complete statements
and question marks for direct questions, leaving only unfinished thoughts open.
This also applies when a saved custom prompt exists; saved wording is preserved.
Automatic punctuation still depends on model inference and its fidelity checks.

Phone checks: watch Queued → Fixing → result during dictation while continuing to
speak; toggle auto off while processing and verify the spinner disappears; test
Pause/Resume and Undo. Verify status text is legible in portrait/landscape and both
themes. Dictate “it is still Doing capitals”, explicit times, a complete question,
a statement and an unfinished phrase. Confirm unchanged numbers and names.

### v7 validation

Native-enabled `assembleDebug test lint` passed. Debug and release each ran 1,145
tests, zero failures, two existing skips; lint zero errors. New regressions cover
Doing, explicit time formatting, invalid and ambiguous number pairs, unchanged
quantities, idempotence, Undo, and preserving newer partial speech.

Host CPU probes using the actual prompt builder and 1.5B model produced:
- “can we meet tomorrow” → “Can we meet tomorrow?”
- “the report is ready” → “The report is ready.”
- “can we meet at 4 15 p.m.” → “Can we meet at 4:15 p.m.?”
- “we are meeting at 4 15 p.m. tomorrow” → “We are meeting at 4:15 p.m. tomorrow.”
- “please bring 4 folders and 15 pens” → “Please bring 4 folders and 15 pens.”
All five passed the cleanup guard. An invented completion of “I was going to”
was rejected, retaining raw text. Probes took roughly 9 seconds on this host;
these are examples, not a guarantee of model accuracy or phone performance.
Phone rendering, touch and speech-provider checks remain unverified.

Version 2.0.6-voice-dev (80), same package and certificate as v6 for in-place
installation. APK: `build/voice-artifacts/kinetica-voice-dev-v7-arm64.apk`; SHA-256: `c67ff3636aad20712903b8470e98d4502067cfa647cc251dedd4ef5081296015`.

## v8: bounded AI status and preserving edits across cleanup requests

The busy indicator now says **AI cleanup**, distinguishing model work from the
immediate casing/time rules. Tapping it explains the two stages. A 25-second
main-thread watchdog covers queue wait as well as inference; it requests native
cancellation, clears the busy state, keeps current text, and ignores late results.
It does not automatically retry timed-out work. New dictated final speech or
toggling automatic cleanup can trigger a new request. Native cancellation remains
cooperative; the UI watchdog does not forcibly terminate a blocked native thread.

Snapshots now carry both current edited input and original recognition text. Model
requests and fidelity checks use current input, so a later cleanup sees prior
capitalization/formatting edits instead of replaying old recognition errors. Raw
text remains reserved for Undo. Snapshot IDs still prevent late edits from replacing
new speech, and the completion gate prevents a stale callback from finishing a newer
request. This fixes a source of regression, not all possible model casing errors.

Phone checks: dictate the “time like 4 15 PM” example, observe immediate formatting
while AI cleanup runs, then verify a terminal status within about 25 seconds. Speak
a second utterance before cleanup finishes and verify earlier edits remain. Turn
auto off during AI work, turn it on again, and check that an old callback cannot
clear the new indicator or apply text. Check Undo still restores raw recognition.

### v8 validation

Native-enabled `assembleDebug test lint` passed. Debug and release each ran 1,150
tests, zero failures, two existing skips; lint zero errors. New regressions exercise
timeout/completion/cancellation ordering, stale callbacks, overlapping utterances,
current edited input and preservation of original speech for Undo. Android Handler
timing and the reported phone interaction still require the manual checks above.
No additional model-quality claim is made; the model prompt/backend are unchanged.

Version 2.0.7-voice-dev (81), same package and certificate as v7.
APK: `build/voice-artifacts/kinetica-voice-dev-v8-arm64.apk`; SHA-256: `9a6dd2479dbbb514894f872d7a91f76abdc8f4ed577926661e5ff380be604e4f`.

## v9 manual acceptance checks

1. With no cleanup model installed, dictate a spaced time and stray capitals. Verify
   recognition text is kept and the bar says a model is needed.
2. Restore default + Save; select the 1.5B model. Dictate times, sentences missing
   comma spaces, direct questions, “a sentence should and with a period”, and valid
   uses of “and”. Inspect actual model output rather than expecting perfect edits.
3. Pause mid-sentence, resume before cleanup completes, then pause again. Verify the
   next request can revise text on both sides of the first pause. Test a paragraph
   over the old 1,100-character boundary, including an earlier already-cleaned group.
4. Exceed 3,000 current characters: verify an explicit limit message, no silent
   restriction to the latest utterance, and no loss of editor text.
5. Check cancellation, timeout, late callbacks, Undo and ordinary keyboard input.

## v9 validation

Removed deterministic capitalization/time formatters and their tests. New draft
regressions cover whole-session requests, previously merged text beyond the old
1,100-character tail, preservation of newer speech, and explicit oversize refusal.
The native-enabled `assembleDebug test lint` gate passed: debug and release each
ran 1,140 tests, zero failures/errors, two existing skips; lint zero errors.

Host probes use the actual Kotlin request builder, JNI backend, acceptance guard
and Qwen 2.5 1.5B Q4_K_M model. The selected shorter prompt produced `4:15 PM.`,
fixed “a sentence should and with a period or a question mark” to
“A sentence should end with a period or a question mark.”, corrected the
mid-sentence capitals in both pause examples, and preserved “bread and butter”.
It added comma spacing and a question mark to the meeting question, but missed
its initial capital. It incorrectly added a period to unfinished “I was going to”.
These are model limitations; the guard accepts these outputs and does not repair
them. Longer, more prescriptive instructions performed worse in this small probe.
No model weights were trained. Host timings are not phone latency measurements.
On-device recording, UI and memory behavior still require the manual checks above.

Version 2.0.8-voice-dev (82), same package and signing certificate as v8.
APK: `build/voice-artifacts/kinetica-voice-dev-v9-arm64.apk` (19,072,480 bytes);
SHA-256: `fd97d9148b68256cd24f03a0d4aca7b4e264c25cd6806088c4b1ac9c527ce7ee`. Final prompt packaging gate also passed.

## v10 validation and known limits

Native host checks passed with the actual Qwen 2.5 1.5B model, Kotlin request builder
and fidelity guard: stale request cancellation, missing model, input token limit,
output token limit, native timeout, cancellation during work, reuse after failures,
and explicit engine release. The first example took 5.46 seconds; subsequent
different examples took 2.79–4.21 seconds on this host. These are not matched
benchmarks or phone latency estimates.

Model output formatted `4 15 PM` as `4:15 PM`, changed “should and with a period”
to “should end with a period.”, and preserved “bread and butter”. It still missed
some sentence-initial capitals and added a period to unfinished “I was going to”.
The new architecture and diagnostics do not imply those quality issues are solved.

The synthetic ZIP-to-JVM replay test passed: 14 operations covering evolving
partials, new finalized speech while a job is active, and application of the older
job without losing newer speech. JVM regressions also cover coalescing, timeout
followed by a stale callback, retry, disable/enable, memory pressure, distinct
native results, raw-versus-edited prompt fields, JSON escaping, WAV headers and PCM
sample offsets. Android touch, actual capture-provider behavior, export providers
and on-device latency still need the manual checks above.

Final native-enabled `assembleDebug test lint` passed: debug and release each ran
1,147 tests, zero failures/errors, two existing skips; lint zero errors.
Version 2.0.9-voice-dev (83), same package and signing certificate as v9.
APK: `build/voice-artifacts/kinetica-voice-dev-v10-arm64.apk` (19,130,216 bytes).
SHA-256: `2a3def2d54ccdf6b4def4b9c105178a5684b479afd3a9a45252cd3f15cb1d606`.

## v11 validation

Native-enabled `assembleDebug test lint` passed after an interrupted first run.
Debug and release each ran 1,152 tests, zero failures/errors, two existing skips;
lint zero errors. The real captured bad outputs for jobs 2 and 4 were separately
checked against the compiled new guard: both rejected. The synthetic report replay
passed 15 operations, including a final job unfinished at session closure.

Host native status/cancellation/reuse checks passed with the actual app request
builder. The model still did not reliably turn a spoken enumeration into a numbered
list, and left “meet at four fifteen” unchanged in one probe. The numeric guard now
permits equivalent formatting but does not force the model to perform it. No phone
ASR-quality fix or on-device latency improvement is claimed.

Version 2.0.10-voice-dev (84), same package and signing certificate as v10.
APK: `build/voice-artifacts/kinetica-voice-dev-v11-arm64.apk` (19,130,220 bytes).
SHA-256: `abb0fd308e668a7183c1c947c5915ed91a32ab686ae2cdc9d88831d7fc54b4ca`.
