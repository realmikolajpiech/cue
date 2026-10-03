# Inference latency investigation

System warnings are posted directly from Kotlin after a completed, validated assessment and history persistence. The foreground status poller does not schedule or delay warnings. Current `GuardianInference.analyze` performs one assessment; the engine is kept warm while monitoring. The saved v2 quality benchmark is historical and cannot establish latency of the current prompt.

`InferenceLatencyTest` is an opt-in comparison using four synthetic development conversations, separate from `cases.json`. It evaluates default CPU threading, two threads, six threads, and default threading again to expose warming, thermal and competing-work effects. It keeps the model bytes, prompt, schema, context and decoding settings fixed, compares all validated enums including signal order, and closes each engine before opening another. It never connects a notification listener or accesses the user's history. An engine accepts an explicit thread count for this experiment; production still uses the native default.

With an authorized Android device and the already licensed local artifact:

```sh
adb shell mkdir -p /data/local/tmp/guardian-latency
adb push models/gemma3-1b-it-int4.litertlm /data/local/tmp/guardian-latency/gemma3-1b-it-int4.litertlm
adb shell chmod 755 /data/local/tmp/guardian-latency
adb shell chmod 644 /data/local/tmp/guardian-latency/gemma3-1b-it-int4.litertlm
cd android
# Use Java 21 when needed by the local build environment.
./gradlew :guardian:connectedDebugAndroidTest -PreactNativeArchitectures=arm64-v8a \
  -Pandroid.testInstrumentationRunnerArguments.class=expo.modules.guardian.InferenceLatencyTest \
  -Pandroid.testInstrumentationRunnerArguments.gemmaModelPath=/data/local/tmp/guardian-latency/gemma3-1b-it-int4.litertlm
cd ..
adb exec-out run-as expo.modules.guardian.test cat no_backup/guardian-latency-threads.json > /tmp/guardian-latency-threads.json
node scripts/check-inference-latency.mjs /tmp/guardian-latency-threads.json
adb shell rm /data/local/tmp/guardian-latency/gemma3-1b-it-int4.litertlm
```

The report contains only synthetic assessments, device/configuration identifiers and timing/memory numbers. A candidate must preserve all assessments and improve per-case warm latency against both default rounds before adoption. Four fixtures do not prove general model accuracy, battery savings or performance on other devices. Notification delivery includes queue wait and Android banner scheduling in addition to model execution; this experiment measures engine initialization and full warm `analyze()` calls, not screen display latency.

3 October 2026: test APK compilation and 21 JVM tests passed. Hardware measurements remain unverified because the S22 disconnected before the model could be copied to the isolated test location. No faster production setting has been selected without measurements.
