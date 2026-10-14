# Validation status

Sprint closed by the owner on October 11, 2026 after confirming the final cold
Creative return works. See `HANDOFF.md` for current state and retained gaps.
Entries below preserve validation history; closure does not certify every
manual scenario or make the full native suite green.

## Latest automatic Creative return

**Connected regression resolved by owner retest:** owner previously reported far return teleports to the
mouse but movement/look remain frozen; a second Creative→return while loaded
restores both. Current client log at 18:32:42 shows epoch 8 Ready/tracking success
on reloaded entity ID 17 (previously 3), followed by `cameraChunkCached=false`.
Warm epoch 10 return at 18:32:53 succeeds with `cameraChunkCached=true`.
These establish a post-handshake regression, not a demonstrated Ready timeout;
the exact client packet/removal sequence was not established. After the tracking
correction, the owner confirmed the return works and explicitly closed the sprint.

Follow-up source audit: `ObserverSystem.tick` updates tracking every server tick,
so a missing immediate return hook alone is not an established cause. However,
`ObserverTracking.CENTERS` can suppress an update without checking Minecraft's
actual `ServerPlayer.getChunkTrackingView()`. Workspace Minecraft 1.21.1
`ChunkMap.java:1060–1087` verifies that view application sends the cache-center
packet only when the prior server view center changes; reapplying an identical
view does not resynchronize a client center. The service-level correction now
validates actual center/radius and ownership (level, family, epoch, target UUID),
and resynchronizes the client center once on same-center ownership changes.
Focused tests cover stale-view repair, ownership resync, unchanged suppression,
and radius/level changes. Connected resolution is owner-confirmed; the exact
incident packet sequence remains unverified. Transition-only `[harness control]` client and
server intent/motor diagnostics distinguish entity lookup/policy loss, other
control gates, and accepted versus applied intents without changing movement.
First-accepted-intent logging checks the intent gate's actual acceptance result.

Latest follow-up validation: **3,058 unit tests, zero failures/errors/skips**;
filtered `observer,medical-state,character,lifecycle,carrier` native run
**146/753 selected, all 146 passed**. Full native **749/753 passed** with failures
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`arbitrarystonehostreplacementeventuallystopsenergizedcable`,
`livesourcesubstationapcbatteryandlampchain`, and
`crushreleasecompletesnormalopenbeforefastautoclose`; causality unestablished.
Tracking/diagnostic source reviews passed; unit XML, native summaries and JAR
hash independently checked. Logs:
`capturebuild/validation-logs/cold-return-control-{build,focused,full}.log`.
Passing focused fixture removed; failed full world retained. Latest JAR SHA-256:
`8153E4B8E5BE7E5D569958A4DE1D8AD3FDA828DC42EEB3685C6DAC00B33E7C27`.
Retest a single cold return, then movement/turning. If still frozen, preserve
matched client/server `[harness control]` logs; this build is not connected proof.

`/ms14dev return` previously requested the saved FULL chunk but immediately
checked its entity lookup, before asynchronous saved-entity loading completed.
`RETURN_WAITS` recorded the wait without resuming it on server ticks, requiring
another command. The existing wait now resumes automatically through the same
exact-body proof and return handoff; the chunk-loading/retry chat is removed.
A scoped, nonexpiring lifecycle ticket retains the exact saved dimension/chunk
while waiting, then releases on completion, cancellation, logout or server stop.
This extends existing lifecycle state rather than adding a parallel scheduler or
using observer tickets. Creative inventory/mode remain unchanged before body
proof. Repeated requests retain the original wait and deadline.

Source review passed. Final build passed **3,056 unit tests, zero
failures/errors/skips**. Filtered native `lifecycle,carrier,character` run:
**112/753 selected, all 112 passed**. Full native run: **753/753 selected,
749 passed, four failed**. Independently checked logs show the failures are
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
`logpulsedisableslampandrestoresatresolveddeadline`, and
`crushreleasecompletesnormalopenbeforefastautoclose`. These are power/airlock
failures, but not the exact previous four; causality is unestablished. Failure
fixture retained, passing focused fixture removed. Evidence:
`capturebuild/validation-logs/creative-return-{units-build,focused,full}.log`.

Policy regressions cover automatic resume/cancellation/timeout; the native
fixture verifies exact-chunk ticket retention. **Real persisted-entity unload
and original-UUID reload, and authenticated connected return, remain unverified.**
Retest one return request after flying far from the saved body in Creative.
Latest JAR: `build/libs/moonstation14-0.0.1.jar`, independently verified SHA-256
`BCFBE9AC2809D5FAC5549D1F1A98E11CDDA3F5307690CD38B5D9AB8D00F07180`.
No commits, owner configuration/profile edits or sprint closure occurred.

## Latest pumpkin pie food addition

`moonstation14:pumpkin_pie_slice` reuses `PrototypeEdibleItem` and resolved
`food_base` ingestion defaults (FOOD, 5u/1 second, destroy when empty), with
independent nonstacking portions and creative-tab availability. Source contents
match pinned `FoodPiePumpkinSlice`/`FoodPieSliceBase`: 7.5u Nutriment, 3.5u
Vitamin, 1.25u Sugar. Full ancestor mapping and asset attribution are recorded
in `docs/pumpkin-pie-slice-parity.md`. Upstream declares 7u capacity despite
12.25u initial contents, which its solution initialization retains; the local
strict source invariant requires 12.25u capacity. This capacity/refill departure
preserves the whole serving, not an upstream automatic capacity increase.

No hunger, digestion, cheese or reagent-effect values were changed. Three
resolved pie servings restore Mouse hunger from 5 to a peak **18.64** in the
shared digestion/metabolism/hunger regression, conservatively excluding Sugar's
conditional satiation. The 3,675 source cents account for 1,764 metabolized body
cents and 1,911 cents of expected digestion efficacy/quantization loss. Earlier
test equality incorrectly assumed no absorption loss; no production-rate change
was used to fix it. Native handless intake initializes the full source, accepts
a 500-cent bite and retains 725 source cents; FOOD remains admissible at the
hydration cap. Vitamin's existing healing/bleeding effects are retained.

Source/asset/test reviews passed. Final serial build passed **3,052 unit tests,
zero failures/errors/skips**. Final broader filtered native selection:
**70/752 selected, all 70 passed**, tags `food,handless-ingestion,ingestion,
mouse-ghost-roles,mouse-appearance,medical-state`. Separate capacity/timed check:
**60/752 selected, all 60 passed**. The recurring capacity fixture was aligned
away from its body's metabolism bucket across the completion edge; it again
asserts exact 50-cent intake, 500→450 source conservation, full stomach, terminal
completion, unchanged source after later natural digestion, and new-gesture
resumption. No production rate changes or weakened transfer assertion were used.

Unit XML, native summaries and JAR hash independently verified. Retained evidence:
`capturebuild/validation-logs/pumpkin-pie-food-focused-final.log`,
`pumpkin-pie-food-capacity-final.log`, and the matching saved nutrition/profile
unit XML in that directory. Passing disposable fixtures removed. Data/item-only
production changes did not repeat the full native suite; its latest prior result
remains 746/750 with four unresolved power/airlock failures. New food's visual and
connected eating acceptance is pending; no graphical client was launched.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`C0183A145504D29BE15645FCCE503891AB6B2B04EA1E778B4A9BF4808CF5F869`.
Sprint remains active; no staging/commits/merges or owner world/profile edits.

## Latest controlled terrain render-grid / food-over-puddle follow-up

Owner reports the remaining terrain issue, then adds food-over-puddle targeting.
Actual Oct. 11 client diagnostics show cached camera chunks with camera/cache
centers moving while the carrier stays elsewhere. Minecraft 1.21.1
`LevelRenderer.setupRender` uses actual `Camera.position` for culling but carrier
player X/Y/Z for section-change detection and `ViewArea.repositionCamera`.
The client-only owned-view mixin now feeds the same actual camera coordinates
to those three grid inputs for a committed owned camera, retaining vanilla
fallback. `allChanged()` rebuilds invalidate cached section coordinates so the
next render setup also corrects the newly rebuilt grid. No movement, prediction,
server tickets or chunk-cache loading was changed. Source and bytecode targets
were verified against the workspace NeoForge artifacts; live rendering remains
unverified. Existing diagnostics now include effective renderer position.

Both alternate-use client paths now use shared source selection: a supported
ray-selected item takes precedence over its underlying puddle, with puddle
fallback otherwise. Existing authoritative COLLIDER targeting already handles
the non-colliding puddle without weakening wall/reach checks. A new native
fixture ingests food above a puddle while preserving the puddle contents.
Source reviews passed. Final serial build passed **3,051 unit tests, zero
failures/errors/skips**. Filtered native run: **205/751 selected, all 205 passed**,
including food-over-puddle ingestion. Logs:
`capturebuild/validation-logs/owned-render-anchor-food-puddle-{build,focused}.log`.
Unit XML, native pass summary and JAR hash were independently checked. Client-only
production changes did not rerun the full native suite; its latest prior result
remains 746/750 with four unresolved power/airlock failures recorded below.
Passing disposable focused fixture was removed. No graphical client or runtime
Mixin application was verified; owner rendering/input retest is still required.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`F4A0C4EBCB6E7573F7A863688D0DB867C750387D8A411BD2545877649B9EF139`.
Sprint remains active; no commits, merges or owner configuration/profile edits.

Owner confirmed a mouse can fill its stomach with food and leave the unconsumed
food remainder. Hunger cap is **35**, not 10; **10 or below is Starving**.
Cheese-only recovery near that boundary follows a single-Nutriment throughput
limit, not a hard cap: upstream default digestion transfers 0.25u/s at 0.5
efficacy, yielding 0.125u/s; Nutriment hunger effect is
`1.5 × (0.125 / 0.5) = 0.375/s`. Local cent quantization yields 0.12u/s and
0.36 hunger/s. Both are above Starving decay (0.30/s) but below Peckish decay
(0.40/s), explaining the cheese-only boundary under the pinned defaults.
No food/rate buff was introduced. Upstream references: `MetabolizerComponent`
default solutions/rates, `MetabolizerSystem.TryMetabolizeStage:184–195`,
`SatiateEntityEffectSystem:29–31`, and `FoodCheeseSlice` (3.75u Nutriment).
Earlier research asserting any positive partial dose applies the full 1.5 was
incorrect; effect scale is proportional to the actual removed dose.

Nutrition slowdown is consumed by the live controlled movement snapshot/motor
and matching client prediction, not merely native attribute modifiers:
Starving ×0.75, Parched ×0.75, both ×0.5625. Comparisons within one hunger band
do not change speed. In-game measured-distance verification was not performed.
Mouse AI remains deferred; no NPC framework or extra organ components were added.

## Latest ghost-look / drink-stop / HUD regression follow-up

The preceding artifact regressed ghost turning: canonical observer-state filtering
hid the state used by legacy mouse classification, while owned look projection
and movement intents still consumed raw cached observer angles. Canonical ghosts
now use the existing carrier mouse-look accumulator consistently in redirect,
projection and intents. Medical filtering stays in place; no movement motor,
speed or prediction algorithm was replaced. Source review passed. Routing tests
are source-contract checks, not live turning proof; reported stutter still needs
connected verification.

The GUI frame had incorrect source/destination blit geometry. It now draws the
complete 24×7 texture into a 48×14 destination using the explicit destination-size
overload (2× instead of 3×), retaining the lower-screen anchor and remote bars.
Source/texture geometry review passed; connected appearance remains pending.

Actual endless-drinking root: reagent metabolism and thirst decay run in the same
20-tick activity bucket. Metabolism can reach maximum hydration, then decay lowers
it before timed validation observes it. A post-metabolism/pre-decay check now
selectively terminates that body's voluntary DRINK operations through the shared
timed runtime; FOOD and unrelated actions remain active. Completion also checks
actual accepted stomach quantity and post-commit room. The old partial-capacity
comparison already stopped a partial final dose; it was not the hydration bug.
New Human/Mouse fixtures exercise the real activity order, continued natural
decay after stop, and fresh-gesture resumption; both assert active drinking and
below-cap hydration immediately before metabolism. Their focused checks passed.
Initial serial build passed 3,047 unit tests with zero failures/errors/skips.
Initial filtered native run: 202/750 selected, 201 passed; initial full native:
750/750 selected, 748 passed. The new
`finalpartialcapacitydosestopstimedpuddleoperation` failed with "No value present"
in both runs; its fixture was corrected to align with the actual operation
deadline and keep hydration below cap for this stomach-capacity test. It still
exercises the production timed transfer and verifies exactly 50 cents accepted,
450 source cents remaining, terminal completion and distinct-gesture resumption.
Full also failed
`crushreleasecompletesnormalopenbeforefastautoclose`; causality is unestablished.
Logs: `capturebuild/validation-logs/ghost-look-drink-stop-hud-{build,focused,full}.log`.

Final serial build: **3,047 unit tests, zero failures/errors/skips**. Final
filtered native: **202/750 selected, all 202 passed**. Final full native:
**750/750 selected, 746 passed, 4 failed**:
`arbitrarystonehostreplacementeventuallystopsenergizedcable` (lamp unlit before
replacement), `livesourcesubstationapcbatteryandlampchain` (lamp unlit after HV
break), `liveapcmeterdrivesscheduledtwophasedoorandautoclose` (deadline not
reached), and `logpulsedisableslampandrestoresatresolveddeadline` (intact log
circuit). Causality remains unestablished; no unrelated power/airlock fixes were
included. Concurrent FOOD preservation at the hydration-cap edge is covered.
Final scoped source/fixture reviews passed; unit XML and JAR hash independently
verified. Logs: `ghost-look-drink-stop-hud-build-final.log`,
`ghost-look-drink-stop-hud-focused-negative-control.log`, and
`ghost-look-drink-stop-hud-full-final-negative-control.log` under
`capturebuild/validation-logs/`. Confirmed passing disposable fixture directories
were removed; failed artifacts retained.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`07C1300B6847A93A09F2F8464BE6C9865B82B6276FC299A082BCE302B7DCBEF3`.
Connected ghost turning/stutter, final HUD appearance and actual mouse drinking
still require owner acceptance. Authenticated live-owner carrier-void behavior,
invisible terrain and prior handshake timeouts remain unresolved. Sprint stays
active; no commits, merges or owner world/profile rewrites.

Upstream clarification: pinned `IngestionSystem.cs:368–391` compares the original
requested transfer to available volume and stops when it fills the stomach,
including a partial final sip. Puddle explicitly resolves Drink, 1u/3 seconds
(`Resources/Prototypes/Entities/Effects/puddle.yml:204–209`). Earlier research
claims of a repeating capacity-limited dose and absent puddle timing were wrong.
Both inherited stomach capacities remain 50u; local max-hydration stopping is
still a deliberate departure. Mouse slip immunity is unchanged.

## Latest known-zero authority / transport-carrier / observer-scope corrections

Concrete health root cause: `DamageSystem.ensureBaseline` deleted present empty ledgers during routine checks, despite enrollment/Ready publication. All baseline import, preview and reconciliation paths now treat any present ledger—including known-zero—as authoritative. Missing state remains distinct. Repeated baseline/reconciliation regression passed. Related acid/vacuum fixtures now distinguish deliberate legacy native-health import from authoritative medical state rather than setting contradictory native-only health.

Canonical CHARACTER HUD bodies take precedence over legacy observer hints; canonical GHOST has no physiology/status/health HUD. `ObserverClient` exposes only effective medical state matching canonical ownership: stale Critical/Corpse snapshots cannot leak through public getters, block ghost interaction, or change camera/look. Canonical ghost examination remains supported independently of the legacy observer snapshot.

Committed physical transport carriers with a distinct owned body reject incoming native damage before baseline projection, and do not run medical activity ticks. Actual character bodies retain normal physiology and damage. Carrier-policy/source-selection tests passed, but **authenticated live-owner void damage and continued ghost control are not independently proven**; connected retesting is required. Existing world/profile records were not repaired or rewritten.

Local progress HUD is now 3× the authored base (**72×21 GUI pixels**), near the original lower-screen anchor (~85% viewport height), with viewport clamping. Remote world bars are unchanged. Mouse general passive healing was not established; configured asphyxiation recovery is 0.25 per update and blood-loss recovery is type-specific. No blanket regeneration nerf/buff was added. Periodic dying squeaks reported by the owner remain unlocated in the pinned revision/history checked; no guessed loop was introduced.

Final serial build: **3,045 unit tests, 0 failures/errors/skips**, including `ObserverMedicalScopePolicyTest` (3/3). Broad filtered native rerun: **425/747 selected, all passed**. Final observer/character/medical/timed filtered rerun: **167/747 selected, all passed**. Latest full native: **747/747 selected, 745 passed, 2 failed**, `arbitrarystonehostreplacementeventuallystopsenergizedcable` and `liveapcmeterdrivesscheduledtwophasedoorandautoclose`. Failure causality remains unestablished. The final client-only observer-scope changes did not repeat the full native run. Logs: `capturebuild/validation-logs/carrier-isolation-known-zero-hud-build-final.log`, `carrier-isolation-known-zero-hud-observer-focused-final.log`, and the related focused/full logs in that directory. Final source reviews passed; XML and JAR hash independently checked. Failed full-run artifacts retained.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `5B8AC33EB59A897BFDCD0BA928B93972E299CE438E5A30B2E717C6AAB914DBA0`. Actual connected health display, ghost isolation/void control and HUD size/placement still require owner acceptance. Invisible terrain remains unresolved. Sprint stays active; no commits/merges.

## Latest Ready-publication / GUI-action / ghost-isolation follow-up

Initial medical state is republished through the existing synchronized damage attachment after CHARACTER Ready commits, when the client has confirmed the body exists. Publication preserves present ledgers, including known-zero, rather than importing a native-health estimate over them. New live coverage verifies known-zero and nonzero preservation. Earlier enrollment-time publication alone did not resolve the owner's pending readout; connected initial delivery after this correction still needs verification.

Local owned timed actions now use a viewport-anchored GUI overlay driven by the existing timed tracker, not camera-relative world geometry. Remote actors retain world bars. Layout/stacking tests are camera-height/pitch independent; actual look-down visual acceptance remains pending.

Mind-controlled sessions with no committed character body no longer select carrier physiology/status for the alerts HUD. Observer bodies also now require custom persistence, preventing vanilla distance despawn independently of NoAI; explicit lifecycle discard remains valid. Ghost damage rejection already existed. Below-world survival and explicit cleanup fixture coverage passed; the owner's exact void/death-sound failure sequence was not independently reconstructed.

Owner confirms mouse speech squeaks and smooth mouse movement. Pinned SS14 mouse composition does not automatically squeak on Critical entry or while Critical; normal critical speech/emotes are blocked, and configured deathgasp is Critical → Dead. No automatic critical squeak loop was added.

Latest serial build: **3,038 unit tests, 0 failures/errors/skips**. Filtered native: **189/746 selected, all 189 passed**, tags `character,damage,observer,timed-interaction,ingestion,handless-ingestion,mouse-ghost-roles,mouse-appearance`. Full native: **746/746 selected, 745 passed, 1 failed**, `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` (unknown APC output path). Failure remains unresolved; causality is not established. Logs: `capturebuild/validation-logs/initial-health-hud-ghost-{build,focused,full}.log`. Source review passed; unit XML and JAR hash independently checked. Failed full-run artifacts retained.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `5DD9ABE8479338E38FFD1ADFF52B2B0635C85EF778489BDF4DD13DE631E4C69E`. Invisible terrain remains unresolved with client cache diagnostics available. Sprint stays active; no commits or merges.

## Latest medical-publication / audio / chunk-diagnostic follow-up (Oct. 11)

Medical ledgers now retain known-zero values in serialization and explicitly synchronize initial enrollment, damage updates and healing back to zero through the existing attachment API. Persistence/stream-codec tests cover zero, damage and healed-zero; enrollment fixtures preserve a present empty ledger after healing. This addresses the missing publication path behind permanent pending HUD values, but actual connected-client delivery must still be retested.

Received mouse speech now uses client-local positional sound playback instead of the client-side `playSound(null, …)` call. Food/drink kind flows from the resolved profile through the existing successful timed commit: FOOD uses the imported SS14 three-sample Eating collection, DRINK retains its prior cue. Attribution uncertainties are recorded in `docs/audio-unknown-provenance.md`. Self-bubble suppression is unchanged. Client audio playback remains unverified.

Mouse smooth movement is owner-confirmed; starting-character jitter was attributed by the owner to external lag and withdrawn. Neither motion path was changed in this follow-up. Invisible terrain is **unresolved**: vanilla source inspection confirmed shared server view changes send matching cache-center packets and watch changes. Client-only `[controlled chunks]` diagnostics observe packet processing and camera/carrier/cache state transitions, including cache availability after chunk data arrives. They do not force loading or claim a rendering fix.

Latest serial build: **3,037 unit tests, 0 failures/errors/skips**. Filtered native: **169/745 selected, all 169 passed**. Full native: **745/745 selected, 743 passed, 2 failed**: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, `arbitrarystonehostreplacementeventuallystopsenergizedcable`. Power failures remain unresolved; causality is not established. Logs: `capturebuild/validation-logs/health-sync-audio-chunks-{build,focused,full}.log`. Final scoped source review passed; current unit XML and artifact hash independently checked. Failed full-run artifacts retained.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `9B97BE2A67298661C92C0005435728D632F2E779796D8C0D874AF85852D10A74`. Sprint remains active; no commits/merges or connected graphical acceptance claimed.

## Latest prototype-health / cheese-portion corrections

Mapped medical actors no longer silently switch to native Minecraft health when their client damage ledger is unavailable: current health is pending at the resolved prototype maximum until authoritative data arrives. Server enrollment publishes a known-zero synchronized `DAMAGE` attachment only for living actors with resolved medical policy and no existing ledger, including idempotent enrollment. Existing damage survives re-enrollment. Thus resolved Human and Mouse scales remain **100** and **10**, rather than native **20** and **4**. Actual connected initial delivery still needs retesting.

Cheese's former 16-count stack shared one 3.75-unit reagent source; slices now have max stack size 1, matching individual upstream food entities and the current per-stack source representation. Sixteen-portion coverage verifies consuming one removes only that entity while the other fifteen retain independent full sources. Use newly obtained portions for retesting; no old-stack migration was introduced. Nutrition thresholds remain unchanged, but zero hunger/thirst HUD labels are **STARVING/PARCHED**, not a claim of mob death. Gait is unchanged from the approved generic implementation.

Latest full serial build: **3,036 unit tests, 0 failures/errors/skips**. Filtered native: **186/745 selected, all 186 passed**. Full native: **745/745 selected, 743 passed, 2 failed**: `connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt`, `lampExtensionReceiverUsesNearestKnownLvCableWithinThreeBlocks`. These APC/cable failures remain unresolved; causality is not established. Logs: `capturebuild/validation-logs/health-ledger-cheese-portions-{build,focused,full}.log`. Final scoped source review passed; XML and artifact hash independently checked. Failed full-run artifacts retained.

Latest JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `A4DDBAB8B99F94EEA48DE84E63E118EA19A0D2A9932122AE6D071B52485AC823`. The sprint remains active, connected acceptance pending, with no commits or merges.

## Generic creature presentation follow-up

Health selection is generic: resolved medical thresholds plus an available typed damage ledger take precedence; a missing ledger is not interpreted as zero damage. Otherwise the controlled creature's native health/max health is displayed, never the spectator carrier's. Incorporeal observers do not receive that fallback. Mouse cadence consumes its projected actor identity rather than its host-type mapping. Shared `OwnedHarnessWalkAnimation` provides measured-displacement cadence for authored clips and distance-driven native walk animation for other creatures; native gait receives no duplicate speed multiplier. Mouse-specific pose selection remains in the mouse model.

Latest serial build: **3,036 unit tests, 0 failures/errors/skips**. Filtered native: **67/743 selected, 67 passed**, tags `movement,mouse-ghost-roles,mouse-appearance`. Logs: `capturebuild/validation-logs/generic-creature-health-gait-{build,gametest}.log`. Independent review passed; current XML and JAR hash checked. Presentation-only changes did not rerun the full native suite; its latest result below remains 738/743 with five unresolved failures.

Latest artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `BD7334E9104770CD216FE45BCD26C625F8EBBF7CBDEEC2CF6501EDFFAF67CF68`. Connected visual/speech/movement acceptance remains pending; no sprint closure or commits.

## Latest actual-health / speech / drinking follow-up

- Medical HUD now shows remaining health against the resolved Critical threshold (healthy Mouse **10/10**), retaining authoritative Critical/Dead state labels and the 20-damage death threshold.
- Mouse gait cadence follows measured displacement relative to resolved grounded walk speed; walk/run scale the existing scurry clip without changing physical speeds.
- Lifecycle speech previously rejected ordinary Mob bodies before prototype admission. It now uses generic body admission and the canonical binding accessor; connected chat/bubble/audio still needs retesting.
- Native velocity packets now defer to the existing custom movement snapshot for the exact locally owned prediction target. Remote entities keep vanilla handling. This is a motion-boundary correction, not confirmed resolution of connected jitter.
- Resolved FOOD/DRINK classification preserves inherited food settings. Voluntary drinks stop at the resolved hydration cap at admission/completion; FOOD remains available. This is an owner-requested departure from upstream. Mouse and Human fixture coverage includes cap rejection and mid-action cancellation without further source transfer.
- End-to-end nutrition regression covers five cheese portions through stomach transfer, loaded Nutriment metabolism/effects and Mouse hunger decay. Each slice contains 3.75 units Nutriment; 25 source cents transfer as 12 body cents per one-second pass. The default effect applies `1.5 × removed / 0.5`, not simply 1.5 per ingested unit. Five slices yield **27 gross hunger over approximately 75 seconds**, before caps/tick ordering, while Mouse decay removes approximately 37.5. Upstream organ/stage transfer defaults were verified; no nutrition buff was introduced.
- Ordinary speech while Critical is blocked upstream. Automatic Critical-entry squeaking was not established; no ambient loop or deferred critical-action feature was added.

Validation: full serial build **3,035 unit tests, 0 failures/errors/skips** (`build/validation-logs/mouse-actual-health-build-final.log`); filtered native **102/743 selected, 102 passed** (`mouse-actual-health-native-focused-final.log`); full native **743/743 selected, 738 passed, 5 failed** (`mouse-actual-health-native-full.log`). Full failures: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, `livesourcesubstationapcbatteryandlampchain`, `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`, `liveapcmeterdrivesscheduledtwophasedoorandautoclose`, `logpulsedisableslampandrestoresatresolveddeadline`. These power/airlock failures remain unresolved; causality is not established.

Current artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `D1232B811968171E67F2E4BF986F08E0F7A0258D1619976A6AC2BE24C0667D66`. Current unit XML and artifact hash independently checked. Ghost bleeding/slowdown and intermittent Ready timeout remain unresolved. The sprint remains active and all changes uncommitted.

Final hydration regression additionally observes production thirst decay below cap over the subsequent action/repeat window: source contents remain unchanged and the canceled action stays inactive without new input. Filtered rerun (`handless-ingestion,ingestion`): **20/743 selected, 20 passed**, log `build/validation-logs/mouse-hydration-decay-native-focused.log`. Final serial build again passed all **3,035** unit tests; artifact hash is unchanged. Independent final review passed. The full-suite result above remains the latest full run; no claim that its five failures are unrelated is established.

## Latest mouse follow-up validation

Implemented resolved, persistent/synchronized random white/brown/gray mouse appearance; composed-character speech admission; stable local first-person timed-action anchoring; explicit clinical-health percentages; and `/give @s moonstation14:cheese_slice`. Cheese uses inherited ingestion profiles, 6-unit source capacity with 3.75 units Nutriment, one-time source initialization, and existing timed transactions. Final world doses destroy sources only when the resolved profile enables `destroy_on_empty`. Existing partial/empty reagent data never refills. The existing dead pose is retained. Upstream cheese sprite and CC-BY-SA-3.0 metadata are retained.

- Full serial build: **3,033 unit tests, 0 failures/errors/skips**. Log: `build/validation-logs/mouse-followups-build-final.log`.
- Filtered native: **24/742 selected, 24 passed, 0 failed**, tags `mouse-ghost-roles,mouse-appearance,ingestion,ghost-roles`. Log: `build/validation-logs/mouse-followups-native-focused-fixed.log`.
- Full native: **742/742 selected, 740 passed, 2 failed**: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, `livesourcesubstationapcbatteryandlampchain`. Log: `build/validation-logs/mouse-followups-native-full.log`. These are power-path failures; causality with this work is unestablished, not certified unrelated. The suite remains red.
- Artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `F80B6316D0567E2661B584D9D8DF1AD94BA840AA9E68BA2FF550700EA16CF54D`. Hash and current unit XML independently checked; final source review passed.

Earlier failed follow-up checks are superseded by these reruns: schema/anchor fixture expectations were corrected, required mouse Bloodstream defaults supplied, and final-dose world-source destruction fixed. Ghost bleeding/slowdown, mouse jitter, and intermittent Ready timeout remain unresolved; transition-only diagnostics do not constitute fixes. Connected speech/audio, camera/UI and color observations remain pending. No sprint closure or commit is authorized.

## Active READY_TIMEOUT investigation

Owner reports an intermittent CHARACTER handshake timeout. Archived `run/logs/2026-10-10-1.log.gz` / `debug-1.log.gz` preserve Begin for epoch 2/body 2 at 16:26:06 followed by `READY_TIMEOUT` at 16:26:10.406; exact connection, authorization and body liveness were true. Camera remaining on the carrier and `intentEligible=false` during this handshake do not establish the failure cause. A separate 16:28 attempt in current logs completed Ready/Commit and subsequent character chunk-view movement; this does not explain or resolve the earlier failure.

No newer relevant fix was found in inspected sibling workspaces. The historical custom-character recognition/deferred-Begin fix is already present. Client `[harness ready]` state-change diagnostics now expose raw entity availability/type, recognition, spectator/context/epoch gates, Ready sends, deferred/accepted Begin and pending clears. This is instrumentation only; authority/readiness rules and timeout are unchanged. Build/review results are pending. Preserve owner logs and reproduce for matched client/server epoch evidence. The sprint remains active; no closure, handoff, merge or claim repair was performed.

## Oral ingestion, full-row targeting, gait and dev controls

Owner-requested handless ingestion extends the existing world-item/puddle timed paths and source/stomach transactions. Resolved Mouth plus absent/empty Hands grants handless oral access; stomach/body/live-action checks remain required. `supported_animal` composes Mouth. A handed Human missing live hand state is rejected; the standalone Pig currently lacks oral anatomy and is also rejected. Prototype-resolution coverage includes an inherited non-Mouse oral/no-hands composition. No Mothroach entity was introduced.

The existing Alt+E/Alt+left-click gesture nominates an item UUID or puddle position through a body/epoch/sequence request. `BodyActionAuthority`, the resolved capability and monotonic sequence gate are checked server-side. The separate oral payload is justified because hand requests/snapshots require actual hands; it supplies no phantom hand and reuses the same timed controllers/completion transactions. Held ingestion and ordinary E/Shift+E are unchanged. Follow-up pinned-source research confirmed these alternate-use bindings execute the first available AlternativeVerb, including edible self-ingestion (`Resources/keybinds.yml`, `SharedInteractionSystem`, `IngestionSystem.API`). This establishes the food gesture, not blanket puddle or verb-priority parity. Plain-click/E ingestion remains intentionally absent.

### Follow-up parity findings (implementation validation pending)

- Mouse inherits `StunImmune` through `SimpleMobBase → BaseSimpleMob`. `StatusEffectStunned` rejects that tag, and `SlipperySystem.CanSlip` requires stun eligibility. Stock mice therefore do not slip; the earlier absence-of-`NoSlip` inference was incomplete. Local mouse also fails slip admission because it has no supported `Stunnable` composition; do not add mouse slipping to fix the reported symptom.
- Ingestion does not stop merely because thirst reaches OverHydrated. Upstream clamps thirst but admission/repetition uses mouth, digestibility, source and stomach capacity. Keep this distinction from a full stomach.
- Local `100/100` medical HUD is a clinical percentage normalized against the resolved critical threshold, not native HP or damage-to-death. Mouse thresholds remain 10 Critical / 20 Dead.
- Ghost observer composition has no Hunger, Thirst or Bloodstream. Reported ghost bleeding/slowdown and mouse jitter remain unproven; new actor/tracking diagnostics are evidence gathering, not fixes. Former bodies must continue their independent physiology.
- Owner clarified that the mouse already has a dead pose. Preserve it; the proposed upside-down replacement was withdrawn.

Picker painting and hit-testing share full-row rectangles, including horizontal edges. Mouse locomotion consumes immediate body displacement instead of smoothed limb swing and uses the existing 0.2-second R2 scurry clip while moving; no animation asset or physical speed changed.

Ctrl+Shift+F6 invokes operator-checked `/ms14dev creative`; Ctrl+Shift+F7 invokes `/ms14dev return`. Registration is MOD-bus/client-only, runtime input GAME-bus/client-only. Chords are press-edge triggered and suppressed with screens open. Current/documented planned controls were checked for default chord conflicts; user rebinding can change that. Returning mapped role bodies now reacquires the existing grounded lease before strict no-AI preflight. The no-AI check and post-CAS recovery behavior remain intact; rejected startup logs its reason.

Source reviews passed. Initial validation exposed fixture/assertion issues (row indexing, client source classification, Pig anatomy/role assumptions, puddle aiming, and stomach metabolism after ingestion). Combined-run diagnostics then found test mice leaving the level through native despawn after other fixtures introduced distant players. Fixture mice are persistent, and the out-of-reach regression uses a separate item rather than perturbing the timed source. Source support and puddle collision-shape targeting keep sources valid; assertions check consumption despite metabolism. Direct inspection also caught and fixed a handless validation early-return that skipped source checks; identity, reagent and reach validation now run with or without hands. Source-loss regressions and independent review cover this. Temporary diagnostics were removed.

Minecraft source confirmed that NoAI does not stop distance despawning. Production `MouseMob.requiresCustomPersistence()` now prevents station/controlled mice disappearing far from the physical carrier, matching the existing player-body harness policy. Native mouse coverage asserts this without changing the saved persistence flag, collider, movement or AI defaults.

Final full build: **3,028 unit tests, 0 failures/errors/skips**. Focused native tags `handless-ingestion,ingestion,mouse-ghost-roles,lifecycle,character`: **130/740 selected, all 130 passed**. Full native suite: **740/740 completed, 739 passed, 1 failed**—`arbitrarystonehostreplacementeventuallystopsenergizedcable`. Its cable/APC path was unknown; causality remains unestablished, so full native validation remains red. Logs: `build/validation-logs/mouse-ghost-roles-oral-dev-gait-{build,native-focused,native-full}.log`. Full failure world: `build/gametest-mouse-ghost-roles-oral-dev-gait-full-validated`. The full run includes the persistence change; the final build was rerun afterward to package that change and independently verify all unit results and the JAR hash. Passing disposable fixtures are removed. Obsolete handless diagnostic worlds were removed after preserving their logs under `build/validation-logs`. No connected eating, Creative-return or graphical animation acceptance is claimed.

## R2 model, UI coordinates and shared view authority

The accepted R2 mouse model/animation/six textures from the sibling asset workspace are integrated, with attribution and unchanged collision dimensions. Supported synced Critical/Dead states select the authored down pose/material/gore; vanilla corpse flip is suppressed. Default white is active; optional color variants/advanced clips remain deferred.

The picker starts at the top left. Its close helper now passes the correct unscaled chrome width while retaining the GUI-space origin; the previous call placed the X around half the window width. Render and hit-test share that helper. Rows use 12px height throughout, and the missing `entity.moonstation14.mouse` language entry is supplied.

The earlier streaming correction omitted canonical lifecycle ghosts and other committed harness-control targets. `ControlledViewTarget` now resolves existing authenticated lifecycle ghost/character and core harness authorities for both chunk watching and loading tickets. Unbound legacy medical ghosts retain their owner/session validation; bound sessions cannot bypass canonical commit through that fallback. Resolver precedence/consumer-boundary tests and source review passed. View/ticket transitions are logged to aid connected diagnosis. This does not establish positive camera-packet or terrain-streaming behavior.

Final full build: **3,017 unit tests, 0 failures/errors/skips**. Focused native tags selected **90/736**, all **90 passed**. Full native suite completed **736/736: 732 passed, 4 failed**: `logpulsedisableslampandrestoresatresolveddeadline`, `liveapcmeterdrivesscheduledtwophasedoorandautoclose`, `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`, `crushreleasecompletesnormalopenbeforefastautoclose`. Failure causality remains unestablished; no unrelated fixes were made. Logs: `build/validation-logs/mouse-ghost-roles-r2-ui-view-{build,native-focused,native-full}.log`. Failed world: `build/gametest-mouse-ghost-roles-r2-ui-view-full`. Passing disposable fixture removed. Unit XML and JAR hash independently checked; no graphical/connected acceptance is claimed.

## Drag and readable-name correction

The picker requested a 64px width while its geometry minimum was still 84px. Every render therefore called `setBounds`, which cancelled pointer capture. The minimum now matches the presentation width; shared production relayout preserves placement and defers bounds changes while dragging. Close cancels controller capture, including Escape during a drag. Group headers and entity rows use localized entity-type descriptions (Pig, Villager, Mouse), retaining registry IDs only as stable grouping keys. Production-layout/capture regression checks and independent source review passed; rendered dragging/names remain pending connected retest.

Earlier drag/name full build: **3,012 unit tests passed, 0 failures/errors/skips**. Log: `build/validation-logs/mouse-ghost-roles-drag-names-build.log`. These were client-only corrections; latest model/view validation is recorded above.

## Grouped picker, teleport, nutrition and streaming follow-up

Connected feedback prompted these additional corrections:

- The picker uses expandable role-ID groups with UUID-distinct rows, half-scale text, no search field, content-driven height and approximately one-tenth viewport width (64 logical-pixel practical minimum). Narrow layouts stack actions. `GhostRoleMenuPresentation` supplies shared flattened rows for rendering, hit-testing and scroll clamping; partial trailing rows are not clickable. Existing compact chrome/geometry handle close and drag.
- Shared-workspace opening establishes automatic UI intent independently of manual L-Alt intent, so opening `/ghostroles` should release the cursor.
- Teleport places the ghost two blocks behind and one block above the target's feet, aiming at its eye. A successful correlated response carries bounded look angles; current ghost/camera checks gate local player/observer look and prediction-history resets. Vanilla entity rotation alone did not synchronize future input.
- The shared 0.75 nutrition factor supplies native attribute projections and controlled CHARACTER snapshots. Starving and parched multiply to 0.5625; the motor consumes the snapshot factor once. A live mouse fixture exercises combined/recovered states through the production actor resolver and grounded motor. No nutrition death behavior was added.
- `ObserverTracking` and the chunk-center mixin use the verified committed entity for the view center. `ObserverChunkTickets` loads that view area for controlled characters through the existing region-ticket lifecycle. Chunk watches alone did not move carrier-based loading tickets. Replacement/removal handles movement, handoff, dimension changes, disconnect and shutdown. Pure view/ticket tests and Minecraft-source review cover this; connected terrain streaming remains unverified.

Cursor/teleport, nutrition, chunk-loading and grouped-picker source reviews passed. The initial combined build found test-definition failures; these were corrected. Final full build passed **3,010 unit tests, 0 failures/errors/skips**. Focused native tags `ghost-roles,mouse-ghost-roles,nutrition` selected **4/736**, with **4 passed, 0 failed**. Full native suite completed **736/736: 732 passed, 4 failed**. Logs: `build/validation-logs/mouse-ghost-roles-grouped-tracking-{build,native-focused,native-full}.log`.

Full-suite failures: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, `arbitrarystonehostreplacementeventuallystopsenergizedcable`, `logpulsedisableslampandrestoresatresolveddeadline`, and `liveapcmeterdrivesscheduledtwophasedoorandautoclose`. These involve power/lighting/door fixtures; causality is unestablished, so full native validation remains red. Failed artifacts are retained at `build/gametest-mouse-ghost-roles-grouped-tracking-full`; the disposable passing fixture directory is absent. Result XML, JAR hash and whitespace checks were independently verified. No graphical or connected chunk/camera/cursor acceptance is claimed.

- Latest full build: **3,028 unit tests passed, 0 failures/errors/skips**, with JAR creation; see oral/dev/gait results above. Earlier R2/view build passed 3,017; drag/name build passed 3,012; grouped/tracking build passed 3,010; reconciliation/window/catalog build passed 2,997. Earlier focused GhostRole/PointerModePolicy checks passed **11 tests**.
- Focused unit run: **165 passed, 0 failed, 0 skipped**. Log: `build/validation-logs/mouse-ghost-roles-focused-validated.log`.
- Earlier broad focused native run: **288/734 selected; 288 passed, 0 failed**. Tags: `mouse-ghost-roles,character,lifecycle,movement,physiology,chat,damage,prototype`. Log: `build/validation-logs/mouse-ghost-roles-native-focused-fixed.log`.
- The disposable passing `build/gametest-tags-fixture` directory was removed; validation logs remain.
- Earlier full native run: **734/734 completed; 732 passed, 2 failed**. Log: `build/validation-logs/mouse-ghost-roles-native-full-fixed.log`. Failures: `lampextensionreceiverusesnearestknownlvcablewithinthreeblocks` and `arbitrarystonehostreplacementeventuallystopsenergizedcable`. Causality is not established; latest full-suite results are above.
- No assistant-launched GPU client or positive authenticated connected-player takeover evidence. Unowned-mouse native fixtures do not establish player-authority success. See `MANUAL.md`.

## Resolved validation blockers

Forced recompilation replaced broad stale/missing-class diagnostics with concrete source errors, which were corrected. The test-executor startup failure was an actual dedicated-server classloading bug: the new menu's automatic subscriber lacked `Dist.CLIENT`. It is fixed. Shared speech payload assertions and client-source classification were updated without weakening server-owned classloading guards.

The earlier 9 focused / 20 full native failures exposed a real custom-Human binder regression and an incorrect idempotent-initialization assertion in the new mouse fixture. Both were corrected; all final selected character/mouse fixtures pass. Failed full fixtures are retained under `build/gametest-mouse-ghost-roles-full-fixed` and earlier diagnostic runs.

## Artifact and remaining checks

JAR: `build/libs/moonstation14-0.0.1.jar`.

SHA-256: `5254A222622FB4C69DB5BE13B8B141E782A63F7BD55A5212FB21D2421529B24C`.

## Reconciliation, compact window and role-host corrections

Connected testing found that L-Alt immediately recaptured the cursor. `BodyHandClient.reconcile()` was clearing pointer authority whenever no character body/epoch existed, which is normal during ghost control. It now clears character/hand caches separately and preserves pointer state only for the exact committed ghost UUID/epoch. Repeated reconciliation-decision checks cover matching, changed and lost ghost context; they are not connected-client evidence.

The picker now defaults to 228 × 176 logical pixels (minimum 216 × 150), using the existing geometry controller, half-scale close chrome and 13px title-bar drag region. Close, Escape and drag/release source guards pass; no visual observation is claimed.

`GhostRoleCatalog.available()` now excludes player-character harnesses from ordinary roles. Villagers still consume Human role composition. Ordinary mobs' persistent bindings are provenance, not active claims, and do not alone suppress availability. Existing Alive and shared-ownership checks remain in force. This fixes a concrete eligibility leak but the exact UUIDs behind the owner's two intermittent Human rows were not captured. Independent source review passed; connected candidate/cursor retesting remains pending.

Latest filtered native check: `ghost-roles,mouse-ghost-roles` selected **3/735**, with **3 passed, 0 failed**. It verifies the live catalog includes unowned villagers/pigs/mice, excludes custom player harnesses, and does not re-advertise a dead mouse across repeated snapshots; the other two fixtures cover mouse physiology and typed death. Log: `build/validation-logs/mouse-ghost-roles-catalog-native.log`. This is not a full-suite rerun or a connected ghost authority/cursor test. The passing disposable `build/gametest-tags-fixture` directory was removed; earlier useful failure artifacts remain.

## Ghost cursor and reply corrections

Connected testing exposed character-only gates in the shared pointer workspace. `BodyHandClient` now recognizes committed ghosts for pointer ownership/opening and manual L-Alt intent, while character hands remain character-only. `StoragePopupScreen` passes ghost movement/flight input through and dismisses the workspace on Escape. Context loss clears pointer state. These reuse the existing workspace and pointer policy rather than adding ghost hands or a second UI.

Registered Snapshot/Result handlers used the shared transport's client callback, but the client bootstrap installed its handler only into a disconnected client-side slot. The existing installer now connects the shared transport callback; regression checks exercise its actual receive path. Refresh deadlines start at the current world tick and expire after 100 ticks without being extended by periodic requests. Valid empty snapshots clear refresh state; unanswered requests show a retry status instead of indefinite “Refreshing…”.

Independent source review passed. The full build above passed after updating the startup allocation guard for tick-aware opening. No graphical or connected retest was performed, and native results below remain from the earlier implementation.

## Connected-retest corrections

`/suicide` rejected actual Human/Pig/Mouse `Biological` container definitions because its guard compared lowercase `biological` case-sensitively. It now reuses the existing medical service's canonical case-insensitive lookup; exact body authority and typed damage/death handling are unchanged. The regression test resolves bundled prototypes and exercises the actual command eligibility helper, not connected command execution.

The owner's `/ghostroles` rejection log shows a committed CHARACTER session and no corresponding GHOST Begin/Commit. The gate remains ghost-only: die, then move to leave the corpse before requesting roles. No evidence establishes an additional ghost-handoff failure yet; connected retesting remains needed after the suicide correction.

World visuals are now the owner-requested placeholder 3D cube, matching the unchanged 0.4 × 0.3 × 0.4-block hitbox. Source geometry review passed; no graphical launch/render observation was performed. Imported SS14 artwork remains attributed but is unused by world rendering. Current JAR suicide and renderer class bytes match compiled outputs. Native results above precede these changes and were not rerun for them.

## Client-startup correction

Owner launch log `run/logs/latest.log:32–57` records a mod-loading NPE: automatic subscriber initialization constructed `GhostRoleMenuWindow`'s `MachineSearchList` before `Minecraft.font` existed. The list is now allocated only on the real menu-open/render path; startup, snapshots, resets and input paths are safe before allocation. Focused startup/menu/classloading checks passed (10 tests), and the full build above passed. The regression test is source-level, not a graphical launch test. The owner's running client was left untouched; no duplicate GPU client was launched. Native results above precede this client-only correction and were not rerun for it.

Connected-player claims, contention, reconnect and graphical/audio observations remain pending. Lifecycle gates remain default-off; owner configuration is not overwritten. No commits, staging or pushes are authorized.
