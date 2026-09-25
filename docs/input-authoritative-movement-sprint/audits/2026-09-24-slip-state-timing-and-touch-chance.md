# 2026-09-24 slip, status timing, contact latch, and Touch chance

**Purpose/status:** a source-pinned explanation of what SS14 does, what this local experiment currently does, and what the owner's observations do and do not establish. This is documentation and automated-test evidence only; it is not a connected retest, a diagnosis of the reported burst/audio/jitter, or movement-sprint acceptance. The local control policy described here is current as of 2026-09-24; connected feel, animation, audio, and movement-mode checks remain open. See the [connected smoke checklist](experimental-connected-smoke.md).

## Reference and the essential distinction

Pinned SS14 source is commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:

- `Content.Shared/Slippery/SlipperySystem.cs:92-153` admits and handles slips.
- `Content.Shared/StepTrigger/StepTriggerSystem.cs:88-132,153-190` tracks stepped-on contact and clears it on contact loss.
- `Content.Shared/Slippery/SlidingSystem.cs:51-85` manages sliding contact effects/lifetime.
- `Content.Shared/Stunnable/SharedStunSystem.cs:44-55,379-390` handles stun action blocking.
- `Content.Shared/Stunnable/SharedStunSystem.Knockdown.cs:601-628` handles knockdown timing/state.
- `Content.Shared/Stunnable/CrawlerComponent.cs:29-39` defines the crawler speed modifier; `Content.Shared/Hands/EntitySystems/SharedHandsSystem.EventListeners.cs:33-44` calculates the free-hands/total-hands contribution.

Do not conflate four separate things: a slip is an **instant admitted event**; Stun and Knockdown are **timed status effects**; Sliding is a **contact-latched state**; and Touch is a **separate probability roll after an eligible slip admission**. In particular, expiry of a status timer does not itself clear the per-source step/contact latch.

## Pinned SS14 behavior

Space Lube has `requiredSpeed = 1`, `superSlippery = true`, default `StunTime = 0.5s` and `KnockdownTime = 1.5s`. At the local 20 ticks/second, these default durations are 10 and 30 ticks respectively. Its `launchVelocityMultiplier` is 1.5, applied only when the entity is not already Sliding. Admission has source/status/speed/area and movement checks; it is not a random chance roll.

- `superSlippery` permits a `SlipEvent` even when the entity is already knocked down. It does not imply another velocity multiplication. If already Sliding, the slip launch is not applied again.
- If already knocked down, an admitted super-slippery event does not apply a new stun or play the normal slip sound; knockdown is refreshed. Launch remains independently gated by `!Sliding`.
- The default 0.5s stun blocks voluntary actions while active. This includes movement input and actions such as attack/use/drop/pickup; look direction is separately subject to the stun direction-attempt gate. External velocity/physics is not thereby erased.
- Knockdown outlasts the default stun by 1.0s. Knockdown alone is not the same full voluntary-action block: crawling remains possible. Pinned crawler speed scales from free/total hands; for the human two-hand case this is 0.4 with two free hands, 0.2 with one free hand, and 0 with none. `CrawlerComponent` contributes base 0.4.
- Sliding is tied to qualifying slippery contact and clears when the last such contact ends. It changes friction/acceleration during contact and prevents another launch while Sliding is present; it is not itself a movement-input lock or a timed status.
- `StepTriggerSystem`'s `CurrentlySteppedOn` source latch survives standing up or knockdown expiry. It clears on actual source/contact exit. Remaining on the same source does not become a fresh step merely because the entity stood up or walked to qualifying speed again; exit and re-entry are needed for another admission from that source.

Thus the ordinary sequence for one qualifying contact is an admitted slip, a 10-tick stun, a 30-tick knockdown refresh, and contact-latched Sliding until exit. A gap may end Sliding before knockdown does. If retained momentum then reaches another qualifying source, a fresh slip can be admitted during knockdown without voluntary input; super-slippery permits it, and because Sliding ended it can launch again. That admission does not renew stun or repeat slip sound when still knocked down. It does refresh knockdown. On a continuously contacted source, simply waiting for status expiry does not re-arm that source latch.

## Current local behavior and deliberate differences

The local implementation uses its own swept/contact adapter and status/movement model; do not describe those as literal SS14 components or as physical prone. The local `CONTACTS` latch is keyed by source position and entity and has the same relevant episode semantics: status expiry/standing does not clear the source latch; true exit and later re-entry do. The adapter uses local contact queries rather than SS14's physical step-trigger contact system.

Local `SlidingAttachment` is a boolean marker, not a timer. It is cleared on last qualifying contact exit or when knockdown ends. While present, local sliding friction/acceleration are applied over qualifying puddle overlap and repeat launch/Touch are suppressed; it does not block movement input. The local slip event itself is instant. The local movement implementation does not reproduce SS14's physical crawler/prone and hands-occupancy calculation: after stun ends, knockdown currently scales a human's voluntary walk/sprint speed by 0.4, with no free-hands modifier. While stun is active, its action-block gate suppresses voluntary movement and actions, and a temporary custom camera guard restricts look; external velocity continues. Standing/knockdown end clears the local SlidingAttachment as described, but does not clear the source contact latch. Full stand-up remains a separate local behavior.

| Interval/state | Pinned SS14 interpretation | Current local interpretation |
|---|---|---|
| Slip at `t=0` | Event is admitted when qualifying checks pass; launch is multiplied by 1.5 only if not Sliding. Stun/normal slip sound only if not already knocked down; knockdown is refreshed. | Admitted local event is instant; local launch and status path follow current already-sliding/already-knocked-down gates. Local sound is played synchronously in that admitted branch before local stun/sliding/launch/knockdown mutations. |
| `0–0.5s` (ticks 0–9) | Default Stun blocks voluntary actions; Knockdown is also active. External physics continues. | Stun action-block gate prevents voluntary move/attack/use/drop/pickup etc.; external velocity continues. Custom camera lock is tied to stun. |
| `0.5–1.5s` (ticks 10–29) | Default Stun has ended; Knockdown persists. Crawling is permitted with pinned crawler/hands speed scaling. Sliding/contact latch independently depends on contact. | Stun gate has ended; knockdown alone does not fully block movement. Local human voluntary walk/sprint speed is multiplied by 0.4; no hand occupancy modifier or physical prone. Camera guard is no longer active after stun, even if knockdown/Sliding persists. |
| At/after `1.5s` | Knockdown expiry/standing does not clear the `CurrentlySteppedOn` latch. While still on that source, no fresh step admission; actual exit/re-entry rearms it. | Knockdown end clears SlidingAttachment, but not source-keyed `CONTACTS`; no fresh admission from same still-contacted source until actual exit/re-entry. |
| Exit while Knockdown remains | Last-contact exit clears Sliding; next qualifying source can admit a super-slippery slip from retained momentum, without voluntary input. Already-knocked-down branch has no new stun/sound. | Last-contact exit clears SlidingAttachment; a later qualifying source can admit from retained momentum while knockdown is active. Same-source latch rules still apply to each source episode. |

Thresholds and gates matter: local source admission uses pure Space Lube speed threshold `>= 1 block/s`, minimum area ratio `0.3`, a nonzero accepted player displacement of at most `1.5 blocks/tick`, source latch per contact episode, and status/reagent identity validation. These are admission constraints, not an RNG chance. The SS14 source gate should be read from the cited implementation and not inferred from the local adapter.

## Touch probability and the 20-unit observation

The current local Touch roll is 50% after an otherwise eligible admitted slip if the entity was not already Sliding; the current solution split is 15%. Do **not** set slip chance to 100% to make an apparent miss go away: slip admission has no random roll and is already deterministic once its gates pass. Touch's probability and slip admission are different decisions. Touch can consume/deplete source solution, which can subsequently change whether the deterministic source threshold is met.

For the stated 20-unit pure-lube example, a successful 15% split leaves `20 × 0.85 = 17` units, still above the strict `>15` source threshold. If another eligible Touch succeeds, it leaves `17 × 0.85 = 14.45`, now below threshold. A later contact may consequently fail the quantity gate until the source is refilled, without implying that the slip itself randomly failed. A failed Touch roll leaves the solution unchanged. Record initial and current source quantities and actual admitted SlipEvents separately from Touch outcomes.

## Sound, gait jitter, and the owner's observations

The owner has reported an intermittent large speed burst over large/multi-tile Space Lube, an apparently late slip/fall sound, and tiny leg-animation jitter. The reports are observations, not proof of a shared cause. Prior local three-launch fixture results were caused by a local parity defect and are corrected/superseded; see the [re-slip parity audit](2026-09-24-re-slip-parity.md). Current automated work does not diagnose the vanilla fall cue's playback, delivery, or perceived timing.

Audio timing specifically differs in the code paths: local `ServerLevel.playSound` is invoked synchronously in the admitted branch before local status/sliding/launch/knockdown application; there is no scheduled end-of-slip sound in that branch. Pinned SS14 calls `PlayPredicted` after launch and before knockdown. Network/prediction timing or the vanilla `SLIME_BLOCK_FALL` cue's perceived character could make local audio seem late, but that is only a hypothesis. Do not claim the sound is fixed, or add duplicate client-predicted audio without a separate diagnosis.

For tiny gait jitter, the proposed cause that vanilla animation compared current and previous tick positions while snapshots set position is a hypothesis, not a confirmed diagnosis. Latest client animation now derives from actual accepted motor displacement instead. First-person camera bob could still jitter. There has been no connected retest of this change. Request a short video and matching client/server logs, and ask whether jitter occurs while stationary or only while moving; do not claim it is resolved.

## Automated evidence and connected retest scope

Focused local evidence includes a pure `20 -> 17 -> 14.45` solution calculation and GameTest `clearingKnockdownDoesNotRearmLatchedPuddleUntilExitAndReentry`: using a real FakePlayer move, it checks that clearing knockdown does not rearm the still-latched puddle and that a true exit/re-entry produces exactly one new SlipEvent. A neighboring fixture was moved to x=4 to remove cross-test interference. The latest full automated GameTest run was 113/113 (latest log: 113 started at line 35, completed/pass at lines 252/253); test/build, JSON parsing (603 files), and `git diff --check` were reported passing. These are not connected-player proof.

Connected retest remains owner-run and should record source identity/amount, actual SlipEvent and launch separately, Sliding/Knockdown/Stun state and elapsed ticks, input after tick 10, behavior after tick 30, contact exit/re-entry, Touch result and remaining solution, sound timing relative to motion, and animation/camera jitter at rest and while moving. Include video and logs for audio/jitter. Keep the default-disabled opt-in policy and all connected, mode/teleport, latency, M2/M3, and M4 lane gates open; no owner retest of these latest changes is claimed. Follow the [smoke checklist](experimental-connected-smoke.md).
