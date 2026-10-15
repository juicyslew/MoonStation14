# Living mouse carrying — hand milestone

The pinned SS14 composition is `MobMouse` inheriting `SimpleMobBase` and
`BaseMobAnimal`; carrying is an inventory/container adaptation, not an upstream
mouse component. `CanEscapeInventory` is five seconds upstream. The local mouse
already comes from resolved character prototypes and remains the single live
entity owning physiology and Mind state.

Reference verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`Resources/Prototypes/Entities/Mobs/NPCs/animals.yml` defines `MobMouse` with
parents `SimpleMobBase` and `BaseMobAnimal`, `Item.size: Tiny`, and
`CanEscapeInventory`. The five-second escape contract is deliberately deferred
to the later escape milestone. The local `PortableBody` capability and
`mouse_tiny` profile represent the resolved `Item/Tiny` eligibility, while the
adapter's separate `portable_body_key` profile is internal Minecraft inventory
presentation/physics metadata. This is not upstream `EntityStorage` or a copied
mouse entity; the live mouse UUID remains the physiology/Mind owner.

`PortableBodyReference` plus `PORTABLE_BODY_REFERENCE` and `PORTABLE_BODY_KEY`
are the existing non-owning adapter primitives. `PortableBodyKeys` is the shared
recognition predicate. Typed `PortableBodyComponent` is resolved through the
character prototype chain and currently opts only the mouse in. `PortableBodyCarrierOwner`
uses the existing `BodyContainmentService` registry and current hand ledger;
`PortableBodyCarrying` validates prospective references and reconciles bind,
transfer, and unbind without a competing body-owner index. Carried breathing uses
the carrier's loaded world air cell, never a disposal reservoir or invented air.
The loose-throw/consume guards continue to reject adapter keys.

`BodyHandItemTransfer` now admits a resolved-capability living target through the
same bounded collider ray, and the normal actor/trusted gateway commit binds or
reconciles ownership before publishing the hand state. Dropping the key removes
the hand attachment, unbinds/restores the same loaded entity, and uses the shared
loaded collision placement plan with the live body's bounds; it never spawns a
key item entity or replaces the body UUID. The focused GameTest covers trusted
pickup/movement/drop plus stale, full-hand, and out-of-reach conservation.
The existing client pickup request path now nominates a resolved portable living
target when no ordinary loose-item pile target is selected; the server ray remains
the only selection authority and no new network channel was added.

The focused native fixture covers live hand pickup/drop, held and worn grids,
world-grid store/take and world-to-world transfer, whole-bag pickup/drop,
carrier/world-root repinning, stale/full/out-of-reach/blocked denial, and body-key
identity conservation. An earlier `living-carry` run passed 5/5 (5/850 selected)
on 2026-10-12; a later 13/13 result also predates current persistence and
prepared-root transaction edits. Neither validates current sources. Item tokens
locate the current ledger entry while the reference UUID
remains stable across root transfers. Portable keys now carry a bounded visual
cache (profile, source name, coat, and medical state only); server hand/world-grid
snapshots refresh that view from the exact registered original body when loaded.
Inventory/grid UI reuses the existing SS14 mouse RSI frames and held-body renderer
reuses the R2 mouse rig and resolved portable profile scale. This is presentation
only: it stores no health, stomach, Mind, or physiology. CPU selector/codec and
asset mapping tests are added but were not run in this source-only unit; native
GPU/pixel review remains unverified. Portable recovery relation persistence and
cold entity-NBT GameTest sources have been added but remain uncompiled and unrun;
original-flag recovery is not accepted until those checks pass. The controlled
OFFLINE-mouse locator/profile-relocation cold acceptance is also unverified; the
existing production proof path has not yet been exercised by that fixture.
Loose throwing,
consumption of keys/containing hosts, and five-second escape remain deferred or
fail-closed; this is not complete living-carry.
