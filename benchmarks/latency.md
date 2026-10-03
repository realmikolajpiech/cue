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
./gradlew :guardian:assembleDebugAndroidTest -PreactNativeArchitectures=arm64-v8a
cd ..
adb install -r modules/guardian/android/build/outputs/apk/androidTest/debug/guardian-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class expo.modules.guardian.InferenceLatencyTest \
  -e gemmaModelPath /data/local/tmp/guardian-latency/gemma3-1b-it-int4.litertlm \
  expo.modules.guardian.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as expo.modules.guardian.test cat no_backup/guardian-latency-threads.json > /tmp/guardian-latency-threads.json
node scripts/check-inference-latency.mjs /tmp/guardian-latency-threads.json
adb shell rm /data/local/tmp/guardian-latency/gemma3-1b-it-int4.litertlm
adb uninstall expo.modules.guardian.test
```

Install and run the test APK explicitly: `connectedDebugAndroidTest` removes the isolated test package at completion, including its saved report. Copy the report before uninstalling it. A validation error is recorded in each case rather than aborting the comparison. The candidate must preserve validated assessments and must not introduce errors; matching an existing failure does not establish correct classification.

The report contains only synthetic assessments, device/configuration identifiers and timing/memory numbers. A candidate must preserve all assessments and improve per-case warm latency against both default rounds before adoption. Four fixtures do not prove general model accuracy, battery savings or performance on other devices. Notification delivery includes queue wait and Android banner scheduling in addition to model execution; this experiment measures engine initialization and full warm `analyze()` calls, not screen display latency.

## Measured comparison — 3 October 2026

The [saved report](gemma3-1b-s22-threads.json) was collected on the reconnected Samsung Galaxy S22 (SM-S901B), Android SDK 36, with the current v3 prompt and unchanged model bytes. The main app was not replaced or stopped. The isolated test completed all 16 assessments and preserved exactly the same assessments and validation failures across configurations.

| CPU configuration | Mean warm analyze | Median warm analyze | Initialization including probe | Accepted schema/citation outputs |
| --- | ---: | ---: | ---: | ---: |
| Native default, first round | 15,706 ms | 14,876 ms | 21,457 ms | 1/4 |
| 2 threads | 17,069 ms | 17,082 ms | 20,651 ms | 1/4 |
| 6 threads | 13,549 ms | 13,610 ms | 15,099 ms | 1/4 |
| Native default, last round | 13,584 ms | 13,554 ms | 14,473 ms | 1/4 |

Six threads differed from the repeated default by only 35 ms in mean latency (about 0.25%) and were slower on one case. Two threads were slower overall. No faster production setting was selected. The difference between the two default rounds is much larger than the six-thread advantage, so the initial-round comparison alone would be misleading. These samples do not explain the user's earlier approximately five-second notification delay: that involved an earlier build/input and end-to-end delivery.

Acceptance is not accuracy: the harmless greeting (case 0) was incorrectly classified as high risk with `money_request`. The other three examples were rejected for citations absent from their input (`model_evidence_not_in_input`) in every round. The test verifies configuration equivalence, not protection quality. The existing v3 classification needs independent correction and evaluation before reliable warning behavior can be claimed; citation validation remains enabled.

Observed test-process PSS ranged from approximately 1.10 to 1.29 GiB. The phone's thermal status changed from 0 before the run to 1 during it; AP temperature was initially 34.7°C and observed at 44.8°C mid-run. Memory readings include the test process and allocator effects and do not establish steady idle usage or battery consumption. This is a sequential experiment with changing cache/thermal conditions, not a randomized power benchmark.

Test APK compilation, 24 JVM tests, this opt-in device comparison, four UI synchronization checks, lint and TypeScript checks passed. The report was copied before removing the isolated test package and temporary model from the phone. Production threading remains the native default.
