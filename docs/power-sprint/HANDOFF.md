# Cable and power foundations

## STATUS

Reconstructed 2026-10-03. Historical M16 implementation was not fully owner/client/multiplayer accepted. Later APC UI, overload, visual-kit, and powered-ventilation slices build on it; their acceptance must be assessed independently.

## ARCHITECTURE

Sparse chunk-owned face cable records represent six faces and HV/MV/APC tiers. Bounded snapshots synchronize clients; loaded-only graphs fail closed on unknown frontiers. Typed source→substation→APC→consumer runtime runs at 20-tick cadence. Floor finishes share a substrate; they do not own cable records.

## IMPORTANT FILES

`current-handoff.md`, `architecture.md`, `audits/m0-platform-contract.md`, and M2a/M3b/M4c/M10/M16 audits. Runtime: `src/main/java/com/juicyslew/moonstation14/ms14/power/graph/PowerGraphService.java`, `floor/StationFloorBlock.java`, `runtime/PowerRuntime.java`.

## SS14 CORRESPONDENCE

Audited LV/MV/HV references guide connectivity. Procedural local cable geometry is not imported SS14 cable-sprite parity. Upstream connected power-net behavior is not a boolean block flag.

## DECISIONS / WHY

Never force-load chunks. Client visuals cannot grant connectivity. Floor visual notifications follow successful mutation. Disabling simulation gates runtime/indexing, not registrations, stored records, or Creative access.

## KNOWN GAPS

Historical two-client/restart/visual and representative 20-player acceptance remain unproven; 20k-node measurements are synthetic. M16 notes same-type host replacement, incomplete mutation interception, and packet session-gap limitations. Later APC UI exists; the older handoff's "no APC UI" is superseded. Use `../powered-ventilation-sprint/HANDOFF.md` for current consumer integration and known cable failures.

## VALIDATION

Historical M16 coordinator compile/unit and 159/159 required GameTests. Later consumer tests are separate evidence; no fresh suite rerun.

## NEXT WORK

Start from latest dependent handoffs, then target cable seam/replacement, persistence, disabled-mode, and scale evidence rather than treating M16 as current global acceptance.
