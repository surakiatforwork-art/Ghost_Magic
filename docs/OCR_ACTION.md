# OCR Text Reader action (0.4 experimental)

`action.ocr.read` is independently authored Ghost Magic code. The focused [Macrorify study](MACRORIFY_OCR_STUDY.md) supplied reference observations about region-based text reading; no reference DEX, resources, native code, or OCR model is included. The action uses the bundled Google ML Kit Latin text recognition SDK and public Android APIs. This document describes code behavior, not complete Macrorify parity or device certification.

## Configuration

All values are native flow string configuration fields. Existing `${variable}` interpolation runs before execution and runtime validation.

| Field | Values / meaning |
| --- | --- |
| `source` | `image` or `screen` |
| `imageUri` | Required for `image`; a `content://` image selected through Android's file picker. Ignored for screen. |
| `outputVariable` | Required variable name matching `[A-Za-z_][A-Za-z0-9_.]*`; reserved `error.code` and `error.message` are rejected. Existing value is replaced on success. |
| `script` | `latin` only. No Thai recognition support is claimed. |
| `region` | `full` or integer percentages `x,y,width,height`, wholly inside 0..100; width and height must be positive. Example `10,20,80,30`. |
| `rotation` | `0`, `90`, `180`, or `270` degrees clockwise; applied before the region. |

The adapter bounds the image's longest side to 2048 pixels, applies explicit rotation, then crops the percentage region and recognizes the result. Crop left/top round down and right/bottom round up within image bounds. Image decoding may initially downsample by a power of two. EXIF orientation is not automatically applied: use the rotation field for an image saved with orientation metadata. Recognition works on resulting pixels, so small text may lose detail after downsampling.

## Using an image

1. Add OCR Text Reader to a flow and edit the node.
2. Select **Choose OCR image**. The Android document picker accepts an image, and the app requests a persistent read grant. The selected URI is recorded in the node with `source=image`.
3. Set the output variable, region and rotation, apply the node settings, and save the flow.
4. Connect `next` to a log or condition using `${yourVariable}`; connect `error` when recovery is needed.

A URI copied into imported JSON does not carry a permission grant. Select the image again on the importing device if access is missing, the provider changed, or permission was revoked. An inaccessible selected image produces a permission/error result; the action does not silently substitute another file.

## Reading the screen

Set `source=screen`. On Android 11 / API 30 or newer, open **Screen OCR access** in the node editor or Permissions, then enable **Ghost Magic screen OCR** in Android Accessibility settings. Image OCR does not require this service. Screen OCR is unsupported on older supported app versions of Android.

This is a screenshot-only Accessibility service. It requests screenshot capability, no window-content retrieval and no gestures. Its event mask is zero by default, the callback consumes no accessibility events, and enabling it does not start a polling loop. A screenshot is requested only when an explicitly run or enabled scheduled flow reaches the screen OCR node. Revoking service access is detected by the adapter.

Manual runs stay in the visible app lifecycle: screen OCR therefore sees the currently visible display, which can include Ghost Magic's editor or trace. Switching away requests manual cancellation; this version does not hide the app and silently continue a manual capture. A scheduled flow captures the current default display when its node executes, without opening permission dialogs or interacting with another app. Scheduling can therefore read sensitive content visible at that time: enable it only for a flow and screen you intend to read. It does not target a named application, window, or secondary display. Protected or locked content can reject capture; secure-window restrictions are not bypassed.

## Results, errors and limits

Successful recognition writes a string to `outputVariable` and follows `next`. **An empty recognized string is a successful empty value**, unlike Macrorify's observed null/blank path. No automatic numeric conversion, default substitution, whitelist/blacklist, replacement rule or text matching is implemented. The trace reports the recognized character count, not the full OCR text. The output remains a per-run string and can be used in later nodes.

Failures follow the configured `error` edge and populate `error.code` and `error.message`; without that edge the run fails. Typical codes include `INVALID_CONFIG`, `PERMISSION_REQUIRED`, `UNSUPPORTED`, `EXECUTION_FAILED`, `OCR_CAPTURE_TIMEOUT`, `OCR_CAPTURE_RATE_LIMITED`, `OCR_SECURE_SCREEN`, and `OCR_CAPTURE_FAILED`. Cancellation and the engine's elapsed-time budget take precedence over starting an error recovery node. A failed OCR operation does not overwrite a prior output-variable value; use error routing to avoid treating an old value as new text.

- Image content: maximum 16 MiB encoded input and 64 million decoded source pixels.
- Screen capture: maximum 16 million pixels; five-second callback wait.
- Recognition: 30-second wait after submitting to the SDK; maximum 100,000 output characters.
- Execution: one shared active flow, 1,000 engine steps and a 120-second run budget; existing scheduled job cancellation rules also apply.

These are cooperative bounds. A document provider's blocking `openInputStream` or `read`, bitmap decoding, and native work cannot be forcibly interrupted at an arbitrary instruction. The recognition deadline starts after decoding/preparation; it is not a hard 30-second bound on the whole action. On cancellation/timeout after SDK submission, completion retains responsibility for closing the recognizer and recycling its bitmap. No subsequent node starts after the engine observes cancellation or an exceeded run budget.

## Data handling

Recognition uses a bundled on-device SDK. The manifest explicitly removes `INTERNET` and `ACCESS_NETWORK_STATE` permissions, including permissions introduced by dependency manifests. Input bitmaps are released after use; screen images are not intentionally saved by this action. Selected image URIs and persistent provider grants remain available for the configured flow.

OCR strings are flow variables. The existing SQLite run journal stores final variables for the latest 100 runs in app-private storage, so recognized text can persist in history even though the trace only contains a count. A later log or notification node can also expose the text by design. This version has no OCR-specific redaction, retention override, or automatic URI-grant cleanup when a node is deleted.

## Sources and next work

Implementation: `core/.../OcrConfig.java`, `core/.../Engine.java`, `app/.../OcrReader.java`, `app/.../OcrScreenshotService.java`, `MainActivity.java`, and `res/xml/ocr_accessibility.xml`.

Public API references: [ML Kit Android text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [supported languages](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages), [AccessibilityService.takeScreenshot](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,%20java.util.concurrent.Executor,%20android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)), and [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo).

Further candidates need separate contracts and tests: character filters, ordered replacements, grayscale/threshold preprocessing, numeric/default semantics, text location and matching, dedicated region selection UI, EXIF orientation, OCR history controls, and real-device permission/capture behavior. Thai script requires a separately evaluated engine; translated UI or Latin OCR multilingual marketing does not establish it.
