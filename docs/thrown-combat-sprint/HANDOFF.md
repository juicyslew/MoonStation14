# STATUS

Closed by the owner on **2026-11-05**, after requesting the final saved Actions-key
repair and declining further validation in favor of manual testing. Owner confirmed
the removal icon, acceptable armor models, returning/ricochet self-hits and prone
hypodart retrieval. Do not start more validation or resubmit without new authorization.

Last serialized gate: **PASS**, `run_c8a88f7cdd1bb6b6aa303742`, generation 1,
acknowledged. It predates the final saved-binding repair; it is not proof for that
last edit. No merge commit or merge continuation was performed. Sprint closure
does not authorize either.

The owner subsequently confirmed that this work was merged.

# ARCHITECTURE

- Resolved typed item/character composition supplies thrown damage, embedding,
  injection, armor and ensnareability. Existing loose entities remain the sole item
  owners through flight, attachment, timed removal, pickup and world recovery.
- `LooseItemEntity` dispatches authoritative swept contacts through
  `ThrownContactPhysics`, then the existing typed damage and thrown-hit event owners.
  `firstContact` accepts a segment starting inside the expanded target bounds;
  entry-only AABB clipping missed such contacts when an ordinarily moving target
  overlapped the projectile's previous position. No hitbox/speed workaround or
  general relative-motion CCD was added.
- `ThrowerClearance` suppresses initial overlap and the departure sweep. Clearance
  advances before candidate enumeration, even outside its query range; later
  return contacts use the normal damage/embedding/ensnaring pipeline. Suppressed
  contacts must not become fake previous contacts.
- Conditional self-removal selects restraints first, otherwise the newest embedded
  item. Repeated activation does not restart or retarget the pinned shared timer.
  Own attachments never intercept ordinary or Alt+E world interactions.
- `BodyItemTargetProbe.selectionBounds` extends only an attached item's upper face
  by the existing 0.2-block tolerance. Client pile nomination and server admission
  share it, retaining reach and block occlusion while allowing floor-clipped retrieval.
- All capability providers use one stateful 20-slot action/provider allocator.
  Surviving grants retain slots; disappearance frees a hole; new grants take the
  lowest free slot. Initial combat precedes scream through grant order, not slot
  reservations. Defaults are 1–9/0, then Shift+1–9/0. Equipment internals/mask and
  conditional removal use the same allocation. Effects receive both the validated
  provider grant and originating request sequence.
- HUD paint/hit/activation use the cached painted identity projection. Cells are
  28×40 logical pixels at half scale, with labels below the 24×24 icon area;
  viewport changes invalidate geometry. Capability and shared chat/status outer
  margins are one GUI pixel. Existing pointer/workspace owners remain authoritative.

# IMPORTANT FILES

Java paths are under `src/main/java/com/juicyslew/moonstation14/`:

- `entities/{LooseItemEntity,ThrownContactPhysics,ThrowerClearance,AttachmentPoseGeometry}.java`
- `ms14/combat/throwing/`: resolved profiles, embedding/injection and ensnaring.
- `ms14/hands/live/{AttachedItemRemovalSystem,BodyItemTargetProbe}.java` and
  `ms14/hands/client/{BodyHandClient,DirectClimbInputPolicy}.java`.
- `ms14/actions/`, especially `client/BodyActionKeys`, `BodyActionSlots`, HUD/layout/
  projection and shared presentation. `ms14/standing/client/` owns turnover input.
- `ms14/equipment/{ModEquipmentItems,client/AuthoredWearableMesh,client/AuthoredWearableRenderer}`.
- `src/gametest/java/.../hands/live/`: native self-removal, floor/prone selection,
  returning self-hit and normal-speed moving-bola fixtures.
- `PARITY.md`, `ASSETS.md`, `FEEDBACK.md`, `EVIDENCE.md`, `MANUAL.md` are supporting
  indexes. This handoff supersedes older fixed-slot/key and claim-only artwork notes
  in earlier handoffs. EVA architecture remains indexed by its own handoff.

# SS14 CORRESPONDENCE

Verified reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Knife/dart/hypodart and bola values/chains are indexed in `PARITY.md`; do not
  derive thrown damage from an item's melee damage. Dart/HypoDart solution limits,
  inherited injection behavior and armor gates remain distinct.
- Ordinary embedded removal inherits `DoAfterArgs.BreakOnMove=false`. Bola also
  explicitly permits movement during breakout. Movement remains allowed.
- Bola's maximum is one ensnare per target. Rejection leaves the second bola loose
  and thrown: it does not consume/delete it, force landing, or play the success cue.
  Normal hard-collision physics determines motion. The port's reflected trajectory
  is an adaptation, not a scripted upstream rejection rule.
- SS14 excludes the thrower for the whole thrown state. Post-separation self-hits
  are an explicit owner-requested Minecraft adaptation. Contact-point 3D rendering,
  tuned flight and enlarged HypoDart sprite are also documented adaptations.

# DECISIONS / WHY

- Actions remains an optional scrollable overview/overflow window, **Ctrl+K** by
  default. Search, drag/reassignment and other upstream management features are
  not implemented. K turns oneself over; Shift+K turns another body. Disposal climb
  defaults to Ctrl+J; the configured menu chord wins over an aliased climb binding.
- Roll intent is captured at key press, retained through release, and cleared on
  invalid UI/world context or level-identity change. Do not reinterpret modifiers
  later at tick consumption.
- Final repair converts the old saved **plain-K Actions binding** to Ctrl+K once
  per client initialization, after Options contains the registered mapping. It
  resets lookup, drains old menu clicks and saves via Options. Non-K custom and
  unbound settings are preserved; no raw options-file rewrite is used.
- Approved armor delivery `8146d749484b087e1d929f7d5edc267430bc3f6bf92c205e6f553010bb29c55f`
  is imported with six unchanged runtime files. Resolved WIDE helmet/vest bindings
  are included in the production client catalog. Draw the whole helmet once using
  its translucent material, not additional shell/visor draws. Parser accepts the
  four actual authored material contracts. Attribution/hashes are in `ASSETS.md`.
- Removal's temporary cyan outward arrow is project-authored and prototype-selected.

# KNOWN GAPS

- Per-item airborne catchability restrictions are explicitly deferred; catching is
  unchanged. No general moving-target rewind/CCD or self/back-source chooser was added.
- Saved disposal-climb settings are not migrated. The targeted Actions repair is
  the exception explicitly requested by the owner.
- GPU/mix checks cannot be inferred from automation. Existing unknown audio
  provenance remains recorded in `docs/audio-unknown-provenance.md`.

# VALIDATION

The last gate passed focused native, readiness and package/provenance checks.
Normal-speed moving-bola, return-hit and floor/prone regressions are included.
The final binding repair had a local 71-selector/413-test PASS before the owner's
no-further-validation instruction; actual saved-mapping, lookup, queue-drain,
save-once and preservation cases are tested. No fresh gate followed that repair.
Earlier fixture failures/corrections are recorded in `EVIDENCE.md`; standalone
disposable preflight logs were removed, not external workflow history.

# NEXT WORK

Await owner-directed work only. Preserve accepted gameplay and existing shared
owners; do not automatically implement deferred catching/UI features or rerun gates.
