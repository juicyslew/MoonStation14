# Collector slot lifecycle contract

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
The collector's `gas_tank` is a `ContainerSlot`; generic ItemSlots allow any
GasTank, interaction insertion/swapping and a separate eject verb. The reaction
consumes Plasma, not every accepted tank's gas. Unarmed activation toggles the
collector with a one-second use delay; it is not an anchoring gate.

`EjectOnBreak` defaults false; `EjectOnDeconstruct` defaults true.
`EmptyOnMachineDeconstructSystem.OnSlotsDeconstruct` detaches populated eligible
slots, preserving the tank. Robust `ContainerSlot.InternalShutdown` deletes a
remaining contained entity when its owner/container shuts down on the server.
Do not infer automatic world ejection on ordinary owner deletion.

The collector prototype itself has no Destructible component: research does not
establish a particular damage threshold/deletion caller. Deconstruction and
owner deletion are distinct paths. Sources: `collector.yml`, shared ItemSlots
component defaults and break handler, server deconstruction handler, Robust
container shutdown.

Local permanent-removal disposes the installed tank and its canonical ledger
claim; it deliberately does not eject a canister on ordinary block deletion.
Chunk unload continues to suspend and preserve the serialized slot payload.
Collector block drops preserve profile/configuration but omit the private tank
and mark initialization complete, preventing a dropped full-profile collector
from minting its configured starter tank again. This is an explicit Minecraft
owner-deletion adaptation, not a claim about SS14 destruction thresholds or
deconstruction behavior. Explicit tank ejection remains a separate operation.
No new collector-specific UI is required for upstream's standard slot behavior.

## Operator interaction research

Pinned generic ItemSlots `OnInteractUsing:204–270` inserts a held eligible tank
and swaps an occupied slot when `slot.Swap` allows it; the old item goes to hands
through the shared hands system. Ejection is a separate `AlternativeVerb` in the
Eject category, gated by hands, access/interact and pickup permissions. The default
entity/verb menu is **right-click** (`UseSecondary` / `MouseRight`). Alternative
world activation is **Alt+left-click**, with **Alt+E** as secondary binding; do not
substitute it for the eject menu. Local standard context-verb routing is not yet
implemented. Reuse `BodyMachineItemSlotTransfer` and admitted body-hand requests
for operator actions; do not add a collector-specific machine screen.
