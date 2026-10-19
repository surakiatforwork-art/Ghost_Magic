# Ghost Magic Flow format v1

This version is independent of the blueprint IR and Automagic XML. Do not rename a `.flows` file to JSON and assume migration. A legacy importer requires separate evidence and preserved unknown nodes.

The document has `format: "ghostmagic.flow"`, integer `schemaVersion: 1`, UUID `id`, nonempty `name` (maximum 200 characters), positive `revision`, `startId`, `nodes` and `edges`. Each node contains `id`, exact catalog `type`, string-to-string `config`, and finite nonnegative logical editor coordinates `x`,`y`. Each edge contains `from`, `port`, `to`. The experimental import limit is 1 MiB, 200 nodes and 800 edges. Imports receive a new flow ID and never execute automatically.

## Runtime semantics

Exactly one manual or periodic trigger is the start. `trigger.periodic` requires a literal `intervalMinutes` integer from 15 to 10080; schedule activation is local state outside this document, so imports stay inactive. Execution follows a single directed path; conditions choose `true` or `false`, actions use `next`, typed failures use `error`. At most one edge may leave a given node/port. An unconnected selected output terminates the path; an unhandled error fails the run. Disconnected nodes are retained for editing but do not execute. An error route sets `error.code` and `error.message`; handled errors can still result in an overall completed run, so inspect the trace for recovery.

Variables are string values local to a single run. `${name}` resolves from variables already present before a node executes; substitution is one pass. A missing referenced variable is `INVALID_CONFIG`. Numeric configuration is validated after expansion; static invalid numeric fields fail graph validation before side effects. Battery above uses strict `>`; brightness is 0–100 inclusive; delay is 0–60,000 milliseconds. Brightness 0 maps to system brightness 1 to avoid writing a fully dark value; no hardware-luminance precision is promised.

One run executes at a time across the visible activity and background JobService. The current worker has a 1,000-node-step limit and a 120-second time budget. Cancellation is cooperative; delay checks frequently. Platform operations must return promptly; a synchronous host call cannot be forcibly rolled back or safely preempted. The graph snapshot is immutable while executing. Variables reset for the next run. Periodic admission and run journaling are described in [BACKGROUND_RUNTIME.md](BACKGROUND_RUNTIME.md). No configurable queue/parallel policy, shared global state or durable node-level resume is implied.

The permission UI does not resume a previous side effect automatically. Fix access and start a new run. Wi-Fi panel completion means the system controls were requested, not that Wi-Fi changed or a network connected. OEM-specific action behavior remains subject to device validation.
