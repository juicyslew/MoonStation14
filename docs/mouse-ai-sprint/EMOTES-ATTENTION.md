# Mouse emote and idle-interest follow-up

The pinned SS14 checkout (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`) defines
`MobMouse` in `Resources/Prototypes/Entities/Mobs/NPCs/animals.yml`, inheriting
`SimpleMobBase` and `BaseMobAnimal`. It composes `Speech` with the `Squeak`
speech sound and the `Squeak` emote. Shared emote definitions and chat handling
are in `Resources/Prototypes/Entities/Traits/speech.yml` and
`Content.Server/Chat/Systems/EmoteSystem.cs`; the mouse speech collection is
`Resources/Prototypes/Voice/speech_emote_sounds.yml`. The emote is a visible
`name squeaks.` action, not an accented ordinary speech phrase.

Locally, `EmoteRegistry` remains the single closed ID allowlist and
`EmoteEffect` remains the shared plan/delivery owner used by reagent effects.
This change adds `squeak` there and maps it to the already registered and
attributed `mouse_squeak` sound with the verified 0.125 pitch variation; other
emotes retain zero variation. `Speech.allowedEmotes` resolves a strict
closed-registry policy (default empty); the mouse opts into only `squeak`.
`/emote <id>` reuses committed-body speaker resolution from
`LocalSpeechServerHooks`, checks live body control and the resolved capability,
then invokes existing `EmoteEffect.SERVER` exactly once. Its `mouse_squeak`
sound is also registered in the closed sound lookup used by that shared effect
owner. A local two-second
abuse cooldown is an adaptation; no upstream cooldown parity is claimed.

Idle interest is a local-only optional `NpcIdleAttention` composition (range 5,
probe 40–100 ticks, duration 20–40 ticks), consumed during stationary idle wait
and canceled when it leaves that state. It scans only loaded server players in
the same level; it does not request a route or movement wish. Carrying and
inventory escape remain deferred because item-stack storage cannot preserve a
living body's identity/control semantics.

Dedicated GameTests: `mouse-emote` exercises the real server dispatcher with a
normally committed lifecycle human (denial) and a mouse adopted through the
canonical corpse → ghost → role-offer lifecycle (success), verifying exactly one
spatial squeak sound and one chat line for the authorized invocation, plus
cooldown suppression of a rapid repeat. The fixture's embedded player peer is
not network-handshake evidence. `mouse-attention` observes the production mouse brain turn its body/head
toward a nearby server-side player entity and then yield to its production FOOD
plan. The latter uses a synthetic FakePlayer entity as the visible observer, not
an authenticated peer.

Focused validation passed: `mouse-emote` selected 2/838 and passed 2/2;
focused mouse/emote JUnit passed; the full JUnit suite passed (3,290 tests).
`featureReadiness interaction,prediction` passed its catalog (51 tests), with
`EmoteCommandTest` added to the interaction selector; catalog evidence is not
authenticated peer coverage. Latest full native run selected and passed 838/838
(`build/mouse-emote-final-full.log`); focused command coverage is in
`build/mouse-emote-final-final.log`, and airlock reproduction is in
`build/airlock-repro.log`. An earlier full invocation had two failures: `receiverReconcilesAlreadyLoadedMissingFootprint`
(its `PowerFixtureReadiness.awaitFreshAlarmMeter` callback reported “No sequences
finished” at the test position) and
`defaultBlastPrimesFiniteReactiveCellWithoutInstantHeating` (after 80 ticks, gas
moles/energy/293.15 K were unchanged, fire intensity remained zero, and the
atmosphere diagnostic reported `workStatus=committed partial`, `queued=2551`,
`ownershipPending=21`). The later full rerun passed both; no cause was established
or production code changed for either transient fixture observation. Native
visual/model review and connected peer validation remain unverified. One full
838-test invocation failed two airlock fixtures:
`stationarycriticalcrushcontactshocksoncepersolidification` (reopened door
began a second close) and
`crushreleasecompletesnormalopenbeforefastautoclose` (the recorded OPEN phase
was resolved to CLOSING before the next damaging-close check); 836 passed. The
focused eight-test airlock reproduction passed, and the subsequent full run
passed 838/838 after ensuring the new emote fixture removes its test mouse.
The failure's cause was not established. Earlier full-suite runs passed
837/837 before this added fixture. The production JAR (`build/libs/moonstation14-0.0.1.jar`)
was rebuilt after the sound-registry fix; SHA-256:
`1464EA81634988A273E13552BE4AD052F205BECF46F8C24BDA42D2AD2C2E1E58`.
