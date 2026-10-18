# Local voice typing: quick start and reuse guide

This experimental fork keeps the keyboard visible while dictation controls replace
the suggestion strip. Android supplies recognized words; a small on-device model
adds punctuation and casing. A heavier local LLM can optionally rewrite the text,
but is not needed for the small punctuation model. Current user testing reports
good results with that heavier pass disabled; this is qualitative feedback, not a
controlled accuracy or phone-latency benchmark.

## Try the small-model setup

Use Android 12+ with an available on-device recognition language. The bundled
punctuation model supports English. In Settings > Typing > Voice typing, choose
**Small local model**, leave **Fast formatting (English)** enabled, and turn
**AI cleanup off**. Start a new voice session after changing setup. No GGUF model
download is required. The `:voice_typing` action works through the normal keyboard
shortcut system, including Enter-key shortcuts.

For a build containing punctuation but no heavier LLM backend, install JDK 17+
and the Android SDK (platform 34 and build-tools 34.0.0), then:

```sh
git clone --branch feature/on-device-voice-typing https://github.com/nivekmai/kinetica.git
cd kinetica
python3 tools/voice-smoke/prepare_punctuation.py
./gradlew -PvoicePunctuation=true assembleDebug test lint
```

The preparation script downloads and verifies pinned public model/runtime files.
They stay in ignored `build/`; the resulting APK bundles them and does not need a
runtime punctuation-model download. Output: `app/build/outputs/apk/debug/app-debug.apk`.
It installs as Kinetica Voice DEV alongside upstream. Locally built debug APKs use
your local debug signing key and may not upgrade another developer's DEV install.
See [VOICE_TYPING.md](VOICE_TYPING.md) for the optional llama.cpp build and full
validation history. Ordinary builds without either voice build flag still provide
Android speech recognition, but do not bundle the punctuation model or LLM backend.

## Copyable implementation prompt

> Build local Android dictation using Android's on-device SpeechRecognizer for
> words and Edge-Punct-Casing's small int8 ONNX model through sherpa-onnx for
> punctuation and casing. Keep a heavier generative LLM optional and disabled for
> the baseline. The punctuation model is a trained classifier with no system prompt.
> Keep microphone capture independent of inference. Insert recognition immediately,
> retain its original text, and reconsider text across recognition pauses, including
> the current partial, rather than treating every utterance as a finished sentence.
> Use bounded preceding context, one background inference at a time, and only the
> latest pending request. Apply results only to the app-owned dictation span when
> their source/context/generation still match. Require exact word and number
> preservation; transfer only punctuation and casing. Protect names, acronyms,
> abbreviations and existing structure. Hide provisional trailing punctuation while
> listening. Add a small deterministic pass for unwanted mid-sentence capitals and
> comma spacing, with editable protected words. Provide pause/resume, visible model
> status, Undo, and opt-in local audio/event diagnostics. Test pause continuations,
> changed partials, stale results, editor changes and word fidelity. Benchmark on
> the target phone; do not assume desktop timings or punctuation accuracy transfer.

## What each layer does

| Layer | Role | Important limit |
| --- | --- | --- |
| Android on-device speech recognizer | Audio to words, with partial/final callbacks | Provider-dependent availability and recognition errors |
| Edge-Punct-Casing via sherpa-onnx | Predict casing, commas, periods and question marks across pauses | No prompting, spelling correction, list layout or semantic rewriting |
| Fast formatting | Normalize stray mid-sentence capitals and comma spacing immediately | Heuristic; protected-word list helps preserve names |
| Optional local LLM | Richer cleanup, such as list layout and contextual word corrections | Slower; can make mistakes, and is independently switchable |

The punctuation model has 7,490,500 bytes of weights plus a 149,430-byte vocabulary;
the native runtime adds further APK size. The app considers up to 6,000 characters
of uncleaned dictation and 40 preceding context words. The native implementation
internally batches up to 200 subword tokens, so this is not unlimited model context.
Updates are scheduled at most every 250 ms, with stale results rejected. After
manual typing, cursor movement or an editor change, old jobs must not overwrite
the user's text. Some punctuation predictions remain wrong or missing.

Key implementation files are under
[`app/src/main/java/com/kinetica/keyboard/voice`](app/src/main/java/com/kinetica/keyboard/voice):
`VoiceSession`, `VoiceDraft`, `LocalPunctuation`, `PunctuationText`, and
`FastVoiceFormatting`. The native wrapper is
[`PunctuationBackend.kt`](app/src/punctuation/java/com/kinetica/keyboard/voice/PunctuationBackend.kt).

## Licensing

The publisher lists **Edge-Punct-Casing under Apache-2.0** in its
[model card](https://huggingface.co/frankyoujian/Edge-Punct-Casing), and the
[source repository](https://github.com/frankyoujian/Edge-Punct-Casing/blob/main/LICENSE)
contains the Apache 2.0 license. It allows commercial use, modification and
redistribution subject to its terms, including retaining the license and applicable
notices and identifying modifications. It does not require an application's own
code to be open source merely because it uses this model.

This Kinetica fork's application code remains **GPL-3.0-or-later**; copying it is
separate from independently implementing the architecture or using the model.
sherpa-onnx and its bundled dependencies have their own licenses. See
[`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES), which also ships
in the app's open-source licenses screen.
