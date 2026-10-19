# Background runtime 0.2

## Trigger and Android contract

`trigger.periodic` is a saved graph entry with a literal integer `intervalMinutes` from 15 to 10080. The editor's Schedule control activates a separate local schedule. A portable flow document contains no enabled flag or Android job ID, so importing a periodic graph never activates it. Manual Run can test the same graph with source `manual`.

The adapter uses ordinary [`JobScheduler`](https://developer.android.com/reference/android/app/job/JobScheduler) with [`setPeriodic`](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long,long)) and `setPersisted(true)`. Flex is the larger of Android's minimum flex and 5% of the interval; for a 15-minute schedule this is a final 5-minute window. It declares `RECEIVE_BOOT_COMPLETED` for persistence, not a boot receiver that launches an activity. This is deferrable bounded work, not an exact alarm or a perpetual service. Android controls initial delivery, batching, Doze delays and [quotas](https://developer.android.com/about/versions/16/behavior-changes-all). Force-stop prevents delivery until explicit app use resumes. No special exemption from Android 16 quotas is claimed.

The service is exported solely behind the system `BIND_JOB_SERVICE` permission. Worker threads perform loading and execution; main-thread callbacks return promptly. [`onStopJob`](https://developer.android.com/reference/android/app/job/JobService#onStopJob(android.app.job.JobParameters)) sets cancellation, and the interpreter stops at a cooperative boundary. `jobFinished` is not called after that stopped callback. No immediate retry is requested. A later interval is a new occurrence.

## Durable admission and identity

SQLite holds local schedule state: collision-free auto-increment job ID, flow ID, saved revision, generation, enabled state, and the last admitted attempt's wall/elapsed/boot clocks. Replacing/disabling a schedule changes generation. A callback must match ID, generation, revision and interval before admission. The saved flow is loaded under a common process lock and executed as an immutable snapshot.

A shared process guard permits one active run across manual and scheduled sources. A busy occurrence is skipped with a `BUSY` journal result; it is not queued or replayed. Schedule changes and admission use the same lock. Disabling cancels the registered job and signals the active run for that flow. Already completed side effects cannot be rolled back, and permission changes can race with platform calls.

Within one SQLite transaction, scheduled admission reserves the occurrence and inserts a `RUNNING` record before executing effects. Repeated delivery within the configured interval is rejected. Elapsed realtime is used within a boot; wall time bridges reboot. A backward wall-clock adjustment across boots can delay admission further. This policy may skip a close-together valid flex-window delivery; it favors avoiding duplicate attempts over exact cadence. Restoring a missing Android job preserves the generation and last-attempt clocks. Explicitly updating or re-enabling a schedule starts a new generation/window.

This is not an exactly-once side-effect guarantee. Android APIs and the journal do not share a transaction. A process can die after an effect but before its completion record. On the next runtime database open, old-owner `RUNNING` entries become `INTERRUPTED` with an explicit ambiguity message. The engine never resumes from the last node or automatically replays the incomplete flow. The reservation prevents immediate system redelivery from starting it again in the same interval. Journal timestamps for interruption describe detection, not the exact time the process died.

## Actions, permissions and history

Background runs can use local variables, conditions, battery reads, log, bounded delay, own-app notifications and system brightness. Notification and brightness permissions are rechecked when executing. Missing access produces a typed error; background execution never launches a permission dialog. Wi-Fi panel nodes are rejected for the entire scheduled graph before enabling and again before admission, including disconnected nodes. Settings UI requires a visible activity.

The journal retains 100 recent records, including flow name/revision/source, terminal status and final per-node trace. While a run is active it has a RUNNING record; detailed node traces are committed at finalization. A crash therefore may leave no per-node trace, and an interruption must not be interpreted as proof no effects occurred. Run history stays when a flow is deleted. No remote telemetry is sent.

## Verification boundary

Instrumentation covers graph interval rules, persisted JobInfo configuration, inactive imports, generation/revision fences, disabled jobs, background-only preflight, cross-source concurrency, cancellation/history, duplicate admission and preserving cooldown when restoring a job. Separate ADB tests exercise the real service with the editor hidden, system timeout, process kill and reboot. Forced delivery bypasses scheduling constraints and cannot establish natural cadence, Doze latency, quota behavior, OEM reliability or exact-time semantics.

Before broader release: test natural intervals under Doze and standby, API/OEM variants, storage failure, clock/timezone changes, permission revocation while running, and schema upgrades. Durable node-level resume, configurable queue/parallel policies and exact scheduling remain separate work.
