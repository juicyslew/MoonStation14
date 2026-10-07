# Disposal pneumatic sprint handoff

## STATUS

**Closed by owner acceptance.** The owner reports all observed issues resolved after the live finite-air intake fix. Construction, connected containment/camera, full-route transport and exit behavior are accepted. This is not a claim of complete upstream feature coverage or a green full GameTest suite.

Latest artifact: `build/libs/moonstation14-0.0.1.jar`. Original feature commit: `d0a0152`; follow-up changes remain uncommitted. Preserve unrelated working-tree edits, especially atmosphere and character lifecycle work.

## ARCHITECTURE

- Resolved typed disposal prototypes drive unit timing/power, tube routing/geometry/construction and holder air/turn damage. Inheritance/defaults are runtime inputs, not documentation-only JSON.
- Exposed pipes occupy their own block cells. Ordinary straight tubes can occupy eligible sealed steel-wall hosts without replacing the wall or breaking its atmosphere seal. Host incarnations invalidate replaced structures. Orthogonal forward/up frames support all six directions; trunk routing ports are distinct from the upward unit interface.
- Server-owned holders carry whole-stack ledger items and existing bodies, preserving UUID, Mind, inventory and control identity. Loaded-only routing never forces chunks. Generic containment lives in `ms14/containment`, not disposal-specific status lists.
- Containment forces third-person back, locks perspective changes and restores the previous perspective on final exit, not unit-to-holder transfer. Targeting is owner-scoped; body hands/bag/belt HUD remain available. Transit camera can pass terrain.
- Movement eligibility is centralized in `CharacterControlSystem.canMove`; action permission is separate. Escape also requires a hand and the resolved unit-wide 0.5-second cooldown. Idle run-mode input does not count as an escape attempt.
- Transit interpolates consecutive authoritative samples only for the same body/epoch/holder; transitions reset. Routes are not client-predicted. Generic `ExternalBodyFlight` handles outlet momentum, passive advancement, same-tick deduplication and cancellation on containment. Active flight suppresses adapter gravity/vertical drag; afterward normal physics resumes until grounded motion settles.
- Before flush, unit occupants use world air (`DisposalUnitWorldAirSource`), not the empty charging reservoir. Flush withdraws finite headspace gas into the resolved 2500 L reservoir, transfers it into the 70 L holder and clears unit air. Holder exposure and inhale/exhale use that same finite carried mixture. Exit returns remaining gas where an eligible finite destination exists.
- Live intake and release use `AtmosphereService.finiteCellEndpointForImmediateTransfer`: immediate server-thread, same-tick transfers requiring loaded chunk identity, finite ownership, passability and non-exterior state. They do **not** require a complete connected-room enclosure proof. The older trusted room endpoint retains its strict proof for setup/test use. Unknown air never becomes invented oxygen; unavailable sources preserve owned reservoir inventory. Transactions conserve species and thermal energy.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/disposal/`: `prototype/`, `domain/`, `world/`, `unit/`, `transport/`, `ui/`, `client/`, `fixture/`.
- `src/main/resources/data/moonstation14/moonstation14/disposal_entity/`: typed inherited defaults.
- `ms14/containment/`, `character/CharacterControlSystem.java`, `player_body_control/movement/ExternalBodyFlight.java`: shared ownership and movement capabilities.
- `atmos/world/AtmosphereService.java`, `disposal/unit/DisposalUnitFlushGas.java`, `disposal/transport/LoadedWorldDisposalRouting.java`: live finite-cell gas transaction boundaries.
- `disposal/transport/DisposalRideDamageDiagnostics.java`: observational typed-damage/air logs; flush intake also logs availability and oxygen transfer.
- `hands/network/`: real-level tube shape callbacks, bounded authoritative ray admission and immediate hand-refresh acknowledgement after construction.
- Supporting indexes here: `parity-audit.md`, `architecture.md`, `fixture.md`, `assets-and-attribution.md`, `validation.md`. Older chronological entries can describe superseded states; this handoff records closure.

## SS14 CORRESPONDENCE

Reference revision verified: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` in the Documents `SS14-dev/space-station-14` checkout.

- Unit chain: `DisposalUnit → DisposalUnitBase → BaseMachinePowered → BaseMachine → BaseStructure`; relevant native defaults include 1000 W, 0.5-second self-entry, 2-second insertion/manual flush, 30-second automatic engage and timer-based charging.
- `DisposalUnitSystem.HandleAir` samples the unit's atmosphere tile using source temperature and `0.1 × deficit toward (0.25 × 1 atm × 1.01)`. `DisposalTubeSystem.TryInsert` merges unit gas into `DisposalHolderComponent.Air` (70 L), then clears unit air. `BeingDisposedSystem` redirects exposure and breathing; `DisposableSystem` releases remaining gas on exit.
- Native segment timing is 0.1 seconds; default turn damage is zero. Open exits use distance 3/speed 10, nominally six local ticks. No unconditional ride injury or generic launch sound was introduced.
- Own-cell colliding octagonal 14/16-diameter pipes, sealed structural hosts, role colors, headspace sampling for solid Minecraft cabinets and gravity/collision behavior are deliberate Minecraft adaptations. Native pipes are noncolliding/subfloor. Imported sounds/sprites retain attribution; unresolved audio provenance is tracked in `docs/audio-unknown-provenance.md`.

## DECISIONS / WHY

- Construction consumes no hand slot: G enters Disposal Pipes; hold G + scroll selects; R rotates; H exits. Bindings are configurable and displayed. One click places one tube; held-G palette does not flicker. Shared crafting/browser expansion is separate.
- Preview/server target selection is shared. Sealed crossings use a scoped no-depth color-only ghost and explicit wall label. Placement issues persist 80 client ticks. Missing refreshed hand proof says “Updating held material…” rather than reporting wrong material.
- E opens unit controls, primary use inserts held stacks, K begins power-independent timed self-entry. Server post-tick drives completion/cancellation without relying on the carrier's player tick.
- `/ms14 disposals_fixture build ~ ~ ~` provides a ground-safe powered room and 259-node tour covering ten tube types. No footprint extends below supplied Y. Successful sites are remembered for initialize/status/enter; see `fixture.md`.
- Severe connected transit injury corresponded to zero-oxygen, approximately 0.7 kPa carried air. The live intake incorrectly used the strict test-oriented whole-room endpoint. Fixing local finite-cell intake/release preserved finite gas rather than masking pressure/asphyxiation damage. Separate room CO₂ work was not absorbed into this fix.

## KNOWN GAPS

Client self-entry prediction and canonical critical mob-state eligibility remain incomplete. Dedicated spawn-rejected fallback, bound-owner held-explosion collection and welder refilling remain follow-ups. Storage containers/freezers and the shared browser were not implemented. Closure does not waive these parity gaps.

## VALIDATION

Latest focused validation: **123 JUnit tests passed**, Java/GameTest compilation and jar generation passed; independent review passed. Log: `build/disposals-live-intake-focused.log`. New `DisposalLiveIntakeGameTests` cover local intake despite strict-proof rejection, species/thermal conservation, holder pressure, vacuum/unavailable sources and stale endpoints; these native cases compiled but were not run.

Earlier focused native tests completed both full tours with objects and bound HUMAN identity/inventory/breathing preserved. Last consolidated movement batch was **11/12**; landing cleanup was corrected afterward with focused unit coverage, without a headless rerun. Broader historical GameTest failures remain unresolved and are not claimed unrelated. Failure artifacts are retained. No additional server/client was launched for recent fixes. Owner connected acceptance closes the observed issues.

## NEXT WORK

Start future disposal work here and consult the parity audit before extending composition. Add critical mob-state restrictions through the shared capability query, not disposal-specific health thresholds. Run the compiled live-intake and landing regressions in a future consolidated approved native validation batch; keep full-suite failures visible. Treat shared construction browsing and unrelated atmosphere work as separate scopes.
