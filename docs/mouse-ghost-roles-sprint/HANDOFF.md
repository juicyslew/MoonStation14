# Mouse and ghost roles sprint

## STATUS

Closed by the owner on October 11, 2026. Owner confirmed the final cold Creative
return works after the tracking correction. No staging, commits, merges or pushes
were authorized or performed. Closure does not imply every manual scenario was
individually witnessed or that the full native suite is green.

Playable mice, ghost-role listing/teleport/takeover, ordinary-mob ownership,
death/reconnect and development Creative return are implemented. Accepted R2
mouse model, animations, no first-person arms, stable white/brown/gray variants,
speech, actual-health display and shared handless ingestion remain in place.
Cheese and nutritious pumpkin pie slices are available.

## ARCHITECTURE

- Resolved typed character inheritance supplies mouse composition and live
  physiology/movement policies. Mouse has no fabricated hands or human profile.
- `ghost_roles` catalog admits loaded, Alive, unclaimed eligible bodies in the
  same level. Teleport does not claim. Takeover reuses lifecycle/profile CAS and
  Offer/OfferReady → Begin/Ready/Commit; successful takeover relinquishes the
  previous claim, failure preserves the ghost. Persistent ordinary-mob binding
  alone is not an active claim. Custom player-character harnesses are excluded
  from ordinary role listings.
- Canonical control takes precedence over legacy medical observer hints. Ghosts
  have no effective carrier medical/physiology HUD. Shared owned-view tracking,
  tickets and renderer anchoring keep the camera independent of the carrier.
- `RETURN_WAITS` in the existing reconnect service automatically resumes one
  Creative return request. A scoped exact-chunk ticket is held until completion
  or cancellation, including logout/stop cleanup. Creative inventory/mode stay
  untouched until exact saved-body proof; existing handoff machinery is reused.
- `ObserverTracking` suppression validates the actual Minecraft tracking view
  and ownership identity (level, family, epoch, target UUID), not only its cache.
  New ownership at the same center explicitly resynchronizes the client center.
  Vanilla view application otherwise omits that packet when server centers match.
- Oral food/drink uses shared authority, timed transactions, per-stack solutions
  and cleanup. Supported ray-selected food outranks its underlying puddle;
  authoritative reach/LOS checks remain intact. Alt+E/Alt+left-click are the
  handless gestures, with no plain-click/E fallback.

## IMPORTANT FILES

Java paths below are under `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/ghost_roles/`: catalog, networking and compact draggable grouped picker.
- `ms14/player_body_control/lifecycle/server/`: `LifecycleExistingBodyReconnect`,
  `LifecycleDevelopmentMode`, `LifecycleCharacterSessionControl` and takeover.
- `ms14/player_body_control/client/GhostControlClient.java`: readiness,
  prediction, owned look and transition-only control diagnostics.
- `ms14/medical_observer/`: `ControlledViewTarget`, `ObserverTracking`,
  `ObserverChunkTickets` and effective observer scope.
- `mixin/client/LevelRendererOwnedViewAnchorMixin.java`: camera-relative render
  grid placement and rebuild invalidation; movement algorithms are unchanged.
- `ms14/ingestion/`, `ms14/timed_interaction/`,
  `ms14/hands/client/BodyHandClient.java`: admission, targeting and transactions.
- `ms14/damage/DamageSystem.java`: authoritative known-zero ledger preservation.
- `eventhooks/TickHooks.java`: hydration-edge termination between metabolism
  and thirst decay.
- `src/main/resources/data/moonstation14/moonstation14/`: resolved character,
  organ, reagent and ingestion definitions; `item/custom/PrototypeEdibleItem`
  initializes profile-derived food portions.

Supporting indexes: `VALIDATION.md`, `MANUAL.md`, `ROLE-BINDING.md`,
`PHYSIOLOGY.md`, `CORE-TRANSITION.md`; some supporting entries describe earlier
milestones. Food mapping: `docs/pumpkin-pie-slice-parity.md`. Timed ingestion
correspondence: `docs/timed-interactions-ingestion-sprint/PARITY.md`.

## SS14 CORRESPONDENCE

Reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, checkout
`C:/Users/William/Documents/SS14-dev/space-station-14`.

Mouse maps `MobMouse` → `SimpleMobBase`/`BaseMobAnimal` to supported local
inheritance. Full upstream heart/liver/kidney composition is not implemented;
local live animal lungs and shared stomach/bloodstream physiology are supported.
SS14 assets retain attribution; mouse squeak provenance remains recorded in
`docs/audio-unknown-provenance.md`. Accepted R2 3D presentation supersedes early
placeholder/world-sprite documentation.

SS14 stomach capacity is 50u for both humans and animals. A stomach-filling final
partial dose stops repetition. Stop-at-maximum-hydration is a deliberate local
adaptation; FOOD remains allowed there. Vitamin healing/bleeding effects remain.

Pumpkin pie slice preserves upstream 7.5u Nutriment, 3.5u Vitamin, 1.25u Sugar.
Upstream nominal capacity is 7u despite 12.25u initial contents; local strict
source capacity is 12.25u, a documented capacity/refill departure, not a buff to
metabolism. Its SS14 sprite attribution is retained beside the texture.

## DECISIONS / WHY

- Mouse walk/sprint 2.5/5 with grounded conversion 1.25× yields 3.125/6.25
  blocks/s; collider stays 0.4 × 0.3 × 0.4. Slip immunity is intentional.
- Health is remaining allowance to Critical: Human 100/100, Mouse 10/10; Mouse
  Dead threshold is 20 damage. Any existing ledger, including empty, is
  authoritative. Native estimates must not replace known zero.
- Ghost turning uses the existing carrier look accumulator consistently, not a
  replacement movement/prediction algorithm. Local timed HUD is screen-space,
  complete 24×7 texture at 48×14 GUI pixels; remote bars are unchanged.
- Cheese/pie portions are nonstacking because reagent contents belong to each
  ItemStack. Mouse hunger maximum is 35; ≤10 is Starving, not a hard hunger cap.
  Cheese-only throughput can hover there. Nutrient mixtures improve recovery
  without altering hunger or digestion rates. Starving/Parched speed factors
  are each 0.75 and multiply when combined.

## KNOWN GAPS

AI/HTN, carrying/headwear, inventory escape, unrelated Critical actions and mouse
emotes remain deferred. No guessed periodic Critical squeak was added. Reusable
NPC behavior belongs in a separate sprint.

There is no automated authenticated persisted-entity unload/reload/return test;
native ticket tests and policy tests do not establish that complete path. Owner
now confirms the connected cold-return result. Earlier intermittent Ready
timeouts and carrier-void protection lack exhaustive connected evidence. Manual
contention/repeated lifecycle cases are not individually certified by closure.

## VALIDATION

Final build: **3,058 unit tests**, zero failures/errors/skips. Filtered native
`observer,medical-state,character,lifecycle,carrier`: **146/753 selected, all
146 passed**. Full native: **749/753 passed**. Four failures remain:
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`arbitrarystonehostreplacementeventuallystopsenergizedcable`,
`livesourcesubstationapcbatteryandlampchain`,
`crushreleasecompletesnormalopenbeforefastautoclose`; causality unestablished.

Final JAR `build/libs/moonstation14-0.0.1.jar`, SHA-256:
`8153E4B8E5BE7E5D569958A4DE1D8AD3FDA828DC42EEB3685C6DAC00B33E7C27`.
Evidence in `capturebuild/validation-logs/cold-return-control-{build,focused,full}.log`;
failed full world retained, passing disposable focused world removed. Reviews,
unit XML, native summaries and hash independently checked. Three pie servings
raise test hunger 5→peak 18.64 without counting conditional Sugar satiation.

## NEXT WORK

Start from this handoff; use `VALIDATION.md` only for detailed evidence/history.
Investigate full-suite power/airlock failures separately. If control regresses,
retain matched `[harness control]`, `[harness ready]`, `[harness tracking]`,
`[controlled chunks]` and `[controlled view]` logs before changing movement.
