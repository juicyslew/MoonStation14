# Beakers and ChemMaster 4000

## STATUS

Closed by the owner on **2026-10-10**. Usable beakers, powered ChemMaster input/
buffer workflow, shared liquid interactions, compact nonmodal UI and chemistry
fixture are implemented. Later native machine models, wall-mounted APC and
lighting are indexed in `../machine-model-integration/HANDOFF.md`.
Changes remain uncommitted; no commit, push, reset or discard was performed.
Closure does not make unresolved aggregate tests or unobserved transport/GPU
behavior green.

## ARCHITECTURE

Typed inherited beaker/ChemMaster prototypes drive live capacity, solution
capabilities, removable-slot admission, receiver load, reach and UI policies.
Standard/large beakers use 50u/100u capacities, six fill tiers and blended mixture
tint through existing item model/property/color services. Shared liquid routes
consume resolved capabilities; ordinary held-container pouring and timed drinking
default to 5u. Primary target-use checks optional medical membership, not a strict
medical lookup that rejects ordinary jug/beaker items before liquid transfer.

`ChemMasterBlockEntity` owns a removable input slot and a separate persistent,
unlimited, nonreacting buffer. Existing item ownership ledger/slot services govern
handoffs, ejection fallback and suspension/restore. Exact hundredth-unit arithmetic
conserves reagents; returning to an ordinary container invokes normal reactions.
Recipe audio emits after successful commits, not inert buffer storage.

Power uses existing APC allocation: UI processing requires fresh full delivery of
the resolved **250 W** receiver load. Manual insertion/ejection and whole-world-
container dumping are not power-gated. Block-item drops retain slot/buffer data;
new placement identity differs from chunk persistence identity.

Independent viewer sessions use controlled-body authority and placement identity.
The workspace is nonmodal: movement, looking away, idle, sibling focus and mouse
lock changes do not close it. Genuine power/range/action/body/world invalidation
does. Revisioned requests and full snapshots remain authoritative. Snapshot ACKs
retain each viewer's LAST_REQUEST watermark so reinsertion/unsolicited refresh
cannot revive a completed request as permanent pending.

## IMPORTANT FILES

Under `src/main/java/com/juicyslew/moonstation14/ms14/`:

- `chemistry/chemmaster/`: typed data/merge/catalog, block/BE, buffer, control DTOs,
  session/network services and audio. `client/`: Layout, Overlay and Workspace.
- `chemistry/chemmaster/fixture/`: fixture service/commands.
- `chemistry/chemmaster/interaction/`: dump capture, service and world transaction.
- `hands/client/`: physical gesture routing and matching BEGIN-ACK release queue;
  `hands/live/`: shared liquid transfer, ingestion and target reach.
- `hands/network/BodyHandRequestService.java`: primary USE_TARGET medical fallback.
- `reagent/`: concise `ReagentUnits` formatting, policy/transaction/reaction paths.
- `player_body_control/lifecycle/server/LifecycleCharacterSessionControl.java`:
  readiness-timeout parking; details in `../lifecycle-ready-timeout-fix/`.

Data/resources: `src/main/resources/data/moonstation14/moonstation14/` typed
prototype families; beaker models/textures and mixer resources under
`assets/moonstation14/`. `COMPOSITION.md` indexes verified inheritance/system
sources. `VALIDATION.md` preserves historical results; `MANUAL.md` has client steps.

## SS14 CORRESPONDENCE

Reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` at
`C:/Users/William/Documents/SS14-dev/space-station-14`.
ChemMaster → BaseMachinePowered/ConstructibleMachine; Beaker/LargeBeaker →
BaseBeaker → BaseItem. Relevant YAML is under
`Resources/Prototypes/Entities/Structures/Machines/chem_master.yml` and
`Objects/Specific/Chemistry/chemistry.yml`; Jug composition is in
`chemical-containers.yml` and the inherited drink prototypes.

Input preserves container order; buffer sort cycles Oldest First → Alphabetical →
Quantity → Recent First. Presets: 1, 5, 10, 15, 20, 25, 30, 50, 100, All.
Transfer/discard affect both lists; buffer reactions are intentionally suppressed.
Fullbright mixer screens follow power, loaded appearance follows occupancy;
there is no invented working timer. Assets/attribution are retained; unclear audio
provenance remains in `../audio-unknown-provenance.md`.

## DECISIONS / WHY

UI is 338×272 logical / 169×136 GUI pixels, five Container and eight Buffer rows
with independent interior-clipped scrolling. Scissors use absolute GUI coordinates
because Minecraft 1.21.1 ignores pose transforms for `enableScissor`. Right-side
tabs join the pane; title has no redundant divider. Sort follows Buffer heading;
Transfer/Discard align right to the buffer panel. Dose buttons stretch from the
existing first-button anchor to each measured amount label. Labels use concise
decimals plus `u`; dose buttons do not. Inserted caption uses a positive layout
rectangle; empty slots omit volume/capacity. Locked mouse disables hover/tooltips.

Dump defaults to **Shift + left drag**. Short clicks examine the captured source,
never drink; Ctrl pulling remains unchanged. Owner-approved reach adaptation uses
ordinary pickup reach (5 blocks), nearest target bounds and independent loaded/
access/LOS checks at both endpoints, not upstream 1.5 units. Cursor nomination is
not authority. Early releases queue until matching BEGIN acceptance; cancellation,
replacement and context loss discard intent. Guards were not weakened.

## KNOWN GAPS

- Output production/pills/bottles remain deferred. Owner accepted leaving the
  currently non-opening Output tab unchanged.
- Reported `no active dump capture` remains unproven. Short-token client send/ACK/
  cancel and server removal-reason diagnostics exist; preserve incident debug logs.
- Examination target priority is deferred, not fixed; estimate **3–6 active hours**.
- Readiness timeout now parks OFFLINE through clean logout before retirement.
  Durable reconnect regression tests pass, but actual connected-client populated
  inventory/body round-trip remains unobserved; no legacy ACTIVE-claim migration.
- Authenticated multiplayer convergence/gesture transport and final GPU behavior
  are not established solely by headless checks. Mojang DNS incident origin remains
  unconfirmed without full logs.

## VALIDATION

Final clean build/unit/GameTest compilation: **2,979 unit tests passed**, no errors,
failures or skips. Latest focused APC/model run **10/737 selected, 10 passed**.
Latest full native before final cosmetic brightness increase **734/737 passed**;
three unresolved power/airlock failures are listed in the machine-model handoff.
Focused success and isolated passes do not prove aggregate failures unrelated.

Fixture: `/ms14 chemmaster_fixture build [origin]` / `status [origin]`; real power,
17 supplies (seven beakers, ten 200u jugs), six-species sample and sodium/chlorine
reaction demonstrations. Use a fresh loaded unobstructed footprint; fixtures do
not force power or alter owner configuration.

Final JAR `build/libs/moonstation14-0.0.1.jar`, SHA-256:
`0295543CB6B3D15AF07CD0456A0D755B7F67002CB4AED77E8601F05ED5C55622`.
Final logs: `capturebuild/validation-logs/apc-light-brightness-*.log`; earlier
chemistry/interaction logs are indexed by `VALIDATION.md`.

## NEXT WORK

Start from the handoffs, then preserve an actual dump incident trace before
changing capture lifecycle. Classify aggregate native failures separately. Output
production and examination priority require a separately scoped future task.
