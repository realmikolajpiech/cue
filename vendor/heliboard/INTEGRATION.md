# HeliBoard in Cue

Vendored from https://github.com/HeliBorg/HeliBoard, tag `v4.0`, commit
`bd48798b99cccc99704eebf2a9259c02dbd684d5`. The complete runtime source,
resources, native dictionary engine and bundled dictionaries are in `android/src/main`.
Copyright notices and upstream license files are retained. HeliBoard is GPL-3.0-only;
AOSP portions also carry Apache-2.0 notices and its icon carries CC-BY-SA-4.0.
Distributing the combined application requires complying with those licenses,
including making its corresponding source available under GPL-3.0.

Cue-specific adaptations:

- Build as an Android library, with the project's Kotlin 2.3.20 and NDK 28.2.
- `App.initialize(Application)` and `CueKeyboardInitializer` initialize upstream
  singletons without replacing Expo's Application. First use enables Polish and key borders.
- The library manifest exposes internal settings, emoji and clipboard components;
  Cue registers the single IME service. Provider authorities use the host application ID.
- `SubtextKeyboard` subclasses `LatinIME` and adds Cue's suggestion panel to
  `main_keyboard_frame`. Typing, composing text, correction, emoji, key popups,
  cursor movement, sizing and system insets remain managed by HeliBoard.
- No proprietary glide-typing library is bundled.

The Expo config plugin `plugins/withSubtext.js` registers the library on prebuild.
