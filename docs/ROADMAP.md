# Ghost Magic Android reconstruction roadmap

The project is a new automation app inspired by Automagic's trigger/condition/action flow model. The earlier APK with a changed label remains a reference artifact; it is not the reconstruction. The native source tree is the new implementation. No milestone below implies full Automagic feature parity.

## Milestone 1: inspectable manual core

Deliver source and a reproducible APK with a flow list and graph UI, manual start, editable nodes and edges, variables, conditional branches, a bounded execution engine, delay, notifications, system brightness with special-access handling, and a clearly named Wi-Fi panel action. Persist flows locally; support a versioned Ghost Magic JSON document through Android's file picker. Show a per-node execution trace and provide cancellation, including cancellation during a delay. Keep execution in the visible application lifecycle for this milestone and explain that limitation in the app.

Acceptance gates:

1. Create and edit a flow containing a variable, a condition and distinct true/false actions; run both branches and inspect the trace.
2. Save, restart the app and load the same graph. Export and reimport it without losing node configuration or connections.
3. Reject malformed graphs, missing edge targets and invalid configuration before side effects; bound repeated execution to prevent runaway cycles.
4. A blocked notification/brightness permission is visible and does not produce a misleading success result. Opening a Wi-Fi panel is recorded as a request for user interaction, not a successful Wi-Fi toggle.
5. Cancellation prevents subsequent nodes from running; a completed side effect is not presented as rolled back.
6. Verify the engine independently of Android adapters, then test Android permission and lifecycle behavior on emulator/device. Record actual OS/device versions and outcomes. A build without runtime testing must be labeled unverified.

## Milestone 2: durable runtime and selected triggers

0.2 implements a bounded first slice: ordinary persisted periodic jobs, separate activation state, generation/revision admission checks, one shared active run, durable journal and interruption detection, stop controls and interval-window duplicate suppression. This does not complete milestone 2. Natural Doze/quota/OEM behavior, broader events, queue/parallel policies, and durable node-level recovery still need design and validation. See [the actual runtime contract](BACKGROUND_RUNTIME.md).

Design durable execution state, duplicate-event protection, concurrency rules, structured errors and restart policy before enabling background triggers. Add selected triggers one at a time: time scheduling, charging/battery and other documented events chosen from real user flows. Use WorkManager for deferrable work and AlarmManager only where timing semantics require it. Choose any foreground service type against its actual workload and modern start/timeout constraints. Include stop controls and observable status. Validate Doze, process death, permission revocation and reboot. See [compatibility matrix](COMPATIBILITY.md).

## Milestone 3: legacy flow migration

0.3 implements the archive-and-inspection slice: source-preserving XML import, bounded secure parsing, shared-object/reference diagnostics, read-only inventory/tree inspection and source/report exports. Fixtures are synthetic; no executable translation or real-export semantic parity is claimed. See [legacy import boundary](LEGACY_IMPORT.md). Real fixtures and per-capability converters remain required.

Use static APK/resource evidence and user-provided flow exports to document the original schema and action semantics. Build a parser and migration report before allowing execution. Map a small verified subset first, preserve unsupported nodes and explain replacements. Test against fixtures with branches, variables, action parameters and unknown node types. Do not advertise complete legacy compatibility based on parsing a single example.

## Milestone 4: expand capability adapters

Prioritize action families from real flows and the compatibility matrix. Add user-assisted Bluetooth/mobile-data settings, notification events and accessibility only with explicit capability states and clear setup. Explore a separately activated privileged bridge only for actions that need it; document its real privilege and device/version limits. Test every adapter independently; do not promise old direct toggles will become available merely by raising the target SDK.

## OCR action slice (0.4 experimental)

Independently implement `action.ocr.read` from focused Macrorify OCR reference observations: selected-image reading, explicit percentage crop/rotation, Latin string output, typed errors, and optional screenshot-only Accessibility capture on Android 11+. Read [the actual action contract](OCR_ACTION.md) and [the static evidence boundary](MACRORIFY_OCR_STUDY.md). This is a capability addition, not completion of any legacy migration milestone. Empty text succeeds; character filters, numeric defaults, preprocessing beyond crop/rotation/downsampling, text matching, Thai script and reference parity remain unsupported. Keep real-device capture/permission testing and privacy controls as explicit follow-up work.

## Continuing release gates

Maintain a source-to-APK build record, signing identity, schema migrations, regression flows, action support matrix and actual device results. Keep reference observations separate from inferred behavior and new design decisions. Review [Android behavior changes](https://developer.android.com/about/versions) whenever raising compile/target SDK or adding support for a new stable Android version. Maintain honest per-action compatibility; future Android versions require validation rather than an unconditional support promise.
