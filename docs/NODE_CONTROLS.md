# Node controls — 0.6 experimental

Tap a node to select it. The pencil button appears on its left; the plus button appears below. Tap the pencil to edit. Tap plus to add a connected Condition or Action. Drag plus onto an existing Action or Condition to connect it; drag onto blank canvas to choose a new component at that position. Drag the node itself to rearrange it. Tap blank canvas to deselect.

Trigger and Action use the next output. Condition asks for true, false or error. Error routes for other types remain available in node settings. Existing output connections require explicit replacement confirmation. Cancelling the picker or replacement prompt retains existing edges. Dropping onto the source itself or a Trigger is rejected. Cycles between other nodes remain subject to the existing engine budgets.

Touch coordinates follow graph zoom. Cancelling a connector gesture creates neither a node nor an edge. Node position changes remain draft changes until Save, including a move interrupted after it began. Creating a component opens its settings; cancelling those settings leaves the new draft component, matching the editor's existing add behavior.

The Nodes menu and output-route selectors remain available as an alternate editing path. This does not claim exact original Automagic gesture parity: multi-touch zoom, continuous edge auto-scroll, undo/redo, reusable elements and missing original capabilities remain separate work. The implementation follows the user's specified interaction and the prior visual reconstruction.
