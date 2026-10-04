# Cue

An AI keyboard that uses the context of your available conversations to help you find your own words — in everyday small talk, a difficult conversation or flirting. It remembers details and agreements and takes your style and intent into account; you review, edit and send the reply yourself.

**HackYeah · Undefined · Mikołaj Piech and Marcel Chudyba.**
[Product overview](docs/PRODUCT.md) · [PDF presentation](artifacts/cue/Cue-HackYeah.pdf) · [Demo](docs/DEMO.md) · [Cue evaluation](benchmarks/cue/README.md) · [Resources and authorship](docs/RESOURCES.md)

No Matrix server, VPS or running Mac is required. Internet access is needed for
messengers and AI. The release build bundles the JavaScript and does not need Metro.

## Getting started

### Requirements

- Node.js 22.13 or newer and npm.
- Android Studio with Android SDK installed.
- JDK 17.
- An Android phone or emulator running Android 8.0 or newer, using ARM64 (`arm64-v8a`).

### Setup

1. Clone this GitHub repository.
2. Open a terminal in the project's root directory.
3. Install dependencies:

   ```sh
   npm ci
   ```

4. Prepare your device:
   - **Phone:** enable USB debugging, connect it via USB and accept the debugging authorization.
   - **Emulator:** create an ARM64 virtual device in Android Studio's Device Manager and start it.
5. Build and launch the app:

   ```sh
   npm run android
   ```

6. Select your phone or emulator if prompted. Leave the development server running in the terminal.

To build and install a version that runs without the development server:

```sh
npm run android -- --variant release --no-bundler
```

### First use

1. Follow the onboarding.
2. Optionally enable cloud AI analysis using the onboarding switch.
3. Set up the Cue keyboard directly from onboarding:
   - Open keyboard settings and enable Cue.
   - Return to the app and select Cue for typing.
   - Try it in the test field.
4. Choose **“Connect a messenger”** and connect Messenger, WhatsApp or Instagram.

AI and keyboard setup can also be completed later in the app's settings.

### Notes

- Expo Go is not supported because the app uses custom native modules.
- x86/x86_64 emulators are not supported by the bundled native libraries.
- Internet access is required for messaging connections and AI.
- The AI provider key is stored on the backend; reviewers do not need to enter it.

In Android Studio, open the generated `android/` directory, not the repo root.
Custom native code lives in `modules/subtext`, configuration in `app.json`
and `plugins/withSubtext.js`; do not edit the generated project by hand.
The repo includes an AAR from the Arie project. Details and sources: [NOTICE](modules/subtext/NOTICE.md).

## AI backend

Store the DeepSeek key only in Supabase → Edge Functions → Secrets, as
`DEEPSEEK_API_KEY`. The app uses the project's public key and an automatic
anonymous Supabase Auth session; enable **Allow anonymous sign-ins** in
Authentication → Sign In / Providers. Device session tokens are encrypted with
Android Keystore. Sessions are not used to store conversations.

Backend: `supabase/functions/deepseek-analyze`. It verifies the session, limits
request size and calls DeepSeek. Conversations and responses are neither stored
in the database nor logged by the function. A private table stores only
counters: 500 analysis attempts per day per session and 1000 per project;
counters older than 7 days are purged on subsequent calls. Limits also count
failed provider calls. The global limit caps cost even when new anonymous
sessions are created; it is not a substitute for DoS protection.
The optional `DEEPSEEK_MODEL` secret changes the model (default `deepseek-flash`).

Deployment after logging in to the CLI:

```sh
supabase link --project-ref qajdybynwafehizuaxad
supabase db query --linked --file supabase/migrations/20261003173000_ai_quota.sql
supabase functions deploy deepseek-analyze --project-ref qajdybynwafehizuaxad --use-api
```

`verify_jwt = false` disables the legacy gateway validation; the function itself
verifies the user token through Supabase Auth. The public key alone does not
grant access to paid analyses. Never add secrets to the repository or the APK.

## Architecture and scope

- Expo SDK 57 + Expo Router: onboarding, conversation list, profile, connections,
  settings; TanStack Query and Zod contract validation.
- `modules/subtext`: Kotlin, Go libraries from Arie/MirrorMsg, local storage,
  connections, DeepSeek and `InputMethodService`.
- Messenger uses messagix/mautrix-meta, WhatsApp uses whatsmeow.
  Stored sessions are restored after a restart.
- A foreground service keeps connections alive and tries to resume them. Android
  may restrict background work; after a force-stop the app must be reopened.
- Every conversation has its own identifier that includes the messenger. People
  with the same name are not merged automatically. The current flow supports
  private conversations; groups are skipped.
- Locally: up to 150 conversations, 200 messages each, up to 4000 characters per
  message, and the latest profile. Storage is in the private `noBackupFilesDir`,
  without an additional content encryption layer. Messenger encrypts cookies with
  Keystore; WhatsApp and E2EE state use the bridges' private databases.
- Each private chat has persistent memory in `subtext-person-memory.json`,
  separate for Messenger and WhatsApp. It survives restarts, trimming history to
  200 messages and eviction of an inactive chat from the 150-conversation cache.
  Disconnecting an account or clearing history also removes the memory. The demo
  has separate memory for a synthetic conversation and does not affect the
  general style.
- Sync appends only new message IDs: it counts your own samples, frequent
  phrases (at least 3 uses), style traits from the last 60 samples, and keeps
  authentic examples, including longer replies. The deduplication window holds
  up to 4096 ID hashes; once trimmed, older messages are skipped by a time
  threshold so re-syncing does not inflate statistics.
- With AI enabled, memory updates after new messages and about 3 seconds of
  quiet. Chats excluded from AI are skipped. An error keeps the previous memory;
  the minimum retry interval is one minute. Cost limits are enforced on the
  backend, and a quota error pauses attempts until the next UTC day.
- Reply analysis is on demand. DeepSeek receives up to the 80 latest stored
  messages, the draft and that chat's memory only; for replies also up to 3
  available photos from the last 12 messages. Context changes are incremental,
  with evidence IDs; no changes does not remove previous entries.
  Model: `deepseek-flash`, JSON output, thinking mode disabled.
- Profiles: summary, observations and agreements with source message IDs, a
  reminder before replying, one to three variants including the option not to
  reply. Sources of new entries keep the quote, author and date; manual
  corrections are marked. They are not a personality diagnosis. The validator
  rejects claims without available sources; having a source does not guarantee
  a correct interpretation.
- The keyboard requires explicitly choosing a conversation. It does not read the
  Messenger screen via accessibility. Suggestions are disabled in password fields
  and in no-personalization fields.
- This iteration targets Android. The iOS implementation developed in parallel
  requires separate verification; web and Expo Go do not support the native
  integrations.
- Integrations are unofficial, and available history depends on the service and
  session. The app does not guarantee fetching the full archive or continuity
  after a force-stop.

## Verification

```sh
npm run lint
npm run typecheck
npm run test:cue
npm run benchmark:cue
./android/gradlew -p android :subtext:testDebugUnitTest
./android/gradlew -p android :subtext:connectedDebugAndroidTest
```

Full acceptance requires the user to log in to both messengers and test real
sync, session expiry, returning from background, and the keyboard.
Tests do not send messages to contacts.

The repo contains only the current Cue product. The previous project, local
model and its benchmarks can be restored from Git history and the
`codex/backup-before-sync-20261003` branch.
The technical identifiers `com.mikolajpiech.guardian`, slug and scheme `guardian`
are kept for compatibility with the installed app and its data.

### HeliBoard keyboard

The Cue keyboard is built on HeliBoard 4.0: typing engine, Polish dictionary,
autocorrect, emoji, clipboard and appearance settings. A Cue suggestion panel
sits above the keyboard. The first launch selects the Polish layout and key
borders; later launches keep the user's settings.

Code and licenses are in `vendor/heliboard`, and adaptation details in
`vendor/heliboard/INTEGRATION.md`. The integration is regenerated by the
`withSubtext` config plugin during `expo prebuild` and requires a new native
Android build.
