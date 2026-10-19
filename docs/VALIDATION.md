# Validation record — 0.4.0 experimental, 2026-10-16

## Current OCR milestone (0.4)

- Local JDK 17 rebuild, core tests, `:app:assembleDebug`, and Android lint completed successfully after configuring the machine's installed Android SDK in ignored `local.properties`. Gradle emitted a non-fatal legacy SDK XML/toolchain warning.
- Core results: **40 tests / 0 failures / 0 errors** (19 engine, 10 legacy parser, 11 OCR engine). The results are collected under `evidence/core-tests.xml` and `evidence/core-test-suites/`.
- Connected Android 16 (API 36) test results: **32 tests / 0 failures / 0 errors / 0 skipped** on the existing isolated `GhostMagic_Validation` emulator, including **10 image OCR tests** and **2 screenshot permission/thread checks**. See `evidence/android-tests.xml`.
- `tools/package.ps1` succeeded and produced `dist/Ghost-Magic-Android-0.4.0-experimental.apk` (46,502,102 bytes), with SHA-256 `06CF2DF62AF9C0B1E8DC9750D051787408540CC4192104B57A56D5C5EABACB50` stored in the adjacent `.sha256` file. This remains debug-signed.
- OCR image recognition, cropping, rotation, blank result, content-provider bounds, failure routing and engine integration are covered by instrumentation tests. Screenshot tests cover negative permission/thread paths; they do not enable Accessibility. A separate earlier Codex smoke artifact (`evidence/ocr-screen-result.txt`) records a successful screenshot OCR trace, but this verification did not independently repeat that interactive screen-capture smoke.
- No physical-device/OEM compatibility, Thai script recognition, complete legacy `.flows` execution, or production-readiness is claimed. See `OCR_ACTION.md` for privacy and lifecycle limits.

## Prior 0.3 baseline

This baseline covers the independent `native` implementation, not the earlier re-signed original APK. There is no claim of full Automagic parity.

## Verified

- JDK 17 core suite: **29 tests**, no failures. The 19 engine tests cover graph validation before side effects, branching, variables/templates, typed errors, cancellation, timeout, cycles, budgets and triggers. Ten legacy-reader tests cover shared objects, reference diagnostics, unknown fields, fan-out, DTD rejection, CDATA/comment handling, UTF-16, limits and version reporting. The original 0.3 aggregate is archived at `evidence/0.3.0/core-tests.xml`; current suite results under `evidence/core-test-suites/` correspond to 0.4.
- Isolated Android emulator: **Android 16, API 36**, x86_64, API 36.1 Play Store image, AVD `GhostMagic_Validation`. **20 integration tests**, no failures. The 0.3 result is archived in `evidence/0.3.0/android-tests.xml` (the top-level `evidence/android-tests.xml` now reflects 0.4).
- Android integration tests cover JSON graph round trip including positions, unknown actions/dangling edges rejection, size/schema rejection, atomic storage/save/delete with existing data retained, real battery adapter, launch → open graph → Run through the UI, notification access denial/error recovery followed by actual notification posting, brightness denial followed by actual settings read-back and adaptive-mode rejection.
- Scheduling integration tests additionally cover persisted periodic JobInfo, flex interval, inactive imports, generation/revision fencing, disable, foreground-only action rejection, one shared active run, journal persistence, cancellation, busy SKIPPED status, reservation of busy occurrences, duplicate suppression, preserving reservation on job restoration, and admitting a later interval using elapsed time despite a changed wall clock.
- Six legacy integration tests exercise Android's actual SAX implementation and archive storage: no native flows/jobs created, byte-exact UTF-16/comment/CRLF preservation, deduplication, DTD/malformed-input rejection, checksum tamper refusal and recovery when metadata is absent.
- `:app:assembleDebug` and `:app:lintDebug` pass. Lint has zero errors; remaining warnings concern prototype localization, layout-editor constructor, dependency freshness and explicit backup rules. Raw lint output is copied to `evidence/lint.txt`.
- APK identity/signing details and SHA-256 are recorded alongside the packaged artifact.
- The background service/engine implementation retains the 0.2 baseline for the separate forced-delivery, timeout, process-kill and reboot smoke. That baseline is preserved under `evidence/0.2.0/` and identified by version in the new build record; it is not presented as a freshly repeated 0.3 reboot test. Scheduling instrumentation was rerun with 0.3.
- The 0.3 SAF smoke uses the actual Android document picker to import the synthetic `.flows` asset, open its report, export original bytes and a report, and reopen the library after process restart. It checks source SHA-256, an unknown plugin entry, identical original/exported bytes, unchanged runnable flow IDs and zero active jobs. Results are in `evidence/legacy-smoke.json`, with screenshots and exported samples alongside it.
- The same smoke also imports a synthetic native JSON periodic flow through the refactored asynchronous path, verifies a new flow ID and imported name, and confirms no job is activated.
- The older 0.1 manual battery-branch smoke and build evidence are preserved under `evidence/0.1.0/`; its source/behavior scope is distinct from this new background milestone.

The Android regex engine rejected an unescaped closing brace that desktop Java accepted. This was fixed and the complete Android suite passed afterward. This is evidence for requiring runtime verification rather than trusting a desktop suite alone.

## Limits

- No physical device/OEM validation. Min API 26 is a declared/build baseline, not a tested API 26 result. Android 13–15 devices and any newer stable Android release still require their own test runs.
- No real Automagic export has been supplied for this milestone. Legacy fixtures are synthetic and explicitly labeled. No parameter conversion, original execution-policy parity or executable legacy import is claimed. Preserving original bytes is distinct from proving a future converted flow behaves identically.
- Natural 15-minute deliveries under Doze/standby, Android quotas, clock changes across reboot and physical-device reboot reliability still require long-running tests. The explicit timeout and process-kill cases establish bounded interruption behavior, not seamless resume or exactly-once effects.
- Wi-Fi panel opening does not prove a toggle, association or Internet access. No direct Wi-Fi toggle is implemented.
- The brightness test verifies the system setting on the emulator, not physical luminance on OEM devices. Permission revocation races and all manufacturer-specific behavior remain unverified.
- Android document-provider export/import UI has not received exhaustive provider, rotation and external-drive testing. JSON serialization/import validation and internal persistence have instrumentation coverage.
- Screenshots are observations of the new app, not reconstructed original screenshots. The UI remains an experimental English interface with a custom graph canvas and an alternate Nodes list.
- AGP/Gradle 8.2.1 reuse the existing local toolchain; compile SDK 36's older-plugin warning is suppressed. Upgrade to a toolchain officially supporting the selected SDK before a production release, then rerun the matrix. The successful emulator results do not remove this build-maintenance debt.
- Platform test runner is a test-only legacy Android runner to avoid adding production UI libraries; modernize the test harness before growing the suite substantially.

## Next acceptance work

1. Obtain real `.flows` exports and test selected flows against the original app, recording expected branches/variables/side effects as fixtures.
2. Validate natural periodic behavior and add selected event triggers; design durable node resume and additional concurrency policies separately.
3. Validate legacy XML parsing and migration reports on real `.flows` exports; unknown nodes must remain explicit.
4. Run the version/OEM matrix in `COMPATIBILITY.md` before declaring general modern-Android support.
