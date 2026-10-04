# Cue on iOS

Paused work in progress. The iOS plugin and native module registration are disabled
in the active app configuration. Android remains the supported platform.
To resume, register `./plugins/withCueIOS`, enable `ios.enableSceneSupport` in
`expo-build-properties`, register the module's Apple platform, and restore the
iOS native-module gate in `src/services/subtext.tsx` before building.

The app has a Swift Expo module for Messenger and WhatsApp, and a separate UIKit
keyboard extension. Android continues to use its existing Kotlin module and keyboard.
The checked-in Go XCFramework has arm64 iPhone and Apple Silicon simulator slices.

## Run

```sh
npm install
npx expo prebuild --platform ios --no-install
cd ios && pod install && cd ..
npx expo run:ios
```

The Expo plugin generates the keyboard target, embeds the extension, copies its
sources and mascot, and sets App Group and shared Keychain entitlements. Generated
`ios/` files are disposable; change `plugins/withCueIOS.js`, `extensions/cue-keyboard`,
or `modules/subtext` instead. Expo Go cannot load this module or the extension.
The SDK 57 build-properties plugin enables scene support for current iOS releases.

On an iPhone, enable Cue in Settings → General → Keyboard → Keyboards → Add New
Keyboard. Enable Allow Full Access for conversation cache and AI access, then hold
the globe key to switch to Cue. Typing does not require Full Access or a network.
AI also requires the user to enable cloud analysis in Cue. No keystroke telemetry
or automatic message sending is implemented.

The keyboard keeps the typing keys visible while searching people or showing
suggestions. Search input is local to the person picker. Suggestions insert at the
current cursor or replace explicitly selected text; they never erase an assumed
whole draft. A changed cursor, selection, text, or input document invalidates the
suggestion. Undo is available only while the inserted suffix is fully readable and
unchanged. Secure fields, phone pads, and apps that refuse third-party keyboards
use the system keyboard, as required by iOS.

Open Cue to sync fresh messages. iOS does not run the Android persistent connection
service. Account sessions live in device-protected storage; conversation cache and
memory are coordinated across app and extension through the App Group. Network AI
results are rejected if cloud access is disabled, history is cleared, the selected
tone changes, or the conversation changes during analysis.

Real-device signing requires an Apple development team with the App Group and
shared Keychain capabilities for both targets. The plugin declares the keyboard
in Expo's EAS `appExtensions` config so EAS can provision both targets. A simulator
build does not prove App Store eligibility or live service authentication.

## Check and rebuild

```sh
npm run lint
npm run typecheck
swift test --package-path modules/subtext
npx expo prebuild --platform ios --no-install
node scripts/ios/test-plugin.cjs
# With an Apple Silicon iOS simulator booted:
python3 scripts/ios/run-keyboard-smoke.py <simulator-udid>
# Rebuild the vendored Go framework only when bridge sources change (Go 1.26+):
BRIDGE_PLATFORM=ios bash scripts/bridges/build-messaging-bridges.sh
```

The default bridge build still outputs the Android AAR. An iOS build never writes
that AAR. Both build modes pin the same upstream commit and apply the checked-in
patches. See `modules/subtext/NOTICE.md` for upstream licensing and provenance.
