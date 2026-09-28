# Future work: T-ray scanner for concealed infrastructure

**Status: deferred proposal only. Do not implement as part of the current power sprint.**

The user explicitly wants a future handheld T-ray scanner that reveals concealed electrical cables and gas piping within a radius, including infrastructure behind blocks. This document records that request and a safe design direction; it is not authorization to begin implementation. The scanner must not be used as a reason to change active power, atmospherics, or player-character work.

## Intended behavior

- Activating an eligible handheld scanner temporarily reveals covered electrical cable runs and gas piping near its operator, even when ordinary block occlusion would hide them.
- The reveal is observer-specific: only the scanner operator sees the additional infrastructure visualization. Other players retain their ordinary view unless they independently use an eligible scanner.
- Use a bounded, genuinely three-dimensional spherical radius, spanning multiple floors rather than only the operator's current floor. Radius is measured from the controlled character's world position and is a server-defined, configurable balance value; do not silently equate it with the SS14 reference's 4-tile default.
- Covered cables and pipes are revealable infrastructure targets; this is not a general x-ray effect and must not reveal arbitrary blocks, entities, inventories, or unrelated concealed content.
- Revealed infrastructure is an overlay/presentation distinct from normal exposed cables, with clear depth/alpha treatment that communicates its concealed status without confusing it with ordinary occlusion or world geometry.
- The reveal lasts only while activation is valid. Deactivation, eligibility loss, leaving range, chunk unload, disconnect, or other lifecycle termination must remove corresponding reveal state promptly.

## Pinned reference material

These SS14 source paths are the behavioral references supplied for this request. Review the relevant upstream revision and its license before using implementation details or assets; they describe reference behavior, not code that should be copied wholesale into this project.

- `Resources/Prototypes/Entities/Objects/Tools/t-ray.yml` — handheld T-ray tool prototype.
- `Content.Shared/SubFloor/SharedTrayScannerSystem.cs` — shared scanner behavior; the reference `TrayScannerComponent.cs` has a default **4 tile** range.
- `Content.Shared/SubFloor/TrayScannerComponent.cs` — scanner component/configuration, including that default range.
- `Content.Client/SubFloor/TrayScannerSystem.cs` — client presentation behavior in SS14.
- `Content.Shared/SubFloor/SharedSubFloorHideSystem.cs` — shared covered infrastructure hide/reveal behavior.
- `Content.Client/SubFloor/SubFloorHideSystem.cs` — client-side reveal presentation behavior.

The reference behavior treats covered cables and covered pipes as separately revealed infrastructure, rather than simply disabling ordinary occlusion. Preserve that distinction in any future design. Do not claim the SS14 range or rendering details are already implemented here.

## Architecture and safety constraints

### Current cable-visual privacy gap

The current cable visual protocol is not a privacy boundary. `CableVisualServerHooks` sends all cable records for watched chunks to every ordinary client. This includes cable positions on the top face under intact tiles and cables physically behind walls; `CableVisualRenderer` hides some of those records in the client presentation, but does not prevent a modified or hacked client from inspecting the received positions without a scanner. Do not describe concealed cable locations as currently secret or protected from clients. Ordinary visual occlusion is a rendering behavior, not server-side access control.

This is distinct from the proposed scanner overlay: ordinary exposed-cable presentation and block occlusion must remain unchanged, while any additional concealed-infrastructure reveal would be an observer-specific, server-authorized overlay. Before scanner work can claim observer-specific privacy, the normal cable visual snapshots must first be redesigned or server-filtered so unauthorized clients do not receive concealed positions, or the responsible owner must explicitly approve presentation-only concealment with its information exposure documented and accepted. Gas piping must never be exposed to unauthorized clients through ordinary packets or scanner packets; its data contract and observer authorization require atmos-owner approval. No present cable networking or renderer changes are authorized by this proposal.

### Server-owned data and authorization

Electrical cable truth must come from the existing server-owned power infrastructure records and loaded-chunk rules; the client must not infer authoritative cable presence from blocks, cached visual geometry, or a client-authored scan request. Gas-pipe truth must come from an atmos-agent-approved server-owned pipe representation. The future implementation must first identify the authoritative owners and read-only query contracts for both networks.

The server authorizes scanner activation, determines operator position and eligibility, applies the bounded 3D spherical radius, and selects only currently valid/loaded targets. Use bounded chunk/cell watchers or equivalent spatial indexing so updates are limited to chunks intersecting the active scan sphere. Do not force-load chunks to satisfy a scan. Watch registration, retained reveal data, queued updates, and per-tick work must all be bounded; authorization is scoped to the owning observer and must be rechecked as the observer moves or changes state.

Send only the minimum observer-specific reveal information needed to render the authorized nearby cable/pipe targets. A scan packet is not a grant to reveal data outside its radius or after the server-side scan ends. Clients render server-authorized reveal data only; they cannot create, extend, persist, or relay reveals, nor author infrastructure state. Ensure ordinary chunk snapshots and other viewers cannot receive this private reveal payload accidentally.

### Activation, character control, and cleanup

Treat activation as a server-validated action on the eligible handheld item, not as client authority. Equipment/inventory eligibility must be evaluated only after the player's character-control/possession transition has settled, using the project’s established controlled-character ownership and lifecycle contract. A transient ghost/body handoff must not attach a scan to the wrong character or permit a stale operator to keep revealing.

Maintain an explicit active-scan lifecycle and make termination idempotent. Stop and clear observer-scoped state on item removal/unequip, invalid control or eligibility, explicit deactivation, disconnect, relevant entity removal, and watcher/chunk unload. On chunk reload, do not replay an expired reveal; require a still-authorized active scan and a fresh bounded server evaluation. Clients must also discard transient reveal state on unwatch, expiry, disconnect, and session/world teardown.

### Rendering

Render the extra indication only in the authorized observer's client presentation and only for its active reveal set. Use an alpha/depth overlay or equivalent distinct presentation for concealed targets; normal cable visibility and ordinary block occlusion remain unchanged for everyone else. Overlay ordering, transparency, depth testing, floor separation, and multi-floor readability require explicit visual review. The scanner must not make concealed infrastructure globally visible via shared block state, ordinary cable visual snapshots, or a persistent world mutation.

## Prerequisites and ownership

1. The power owner confirms the stable server query for actual cable records, loaded-only access, and the correct representation of covered versus exposed cable. Do not depend on client render snapshots as the source of truth.
2. An atmos agent owns and approves the future gas-pipe data contract/query and its lifecycle. Gas pipe ownership is a dependency, not an invitation to modify active atmospherics work in this task.
3. The player-character owner confirms the settled-control/equipment eligibility hook and disconnect/transfer lifecycle contract. Do not introduce a competing possession or inventory model.
4. Agree on scanner eligibility, configurable bounded radius, scan/update cadence, retention/expiry, and the performance/privacy budgets below with the relevant owners before implementation.
5. Review SS14 source provenance and licensing before adapting any code, prototype details, or artwork. Prefer project-owned rendering/assets; this request does not authorize importing SS14 assets.

## Isolated future milestones

Each milestone is separately reviewable and may be stopped without changing unrelated active systems. No milestone below is part of the present task.

1. **Contract and threat model:** document authoritative power and atmos queries, eligibility after control settles, observer scoping, lifecycle termination, radius semantics, packet limits, and licensing/provenance. Explicitly resolve the current cable-visual privacy gap: redesign/filter normal snapshots before promising observer-specific privacy, or obtain explicit owner approval for presentation-only concealment and document the resulting exposure. Establish that gas piping cannot be exposed by unauthorized packets. Obtain power, atmos, player-control, and owner approval before proceeding.
2. **Read-only server query:** add focused tests for bounded 3D sphere selection across floor/chunk boundaries, loaded-only behavior, and independent cable/pipe target classification. No scanner item, client reveal, or mutation of infrastructure.
3. **Authorization and bounded watchers:** implement server activation/eligibility and observer-scoped chunk/cell watchers with explicit queue, retention, rate, and payload limits. Test every cleanup path and ensure no force loads. No rendering yet.
4. **Observer-only client overlay:** consume only authorized transient reveal updates; add expiry and unwatch/session cleanup. Keep normal infrastructure synchronization/visibility untouched. Validate alpha/depth behavior, multiple floors, and resource reload.
5. **Handheld integration and acceptance:** connect item activation only after settled character control, test multiplayer privacy and lifecycle end-to-end, profile worst-case budgets, then seek owner acceptance. Do not claim completion until all gates below are met.

## Privacy, multiplayer, and lifecycle tests

- With two or more clients, activate the scanner for one observer and verify that only that observer receives/renders concealed cable and pipe targets. Another client with no active scanner must not receive reveal payloads, including through chunk-watch snapshots, movement, reconnect, or another client's activation.
- Inspect ordinary chunk-watch snapshots and all normal cable visual packets from a client without a scanner: after the privacy design gate is resolved, verify concealed cable positions are not transmitted to unauthorized clients, unless the power owner has explicitly approved documented presentation-only concealment. Verify that client-side occlusion alone is never counted as privacy, and that neither ordinary nor scanner packets expose gas piping to unauthorized clients.
- Verify exact inclusion/exclusion at the configured spherical boundary in 3D, including vertical distance, adjacent floors, chunk edges, diagonals, and targets outside radius. Confirm a sphere rather than a square, horizontal-only circle, or unbounded floor scan.
- Verify exposed cables remain ordinary presentation, concealed cables reveal only while authorized, and covered pipes reveal separately through the atmos-owned query. Ordinary block occlusion and other concealed content must remain unaffected.
- Attempt forged activation, forged target/reveal updates, excess radius, replayed/stale packets, and requests for unloaded/out-of-range chunks. The server must reject them and must never load chunks on demand.
- Verify reveal removal on deactivation, unequip/item loss, eligibility change, control transfer/settling, death/removal as applicable, leaving a watched chunk/radius, chunk unload, disconnect, reconnect, and client session teardown. Reloading a chunk must not resurrect expired state.
- Verify a scanner activated during or immediately after a player-character handoff cannot reveal for the former body, the wrong character, or an unsettled controller. Verify a newly settled eligible operator can activate normally.
- Verify simultaneous scanners have independent authorization and cleanups; one observer's stop/expiry cannot clear or expose another observer's state.
- Verify restart/save-load does not persist transient reveal state, and changing ordinary infrastructure state is never caused by a scan.

## Performance budgets to agree before implementation

No measured performance claim exists yet. Before milestone 2, owners must set and record numeric ceilings for maximum radius, active scans per server, watched chunks/cells per scan, candidates examined per update, update cadence, queued updates, retained observer-target records, and bytes sent per observer per update. Those limits must cap worst-case work rather than rely on typical player separation.

Profile synthetic dense infrastructure, overlapping observers, rapid movement across chunk boundaries, repeated activate/deactivate, and maximum-radius multi-floor scans. Demonstrate bounded server tick work and memory, bounded client render/update work, no unbounded backlog under edits or packet delay, and no forced chunk loads. Record the environment and worst-case measurements against the agreed ceilings. If the combined cable-plus-pipe query cannot meet them, reduce cadence/radius or redesign the index rather than scanning whole worlds or weakening observer authorization.

## Owner acceptance gates

- [ ] Power owner confirms cable records are authoritative, covered-target selection is correct, and no cable state or ordinary visual sync was changed.
- [ ] Privacy gate is resolved before claiming observer-specific confidentiality: normal cable snapshots are server-filtered/redesigned to withhold concealed positions from unauthorized clients, or the power owner explicitly approves documented presentation-only concealment. Tests inspect received packets rather than relying on `CableVisualRenderer` occlusion; unauthorized gas-pipe data is absent from ordinary and scanner packets.
- [ ] Atmos owner confirms the gas-pipe data owner/query and independently accepts covered-pipe selection; no atmos-owned representation was guessed or duplicated.
- [ ] Player-character owner confirms eligibility is evaluated after control settles and transfer/disconnect cleanup follows the established ownership contract.
- [ ] Owner approves scanner eligibility, bounded spherical multi-floor radius, cadence, privacy model, and numeric resource budgets.
- [ ] Two-client (and overlapping-scanner) tests demonstrate observer-only delivery, no out-of-range or unloaded access, no stale state, and no client authority or information leaks.
- [ ] Visual owner acceptance confirms concealed overlay clarity/depth/alpha across floors and occlusion, with ordinary exposed cables and unrelated occlusion unchanged.
- [ ] Agreed worst-case server/client performance measurements meet the recorded budgets; no forced loads or persistent scan state.
- [ ] Licensing/provenance review is complete for any reused reference code or assets, and the owner explicitly accepts the finished scope.

Until the prerequisites, milestones, tests, and owner gates are explicitly approved, this remains deferred future work. **Do not implement the scanner now.**
