# Macrorify 1.6.0.2: focused OCR / Text Reader study

This study informs an independently authored Ghost Magic action. It does not copy Macrorify application code, resources, native libraries, models, or signing material into Ghost Magic. Static analysis does not establish execution or behavioral parity.

## Target and evidence

User target: `C:/Project_winsurf/Automagics/Macrorify 1.6.0.2.apk+`. The selected base APK is package `com.kok_emm.mobile`, version 1.6.0.2 (218), min SDK 21 / target SDK 35, SHA256 `4e3e920fe46c0257d1663213d3dc0f66f510361f1c80e29fec9b20166882f5bd`.

REA skill version 42 was followed. Existing CLI evidence was reused:

- Container inventory: `ev_39c57d587493cc399e4541f8e5bf0e55aa3d1e3e758ef9806ff1fc653ea9ce32`, `evidence/macrorify-ocr/rea-container.json`.
- Explicit extraction: `ev_2a2b2dcc7b30947e5791859704a562c3fecb39053aedefbfa4e79715c02836ff`, `rea-extract.json`.
- Resource and manifest decode: `ev_4948a9691a921fb4d6dbce3f7706dcc97ca33ea6bdfad31e8f6845c357f8a159`, `rea-base-resources.json`. This reports partial coverage and 139 unsupported/malformed string entries; it intentionally skips smali.

REA MCP and JADX were unavailable. Supplemental focused smali reads came from a separate local Apktool 3.0.3 decode at `.runtime/macrorify-decoded`; these are **shell-derived evidence, not REA Java-source results**. `evidence/macrorify-ocr/static-anchors.json` records exact decoded paths, file hashes, line ranges, and small excerpts. No target execution was performed; supplied native splits are arm64 and the available validation emulator is x86_64.

## Findings ledger

| Question | Observation | Evidence / confidence | Remaining gap |
| --- | --- | --- | --- |
| User entry | String `i7` is Text Reader, public resource ID `0x7f130149`; `L6/a.1.smali:218` loads it in the action selection label switch. | REA resources + supplemental entry-resource anchor; observed static | UI was not opened in original app. |
| Output contract | Help strings `a27` through `a29` describe a string variable result, optional numeric conversion, and a configured default or null when conversion/read fails. | REA/resource ui-contract anchor; observed declaration | Full typed variable assignment and number parsing were not dynamically verified. |
| Region | `K3/U.k(Q8/d)` gets an image Mat through `O3/u.m`, supplies the region plus a double scaling setting to `O3/u.e`, then passes resulting Mat to OCR. | read-region-pipeline anchor; observed instructions | Exact crop coordinate rounding, rotation, clipping and native processing details remain unresolved. |
| Real app OCR caller | `K3/U.k` sets two character filter strings through `O3/w0.c/b`, calls `g(Mat)` then `d()`, wraps returned string in `r4/m`, and clears image state. `K3/r0` implements that interface. | read-region-pipeline + recognition-pipeline anchors; observed static call chain | Cannot prove every action execution route uses this path. |
| OCR engine | `K3/r0.d` lazily creates `t3/c` through `d3/a.X(u3/a.b)`, calls `t(InputImage)`, awaits its task, reads `r3/a.b` text. `u3/a` identifies the Latin module; bundled dependency metadata says text-recognition 16.0.0. | recognition-pipeline, latin-module, recognizer-version anchors; observed instructions/metadata | Dependency presence alone is not the basis of this conclusion: actual app caller is traced. No accuracy or offline behavior measurement. |
| Input adaptation | `K3/r0.g` converts OpenCV Mat through `O3/y.h` to Bitmap and constructs `p3/a` image wrapper. | image-conversion anchor; observed static | Exact color transform inside implementation not traced. |
| Character restrictions | `K3/r0.d` applies `K3/X.b(e,f).a(recognizedText)` after OCR. `X` builds quoted regex character classes; `X$a.a` removes matching characters using replaceAll with empty string. | recognition-pipeline + filter-implementation anchors; observed instructions | These are output filters, not evidence the engine was trained or constrained before recognition. |
| No text / error | Missing image, unsuccessful task, blank recognized text, or caught exception produce null in `r0.d`. Preview caller `A4/i` rejects blank returned text as an exception. | recognition-pipeline + preview-caller anchors; observed static | Do not infer Ghost Magic must silently collapse permission/model errors to no text. |
| Corrections | Resource `a23` describes replacement/include/exclude; `a24` simple replacement, `a25` regex, `a26` whitelist/blacklist. | REA resources; observed declaration | Replacement ordering and regex rules beyond the traced character filter are not established. |
| Preprocessing | Changelog resource `k4` mentions runtime scale, rotate, grayscale and threshold; `k0` advertises 50+ languages. | REA resources; observed product text only | Not proof those transforms run in Text Reader. Not proof Thai script recognition. |
| Screen capture | Manifest declares ScreenCaptureService, EditModeService and PlayModeService as mediaProjection foreground services and corresponding foreground service permissions. | REA manifest; observed declaration | Manifest is not proof current Android consent/lifecycle handling succeeds. |

## Interpretation and implementation boundary

A useful independently authored first action can accept an explicitly selected image, optionally crop a bounded region, recognize text on a worker, filter allowed/blocked characters, and write a configured flow variable. This design is inspired by observed input-region-output composition; its validation, cancellation, empty result handling and error routing must be defined for Ghost Magic independently.

Screen OCR requires a separate capture lifecycle and public Android permission flow. Reading image content and obtaining a screen image are separate capabilities; a library dependency does not supply capture permission. Foreground screen capture, OEM behavior, protected windows, and screen changes must be tested before claiming screen-reading automation.

No Thai OCR capability is established here. Thai translated UI resources do not identify recognition scripts. Numeric conversion/default semantics, arbitrary replacement rules, layout block ordering, preprocessing, dynamic resolution scaling and matching/click-on-text are outside the verified first-action contract. Macrorify text search and text-reader output must not be conflated.

## Checklist outcome

- Target identity and Text Reader resource entry: answered statically.
- Actual app-to-OCR call path, image adaptation, string output and character filtering: answered for the inspected path.
- Region and result semantics: partially answered; exact native crop and typed assignment unresolved.
- Preprocessing, Thai recognition, measured accuracy, current Android runtime parity: unresolved.
- Original target runtime testing: not performed.
