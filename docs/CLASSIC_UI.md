# Classic interface reconstruction — 0.5 experimental

The interface now follows Automagic's familiar structure: compact flow rows, a top app bar, a floating add button, a graph with cyan outlines and teal fill, and separate Trigger / Condition / Action selection. Trigger uses a rounded rectangle, Condition a diamond, and Action a rectangle. Node settings use a full-height dialog with a type selector.

Reference: original Automagic 1.37.0, SHA-256 `0ab40898c1d6d7f1cac59b6119568c96ff586883392b228e6eb884c75212882a`; retained local REA evidence `ev_b2c214d76d41a1efb7261a5710b8ebc99a900be31131b633f56e72be55e849bf` (resource decode, partial coverage). The packaged tutorial image `res/drawable/tutorial_flow_flow.png`, `res/layout-v23/flow_list_row.xml`, `flow_list_activity.xml`, and resource colors establish these visual choices. No reference images, icons, layouts or executable code are embedded in this implementation.

This is an initial reconstruction, not pixel-perfect or runtime parity. The original was installed and reached its agreement screen on the isolated emulator; the agreement was not accepted and no original editor session was observed. Custom-view gestures, exact dimensions, menu ordering, user-defined flow groups, reusable components, undo/redo and all original configuration screens remain incomplete. Current grouping is a single Flows section.

The list switch controls the existing periodic scheduler. Manual flows show a disabled switch because no autonomous manual trigger exists. Choosing another Trigger type replaces the current trigger while retaining its node ID and routes; the engine still permits one trigger. This is a runtime constraint, not original Automagic semantics. Switching component types resets that node's configuration to the selected type's defaults.

All currently executable capabilities remain available. Missing original capabilities are not represented as working actions. OCR is retained under Action. Flow JSON schema and saved positions remain compatible with 0.4; the changed node width may require rearranging an existing graph. Manual-run lifecycle, schedule behavior and history are unchanged.

In 0.6, node selection and drag-to-connect controls were added; see [NODE_CONTROLS.md](NODE_CONTROLS.md).
