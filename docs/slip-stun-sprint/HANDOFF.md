# Slip, stun and reactive Touch

## STATUS

Reconstructed 2026-10-03. Owner closed bounded delivery on 2026-09-24, not full SS14 movement acceptance. Later experimental movement/body-control work is separate and must not be inferred from slip closure.

## ARCHITECTURE

Character prototype policy drives server stun/action gates and client projection. Puddle slips can admit reactive Touch. Sliding uses bounded grounded friction and synchronized nonpersistent projection, not full chain-glide simulation. MovementServerController consumes the movement integration.

## IMPORTANT FILES

`closure.md`, `audits/m5-validation-and-open-gates.md`, `m4-slip-triggered-touch.md`, `2026-09-24-connected-smoke-regressions.md`; `src/main/java/com/juicyslew/moonstation14/ms14/slip/{SlipSystem,SlidingFrictionSystem,ReactiveTouchSystem}.java` and movement controller.

## SS14 CORRESPONDENCE

Pinned canonical slipData and Touch defaults: 50% admission, 15% split of current solution. This is bounded slip/Touch correspondence, not full physical sliding/prone/crawl parity.

## DECISIONS / WHY

Reactive contact is slip-triggered, not unconditional puddle overlap. Keep server effects distinct from projected animation. Full chain slide and physical prone/crawl were deferred to separately authorized movement work.

## KNOWN GAPS

Connected prediction, Touch damage, full character-family coverage and physical prone/crawl lacked complete acceptance. Later experimental motor remains gated; successful dedicated-server fixtures do not prove real-client motion or every body route.

## VALIDATION

Latest historical reviewed log: 109/109 required tests, including repeat Touch/acid run; earlier 106/106 is superseded. Server evidence only; no fresh suites or connected acceptance claimed.

## NEXT WORK

Use `../input-authoritative-movement-sprint/HANDOFF.md` and newer body/hands records before extending motion/action gating. Closure itself authorizes no new implementation.
