# Cue

An AI keyboard that uses the context of your conversations to help you find your own words. You review, edit and send every reply yourself.

**HackYeah · Undefined · Mikołaj Piech and Marcel Chudyba**
[Presentation (PDF)](artifacts/cue/Cue-HackYeah.pdf) · [Demo](docs/DEMO.md)

## Getting started

**Requirements:** Node.js 22.13+, JDK 17 and Android Studio with Android SDK.

1. Clone the repository and open a terminal in its folder.
2. Connect an **ARM64** Android phone with USB debugging enabled, or start an ARM64 emulator.
3. Run:

   ```sh
   npm ci
   npm run android
   ```

Select your device if prompted. Follow the onboarding to enable AI, configure the Cue keyboard and connect a messenger.

> [!NOTE]
> Keep the terminal open while testing. Expo Go and x86/x86_64 emulators are not supported. The AI key is stored on the backend, so you don't need to provide one.
