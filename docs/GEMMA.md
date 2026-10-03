# Gemma on Android

Guardian uses the portable **Gemma 3 1B IT INT4 LiteRT-LM** artifact. The source revision, exact byte size and SHA-256 are pinned in `models/catalog/gemma3-1b.json`; that same manifest is bundled as an Android asset. Weights are ignored by Git and excluded from APK assets.

## Obtain the model

1. Sign in at [the model repository](https://huggingface.co/litert-community/Gemma3-1B-IT) and personally review and accept the Gemma conditions. Access is gated; accepting in a browser does not authenticate the CLI.
2. Download `gemma3-1b-it-int4.litertlm` (584,417,280 bytes). `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` contains the same bytes and is also accepted. Do not choose `.task` or a chipset-specific NPU artifact.
3. Alternatively, configure a read-only `HF_TOKEN` locally or use an existing Hugging Face CLI login, then run:

```sh
npm run model:download
npm run model:download -- --push
```

The script verifies the size and SHA-256 before replacing a local file. `--push` copies it to Downloads on an authorized Android device. `ANDROID_SERIAL` selects a device. Credentials stay on the development computer; never paste tokens into chat or commit them. For a browser download, place the verified file at `models/gemma3-1b-it-int4.litertlm` to reuse the push command without a token.

## Install and run

Use an Android development build, not Expo Go. For local Gradle builds, use Java 21 (Java 25 caused a CMake build-tool error in this environment). In Ochrona, choose **Importuj model .litertlm** and select the downloaded file. Allow roughly 650 MB of free internal space for the private copy, in addition to the downloaded file and any existing installed model. Import pauses monitoring, verifies exact bytes, atomically replaces the file and initializes the CPU backend and checks a harmless assessment before declaring readiness. An invalid or interrupted import preserves the previous model. Initialization errors reject the import instead of reporting success. Inference remains entirely native; private messages never enter JS.

The engine validates and reloads the installed model when the app process starts. Pausing and loss of listener access close the engine. Enable notification access and warning permission yourself, then enable monitoring. Import and benchmark leave monitoring off.

Run the 40-case synthetic benchmark from Ustawienia. See [benchmark instructions](../benchmarks/README.md). Quality on Polish conversations and behavior in real messaging apps require actual measurements; installation alone is not acceptance of the protection pipeline.

## Native verification

```sh
cd android
./gradlew :guardian:testDebugUnitTest :guardian:connectedDebugAndroidTest
```

`GemmaInferenceTest` is opt-in: supply `gemmaModelPath` as an instrumentation runner argument pointing to a readable licensed artifact on the test device. It imports and verifies the actual model, runs a Polish smoke case and all 40 benchmark cases, then checks closing and reinitializing the engine. It does not grant notification-listener access or analyze private notifications.

## Measured baseline — 3 October 2026

The [saved report](../benchmarks/gemma3-1b-s22.json) was produced by the actual model on a Samsung Galaxy S22 (SM-S901B), Android SDK 36, CPU backend. The instrumentation test package has no INTERNET permission. All 40 outputs passed the JSON contract; 19/20 scams and 14/20 benign cases were classified high (recall 95%, precision 57.6%, false-positive rate 70%). Median latency was 11.6 s, p95 13.2 s, initialization 12.7 s, and peak observed PSS about 1.20 GiB. These results establish working offline inference, but fail the intended fast, reliable warning behavior. Do not tune against this evaluation corpus; use separate development examples and evaluate any revised model or prompt independently.

Native verification passed 13 JVM unit tests and 5 device instrumentation tests, including real model import, inference, the benchmark and closing/reinitializing the engine. Lint and TypeScript checks passed. Real notification delivery and the full permission/lifecycle flow still need acceptance on the device.

## Manual check

The Sprawdź tab sends user-entered text (up to 1500 characters) to the native CPU engine and displays a validated assessment. It requires an installed model, but no notification-listener permission or monitoring. Input and output are transient and are not added to history or sent to a server. User-entered text exists in the React Native input; automatically observed notification messages still stay entirely in Kotlin. Marcel's screen styling is retained, with loading, result and failure states replacing the placeholder preview.

## Notification monitoring in the main design

On Android, the main protection switch and Settings use the native monitoring state, and the Marcel history/detail layouts display actual sanitized native results. Web/iOS keep the labeled demo. The protection heading is active only when monitoring is enabled, Android notification access is granted, the listener is connected and the model is ready. The SMS/WhatsApp/Messenger/Beeper list describes the fixed native allowlist; it does not pretend unsupported application-selection controls work. System warning permission is separate from listener access. Monitoring analyzes newly posted notifications. After the listener reconnects with monitoring enabled, it also processes still-visible notifications posted in the last 15 minutes, once the engine is ready. It does not read past conversations inside another app. Beeper (com.beeper.android) is included in the allowlist. End-to-end acceptance still requires a new synthetic incoming notification from a supported app; the earlier CPU benchmark reports latency and false positives separately.

## Useful system warnings

The warning title names the suspected mechanism, rather than repeating a generic risk level. Collapsed text gives an immediate action appropriate to the category. Expanded text remains concise and repeats the actionable advice; the full analysis is reached through the button, without a wall of signal labels. The source app and “ocena AI” appear in the subtitle, and “Zobacz analizę” opens the saved result. No message quote, contact name or inferred signal is inserted. Wording describes a suspicion rather than a verified crime.

## Priority and risk colors

High-risk warnings use the urgent `guardian_high_risk` channel (IMPORTANCE_HIGH, sound, vibration) and PRIORITY_MAX for compatibility. Android settings and Do Not Disturb remain authoritative; visibility cannot be guaranteed. Settings links directly to this channel's banner/sound/vibration controls. A dedicated monochrome shield is used for the status-bar icon; the notification accent and large shield reflect risk (red high, orange medium, yellow low, gray uncertain). The app uses corresponding light/dark risk colors and retains textual risk labels. Its header shield reflects the highest unreviewed result, or the manual result currently displayed. Only high risks trigger automatic system warnings; medium/low colors are used for in-app results.

## Model decisions with cited evidence — v3

The v2 model overreported signals and produced false alarms even for a plain transfer request. In `guardian-pl-v3-evidence`, the model alone chooses risk, category and at most two relevant signals. For each signal it must return a short exact citation from the supplied conversation. Native validation checks the schema and that the cited text exists, then discards private citations before any result is stored or returned to JS. Each assessment uses one model conversation. There is no keyword classifier, per-category cue threshold or rule that replaces the model's risk verdict. Citation presence does not prove semantic relevance; the model can still misinterpret real text. A broken contract is an analysis failure, not a safe verdict. Fresh evaluation is needed; the saved report remains the historical v2 baseline.

Current results carry `analysisVersion`; earlier results remain stored but are presented as requiring reanalysis, with their unsupported labels hidden. Old active alerts are withdrawn rather than refreshed as reliable warnings. In-app explanations use at most two model-selected observations in plain sentences, while system alerts show only the suspected mechanism and next action. No private quote is persisted or returned to JS.
