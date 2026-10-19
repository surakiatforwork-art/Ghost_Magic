# Legacy inspection and source preservation — 0.3

## What this release does

Import accepts a native Ghost Magic JSON document or an XML Automagic-style `.flows` export through Android's document picker. Native JSON follows the existing independent import path and remains unscheduled. XML enters a separate **Legacy library**, never `FlowStore`. There is no executable legacy-to-native converter in 0.3.

The reader recognizes the unnamespaced `<data version="…">` envelope documented by the local blueprint for the 1.37 reference. Version labels 1.37.0 and 1.38.0 are recognized for reporting, not proof of compatibility. Other labels produce a warning and remain review-only. Zipped backups and namespaced root envelopes are not supported.

The inspector projects shared trigger/condition/action definitions, names/types, container IDs and references, connection endpoints/types and execution policies. References resolve within the object kind; duplicate names remain ambiguous rather than choosing a definition. Multiple trigger references, fan-out and unknown connection types remain explicit. The graph view here is a read-only inventory, not the native runnable graph editor.

Reports distinguish structural errors from review warnings and informational retained fields. All reports say **NOT EXECUTABLE**. Partial analogues such as brightness, log or a Wi-Fi panel are suggestions requiring manual configuration and semantic evidence; they are not import mappings. Original `enabled=true`, `PARALLEL`, `QUEUE`, `SKIP` or `STOP` never activates an execution policy or job.

## Preservation and integrity

The original bytes are stored privately as `legacy/<sha256>.flows` with optional JSON display metadata. Source bytes commit through AtomicFile before metadata. A failed metadata write leaves a recoverable source archive. Identical bytes deduplicate to the same identity. Open/export recomputes SHA-256 and refuses an altered archive; it never silently replaces a changed source with another copy.

Original export writes the stored bytes, not reserialized XML. Encoding, CRLF, comments, CDATA delimiters, unknown fields, namespaces inside unknown subtrees and attribute order therefore survive unchanged. The SAX inspection tree is a projection: it does not preserve comment tokens or distinguish CDATA from ordinary character data. This distinction is deliberate; byte-exact preservation is supplied by the archive, not by a claim of lossless XML reserialization.

Reports are UTF-8 and include the source SHA-256, source size, inspector profile, flows, definitions, topology and diagnostics with line positions. They are summaries, not full configuration dumps. Inspect the XML tree or export the original for all parameters. Preview text and item counts are bounded; complete source bytes remain intact.

The library holds up to 50 unique archives. Removing an archive requires an in-app confirmation and does not remove native flows or schedules. No network service is contacted by this importer.

## Parser boundary

The parser accepts at most 1 MiB, 64 nesting levels, 10,000 elements and 64 attributes per element. It is nonvalidating and namespace-aware. Mandatory SAX lexical-handler registration rejects actual DTD starts before entity declarations; an entity resolver rejects external resolution. External entity and external-DTD features are also disabled where supported. A parser that cannot install the mandatory handler fails closed. DTD-like text inside CDATA/comments remains valid content and is not confused with a declaration.

Malformed XML, unsupported envelopes and unsafe declarations fail import before archive persistence. Well-formed exports with broken references may be retained for diagnosis, with errors visible; they still cannot execute. Existing source documents selected through the picker are never modified.

## Evidence and remaining work

No real exported `.flows` file was found in the workspace during this milestone. Fixtures are explicitly synthetic, based on `Automagics blue print/docs/09-legacy-flow-format.md`. They do not establish real Automagic parameter shapes, default values, time units, variable semantics, concurrency or semantic parity. The test asset is under `app/src/androidTest/assets/` and is not bundled in the production app.

Core tests cover branching/shared objects, CDATA, UTF-16, duplicates, missing references, fan-out, unknown elements, version handling, complexity limits and DTD rejection. Android instrumentation validates the actual SAX implementation, private atomic storage, byte identity, deduplication, metadata recovery, tamper refusal and the absence of native-flow/job creation. A separate SAF smoke uses the actual system picker for import and exports, checks source identity and unknown plugin reporting, compares exported bytes, and reopens the library after process restart. Its fixture is synthetic as well.

The SAF smoke also verifies the native JSON import path still creates an independent copy of a periodic graph without activating its schedule.

Executable migration requires real exports and reference results from Automagic. Next: document verified parameter shapes, build per-type converters with explicit unsupported placeholders/reports, preserve graph/variable/error semantics, test each converted flow against its reference, and require review before scheduling any migrated result.
