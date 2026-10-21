# Navigation layer

Swipe upward on Space and release to replace the keys with navigation controls.
The gesture inserts no space. Horizontal Space sliding still moves the cursor;
once a gesture has become a horizontal slide it cannot also open navigation.
An upward gesture that is too short or retracted is consumed without opening the
layer. Existing Space chords, ordinary taps and double-space behavior remain.

The layer stays open until **ABC · Keyboard**, Back (when delivered to the IME),
keyboard dismissal, editor restart/change, or opening another keyboard panel.
It occupies the keyboard's existing height; the suggestion strip stays reserved
but inactive. It follows the active theme and keyboard resizing.

| Control | Behavior |
| --- | --- |
| Arrow D-pad | Left/right and up/down using the editor's native movement |
| Word ← / Word → | Ctrl+Left / Ctrl+Right word-boundary movement |
| Line start / Line end | Home / End, without Ctrl |
| Select / Selecting | Toggle extending the selection with arrows and jumps |
| Copy / Cut / Paste / Select all | Standard editor clipboard/selection actions |
| Undo / Redo | Ask the editor to undo or redo |
| ABC · Keyboard | Return to typing, retaining the current selection |

Hold a movement button for repeated movement. Releasing, cancelling, dragging
outside it, or closing the layer stops repetition. Selection adds Shift only to
each movement key pair; it never leaves a modifier key held in another editor.
Clipboard and undo/redo actions switch the selection toggle off. Switching it off
does not itself remove the editor's highlight; ordinary movement collapses it,
and typing or pasting replaces it as usual.

Navigation settles the pending typed word and cancels automatic spacing. Cursor
callbacks while navigating do not reopen words for autocorrection. Entering the
layer also closes voice capture and invalidates its outstanding edits.

The target editor interprets the events: word boundaries, wrapped-line behavior,
selection, clipboard permissions and undo support can differ in web/custom
editors. There is no document-wide text extraction or clipboard history. See
Android's [movement key definitions](https://developer.android.com/reference/android/view/KeyEvent#KEYCODE_MOVE_HOME)
and [InputConnection key delivery](https://developer.android.com/reference/android/view/inputmethod/InputConnection#sendKeyEvent(android.view.KeyEvent)).

## Manual phone acceptance checks

1. In a multiline notes field, type `one two three`, swipe up from Space and lift.
   Expect the navigation layer and no appended space. Return with ABC; ordinary
   Space taps and left/right slides should still work. Repeat from symbols mode,
   with double-space enabled, and with a configured Space chord.
2. On three lines of text, tap each arrow, then hold it. Expect one movement per
   tap and repetition only while held. Drag outside, release, press Back, dismiss
   the keyboard and switch fields while holding. Expect no continuing movement
   after the layer closes and no selection modifier in the next field.
3. Put the cursor inside a word; use both Word buttons, then Line start/end on a
   long wrapped paragraph. Expect editor-native word boundaries and visual line
   ends where supported. Repeat with emoji and right-to-left text.
4. Toggle Select on, move right three times and left once, then jump by word and
   line. Expect selection extension and contraction from the editor's anchor.
   Toggle off and move; expect normal movement. Copy a selection, move elsewhere
   and paste. Select all, cut, undo and redo in an editor that supports them.
5. Return to the keyboard with a selection and type: expect selection replacement
   with no unexpected automatic space or correction. Move between two fields and
   rotate/fold the phone: the navigation layer should not persist into a new input
   session. Check dark/light themes and resize using the keyboard handle.
6. Enter navigation during dictation/cleanup. Expect the microphone to stop and
   no late voice result to overwrite subsequent cursor or clipboard edits.

Automated coverage checks gesture axis locking, vertical jitter/retraction,
Space-chord cancellation, double-space/spaceless interaction and movement modifier
mapping. Actual touch rendering and editor behavior require the phone checks above;
no emulator test is claimed.

## Development build

This is DEV v17, version `2.0.16-voice-dev` (90), with the same package as v16
(`com.kinetica.keyboard.dev`). The distributed arm64 build retains both optional
voice backends and existing voice preferences. Build it with:

```sh
python3 tools/voice-smoke/prepare_punctuation.py
git submodule update --init third_party/llama.cpp
./gradlew -PvoiceLlm=true -PvoicePunctuation=true assembleDebug test lint
```

The navigation layer itself needs neither model nor microphone access and is
included in ordinary builds too.

Final native-enabled `assembleDebug test lint` passed in 3m30s: debug and release
each ran 1,188 tests, zero failures/errors and two existing skips; lint zero errors.
The APK signature matches v16, and the original small punctuation model hash and
both voice backends were verified in the package. Physical-device acceptance
checks remain open.

Artifact: `kinetica-dev-v17-navigation-arm64.apk` (53,286,148 bytes).
SHA-256: `44a0ddcb3a6df010c6b7a7c7d033e2ca9c583e27d0239a3c0f7be5f4fcd7739e`.
