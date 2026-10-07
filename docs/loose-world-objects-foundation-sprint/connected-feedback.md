# Connected feedback — object usability

The owner's first connected check found real targeting, input, geometry and motion failures despite green trusted fixtures. This pass addresses those reports; automated results must not be described as renewed owner acceptance.

## Changes and decisions

- Remove the pile name/action text. The locally selected loose object gets a depth-tested in-world bounding-box outline, using the same UUID as pickup/use nomination. Selection observation is independent of pending action/query permission. `]` still cycles members.
- Selected pickup uses selected-member visibility proof at **every** validation stage; the final transfer no longer silently substitutes nearest-item policy. Candidate overflow exposes a deterministic 32-member prefix, with a separate bounded scan ceiling.
- Give Shift+E its own rebindable alternate-world-use binding; remove the conflicting default belt shortcut. Empty-hand Shift+E remains world sipping, held-source Shift+E reverses liquid transfer.
- Center the jug mesh at its entity feet and resolve client dimensions through the client prototype catalog. Synced stack changes refresh its physical bounds.
- Generic sprites lie flat rather than inheriting Minecraft's small upright GROUND transform. Bag, steel and glass fit profiles use measured opaque PNG extents, with a 0.7-block longest visible dimension. Grenade ground placement is fitted from its actual model, not its raised item-display transform.
- Missing chunks outside the actual swept motion box no longer freeze an object through a blanket one-block halo. Missing chunks inside its motion path retain pending motion and retry; they are not treated as floor support or permanent settling.
- Existing loose objects no longer invalidate every short-drop sweep merely by overlapping it. Enlarged items also have a shorter safe near-wall candidate rather than unnecessarily falling back to a zero-velocity drop at the body origin.
- Pulling acts horizontally from the feet plane rather than lifting toward the body's chest. The SS14-derived movement slowdown remains **5%**, not a large encumbrance penalty. Both custom movement paths and client prediction use the shared factor.
- Normal fixed throw speed is raised from 0.55 to 0.75 blocks/tick for the owner's requested Minecraft usability. This is an explicit 3D tuning adaptation, not numeric SS14 parity. Hold-to-charge throwing remains out of scope.
- Successful jug breakage produces glass sound and bounded particles. Strong landing impacts can still break glass jugs; failed/partial spill keeps unadmitted contents.
- Creative drops of **MoonStation14 items** use loose objects; unrelated vanilla Creative drops remain vanilla. Uncontrolled Creative players can pick loose items back into their inventory after a short pickup delay. Controlled-body carriers must not bypass authoritative body hands through automatic inventory pickup.
- The Creative hook covers NeoForge's captured `PlayerToss` path as well as direct drops. It records the actual Creative dropper during native item construction and reads `ItemEntity.getOwner()` (the native cached **thrower**, not the pickup target). It does not trust client identity or serialize an item merely to recover its actor.
- Client bounds retry resolution when prototype catalogs arrive after entity/stack packets. Existing 3D `BlockItem` hosts use a separate model fallback, not the flat-sprite profile.
- Larger objects launch from a clear hand-height origin while retaining eye-based aiming. The full physical box must clear walls/ceilings; the jug-under-two-block-ceiling regression is covered.
- Leave ground-item render distance unchanged, per owner feedback.

## Focused connected recheck

First drop and retrieve a pickaxe in Creative; the pickup animation must complete without a crash. Then continue the checks below.

1. Aim at overlapping objects, cycle and click: the outlined member should be selected without the old text or repeated nearest-member denials.
2. Check jug model/hitbox alignment, flat larger bag/steel/glass sprites, and grenade floor contact.
3. Shift+E-sip a loose jug with an empty hand; E/Shift+E transfer held/loose liquid in the correct directions.
4. Creative-drop mod items and pick them back up. Confirm vanilla Creative items retain normal behavior.
5. Repeatedly drop/throw near chunk boundaries, toss a jug upward, and pull while walking. No permanent airborne freeze; target follows horizontally, walls stop it, breakage is obvious and contents are conserved.

## Validation

- **1,898 unit tests**, zero failures/errors: `build/loose-feedback-unit-final.log`.
- **All 430 required GameTests passed** in the fresh final run: `build/loose-feedback-gametest-release-02.log`. An earlier 430-test run had one unrelated intermittent power-path failure; its log/world are retained, and no power code was changed.
- Live fixtures cover ordinary untraced Creative drops, mod-item inventory pickup and vanilla preservation; grounded three-step pulling; nominated non-nearest pile pickup; air drops overlapping existing items; enlarged near-wall drops; and tall-jug throws under a two-block ceiling.
- Real PNG alpha bounds and the **actual production flat-sprite pose** are asserted for bag/steel/glass. The inspected software sheet is `build/reports/loose-object-feedback-sprites.png`. Grenade body/stem bounds use the pinned vanilla cocoa model, with floor contact and physical-box assertions. These are not Minecraft viewport captures.
- Native 1.21.1 collision source confirms non-forcing `getChunkForCollisions`; the removed one-block halo was unnecessary. A real chunk unload/resume fixture was not added, so connected edge/reload checks remain important.
- Raw benchmark samples and environment are retained at `build/reports/loose-feedback-measurements/` (150 samples). With enlarged footprints, final direct-work p95 was **0.878 ms for 1,024 settled objects** and **7.336 ms for 256 continuously jostled objects**. The active workload exceeds the provisional 5 ms guardrail. This is synthetic direct object work, not total-server/client/network cost or a player-capacity guarantee.

Connected acceptance of the corrections is still outstanding. Hold-to-charge throwing and render-distance changes were not implemented. Changes remain uncommitted and unstaged by the assistant.

## Connected crash follow-up

The next owner check crashed during pickaxe pickup animation (`run/crash-reports/crash-2026-10-03_15.35.28-client.txt`). Native `ItemPickupParticle.getSafeCopy` calls virtual `ItemEntity.copy()`. Its inherited implementation constructs a **plain ItemEntity with the original custom entity type**, so dispatch selects `LooseItemRenderer` and its typed bridge throws `ClassCastException`. This occurs before sprite/model rendering. A subtype-preserving loose-item copy is required; software pose tests and dedicated-server suites did not exercise this client animation path. The earlier green suites do not establish connected acceptance.

`LooseItemEntity.copy()` now creates the registered loose subtype and copies only presentation state (position, motion, deep-copied stack, rotation and settled state). It does not restore full entity NBT/attachments, register another world object or reuse the source UUID. The lifecycle regression invokes virtual `copy()` through an `ItemEntity` reference—the pickup particle's exact dispatch—and verifies subtype/type agreement, a fresh unattached copy, intact components/count/rotation and source isolation after copy mutation. Pickup animation stays enabled; neither the global vanilla renderer nor pickaxe assets are changed.

The same regression also covers the actual `ModItems.PICKAXE`, including damage and custom-name components. Automated coverage establishes copy/dispatch compatibility; it is not a connected client viewport test.

The compiled Java bridge was inspected with `javap`: `ItemEntity copy()` delegates to the new covariant `LooseItemEntity copy()`, rather than Minecraft's plain-item constructor. This is the signature used by the pickup particle.

Crash-fix validation: **1,898 units pass**, and the freshly compiled suite including the actual pickaxe regression passes **430/430 GameTests**. Logs: `build/loose-pickup-crash-unit.log`, `build/loose-pickup-crash-compile-final.log`, `build/loose-pickup-crash-gametest-final.log`. An intermediate run failed an unrelated atmosphere fixture; its world/log remain retained. The successful final disposable world was removed. Restart the client with the updated build and repeat pickaxe drop/retrieval before further appearance checks.
