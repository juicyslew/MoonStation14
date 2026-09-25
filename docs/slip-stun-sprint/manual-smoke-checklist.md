# Slip, stun, and reactive Touch: connected-client smoke checklist

**Status: owner smoke failed; retest pending.** This checklist is a manual smoke test, not proof from GameTests. The owner reported player slip/action and reconnect regressions; see the [2026-09-24 connected smoke regression audit](audits/2026-09-24-connected-smoke-regressions.md). It covers a real connected player and a Minecraft Villager using the same local `moonstation14:human` profile. Keep sprint acceptance open until retests and connected-player caustic results are demonstrated and recorded honestly. For the pinned repeated-Touch dose math and sliding skip, see the [2026-09-24 repeated Touch damage follow-up](audits/2026-09-24-repeated-touch-damage.md); its Villager GameTest is not player verification.

## Before starting

- Back up the test world and player data. Use a local test world/server and a connected client; do not try this in a shared or production world.
- Restart both client and server on the matching updated build/version, and record exact versions/build identifiers. The prototype catalog wire format is cent-backed v2, so mismatched versions can invalidate the test.
- Back up the test world and player data before rejoining. **Rejoin first**, before creating test puddles: the prior owner run failed on disconnect/rejoin with `NoSuchElementException` at `EntityActivitySystem.reconcile:80` via `ModEventHooks.onEntityJoinLevel:50`; the stomach activity binding fix now has server GameTest coverage, but authenticated reconnect and disk-world lifecycle still need this manual check. Record whether reconnect succeeds. If it fails, capture the full server/client logs and stop; do not count gameplay observations from a broken session as a retest.
- Use a flat, solid, open area where a thrown jug will land on the ground and make a puddle. Keep the Villager nearby, but do not let it wander into a different puddle.
- Do not drink or otherwise ingest the acid mixture. The jug is intended to be thrown at the ground.

## Prepare the mixed jug

As an operator, enter these commands in chat (including the leading slash):

```text
/gamemode survival
/give @s moonstation14:jug[moonstation14:reagent={"moonstation14:spacelube":16.0f,"moonstation14:polytrinicacid":4.0f}]
```

The mixed-jug command syntax is checked by a parser-only GameTest; it is not executed in the client.

The local puddle flow cutoff is 20 units, so this 20-unit mix is at the cutoff and should remain together instead of flowing away. Its 16 units of Space Lube exceed the independent 15-unit slip cutoff. Throw the jug with the normal non-sneak use action at solid, flat ground; do not use sneak-pour. `JugItem` throws a `ThrownJugEntity`, which spills the full mixture on impact. Confirm that the puddle formed before testing contact.

## High-priority retest order

Perform these in order on a fresh mixed-20 puddle for each actor as appropriate:

1. **Solo player slip first:** ensure no Villager collision or contact can affect the player. Start away, then sprint across a fresh mixed-20 puddle, including a fast crossing that could previously skip between movement samples. Observe whether the player alone slips. If useful, include a slow-contact attempt, which should not slip. Exit fully before another crossing; repeat if needed because the response is brief and contact is latched until re-entry. The automated accepted-movement seam test is not a real network/player test.
2. **Villager positive control:** separately observe the Villager crossing a fresh qualifying mixed puddle. This confirms only the comparison behavior; it does not substitute for player slip.
3. **Sprint across fresh mixed-20 source:** with the player, test another fresh source after confirming the solo condition. Record whether the player slips/stuns and the resulting action window.
4. **Action-denial observations:** during the 0.5-second stun, try sword attack, mining, item use, and snowball use; inspect snowball count during the interval and after server correction. Note how long the denied-action window lasts. Repeat as needed because the window is short. Record both the immediate predicted/animated result and final server state. Mining is a block action; do not treat a visual swing alone as evidence either way.
5. **Rejoin again:** disconnect and reconnect after the gameplay checks. Record success or capture the full failure logs, including the exception and stack trace.

If any step fails, preserve logs and record the exact actor, source, action, timing, and client/server versions. Do not infer that one successful Villager trial proves player behavior.

There is no easy `/stun` command for this test. Do not substitute one or claim the packet/action gates are proven merely because a status marker appeared: actual connected-player action handling is part of the smoke check. Client-side input/status projection can be delayed; prediction may briefly show an action or rubber-band when the server rejects it. Record what was observable at the instant, which action was attempted, and whether the server appeared to deny it; do not interpret a delayed animation alone as proof. Client runtime behavior remains untested until this connected-client retest.

## 50%-chance caustic Touch test

Reactive Touch is attempted only after an admitted slip, and each eligible slip has a 50% chance of accepting Touch. A first attempt can legitimately show no acid effect; there is no guarantee on the first crossing. On an accepted Touch, the system removes 15% of the current whole mixture proportionally: from this fresh 20-unit mix, that is 3 units total, leaving 17 units (Space Lube 13.6 and polytrinic acid 3.4). The acid's Touch effect is half-strength, for 0.30 typed caustic damage to the player.

The 0.30 typed caustic amount is only 0.06 vanilla health at the local conversion, so it may not produce an obvious heart-HUD change. Do not expect a chat popup, and do not assume `/data` makes the relevant attachment visible. If no result is visually clear, note **“not visually verifiable”** instead of claiming success or failure. For another observation, use a fresh mixed jug/puddle (the accepted dose changes the proportions and leaves only 13.6 Space Lube, below the 15-unit slip cutoff); leave/re-enter alone does not replenish it. Repeat with a fresh source or use available developer telemetry to establish whether Touch was accepted and damage was applied.

## Controls and Villager comparison

- For the negative control, create a separate pure-water puddle with a jug and cross it both slowly and quickly. Neither should cause a slip or stun. Keep it separate from the mixed puddle.
- On a fresh mixed source, compare a slow approach, one qualifying quick contact, and a later quick contact after fully leaving and re-entering. Continuous overlap should not produce repeated effects.
- Observe the nearby Villager crossing a fresh mixed puddle at a qualifying speed. It should use the same `moonstation14:human` profile as the player and can show slip/stun behavior; the Villager is also useful for comparing visible caustic response. Do not use the Villager result as a substitute for connected-player caustic verification.

## M5 connected-client sliding characterization (owner; not verified by dedicated server)

Use the same prepared lube mixture/source conditions for each comparison, and perform this with a real connected client. This is a manual movement observation; the dedicated-server GameTests do not exercise LocalPlayer movement, client synchronization, or client rendering.

1. Get the player sliding on one lube puddle, then while the sliding marker is active compare grounded input response and momentum on that puddle with ordinary solid floor. Record the observed direction/input, relative acceleration, and how momentum changes; do not infer exact numeric parity from appearance alone. Source contact friction is `F=.05`; off contact vanilla stone retention is about `0.546`. There is currently no post-contact glide change, so this does not establish the owner's long-slide expectation, which remains open.
2. Compare the single-puddle result against a row of adjacent qualifying lube puddles. Record whether continued qualifying contact changes the observed slide. The owner's single-puddle long-distance glide concern remains unresolved; do not invent or imply a post-contact timer, and do not claim post-contact glide is solved.
3. While still sliding, contact a separate nonslippery puddle whose total solution is above the source's 15-unit sliding threshold. Compare movement on it with lube-only and ordinary-floor observations. Upstream semantics mark this source `AffectsSliding` before checking slippery units, so it contributes neutral `F=1` friction to the mean while sliding; this is intentional, not a test setup error. Keep sources separate so their contents are known.
4. Leave all puddle contact while still sliding and compare movement to ordinary-floor baseline. With no qualifying puddle contact the friction factor is neutral; the slide marker itself can remain until knockdown expiry/reconciliation. Record whether movement returns to baseline and whether it does so immediately.
5. Check reconnect and, if available in the test environment, dimension transfer for a stale sliding marker. The marker is nonpersistent and cleared on join/clone rather than rehydrated from generic knockdown, which may come from a nonslip source. An ongoing slide may consequently be lost across reload/dimension transfer; that is a known parity gap, not evidence that knockdown should recreate the marker. Record observed behavior without claiming durable slide continuity.

Record client/server versions, the source mixture used, and the observations for each comparison. Do not claim client runtime validation before this owner check is performed. Keep the small caustic amount caveat above in mind: the 0.30 typed caustic effect is only 0.06 vanilla health and can be too small to read reliably on the HUD; a small caustic effect may be visually unmeasurable.

## Record and report

Record client/server build versions, whether the player and Villager had slip/stun on quick contact, slow/water control results, which player action was attempted during the short stun and what happened at that instant, and whether player caustic damage was directly verifiable (or **not visually verifiable**). Note whether any retry used a fresh source or developer telemetry. Report unexpected behavior with the world/server log if available. Leave unobserved or visually ambiguous outcomes explicitly unverified; this manual test does not by itself prove full packet-gate coverage or guarantee that every random Touch attempt succeeds.
