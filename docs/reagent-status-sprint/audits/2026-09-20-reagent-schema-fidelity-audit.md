# Reagent Schema Fidelity Audit

**Audit date:** 2026-09-20

## Verdict

The current **406 concrete prototypes decode**, but the canonical lossless schema gate is **not complete**. Recognized behavior-bearing fields are still accepted and dropped, and the condition and plant-effect shapes are not yet validated strongly enough for a lossless canonical-schema claim.

This is a snapshot complementing [`Instructions.md`](../../Instructions.md), not a replacement for it.

## Resolution / Status — 2026-09-20

> **Resolved for Milestone 1 schema/inheritance:** The findings below have been addressed. The original findings and history remain below for audit traceability.

- All 14 top-level fields are now presence-aware in `ReagentData` with typed nested DTOs.
- `PlantAdjustWeeds`/`PlantAdjustPests` probability is preserved.
- All 34 effect variants share flat common fields, and `Oxygenate`/`innoculate` fidelity is tested.
- All 8 conditions and 17 plant variants have exact schema auditing and contextual shape diagnostics.
- All 406 concrete prototypes audit, decode, encode, and decode successfully.
- All 334 references validate with zero missing, case-mismatch, or abstract targets.
- Six canonical resource normalizations are recorded: `metabolismrate` in `fresium`/`polytrinicacid`/`razorium`, removal of two `conditions:null` instances in `frezon`/`holywater`, and lowercase `mannitol` ID.
- The gate is now **passed for Milestone 1 schema/inheritance only**. Runtime reload, publication, and sync remain separate open work.

## Inventory

| Item | Count |
|---|---:|
| Raw prototypes | 411 |
| Concrete prototypes | 406 |
| Abstract prototypes | 5 |
| Ordinary effects | 908 |
| Conditions | 249 |
| Plant effects | 454 |
| References | 334 |
| Unresolved references | 0 |
| Reference case mismatches | 0 |
| References to abstract targets | 0 |

Parent and `abstract` are resolver metadata. They are intentionally absent after resolution and are not counted as silently dropped schema fields.

## Accepted but dropped top-level fields

These are the exact 14 top-level fields accepted by the audit surface but dropped by the decoded/resolved representation. Counts are **raw / resolved** prototypes containing the field:

| Field | Raw | Resolved |
|---|---:|---:|
| `recognizable` | 58 | 60 |
| `fizziness` | 38 | 52 |
| `worksonthedead` | 4 | 4 |
| `alloweddepartments` | 13 | 14 |
| `allowedjobs` | 5 | 5 |
| `slipdata` | 10 | 187 |
| `friction` | 7 | 125 |
| `tilereactions` | 20 | 197 |
| `footstepsound` | 11 | 49 |
| `standsout` | 2 | 6 |
| `flavorminimum` | 5 | 5 |
| `evaporationspeed` | 4 | 4 |
| `viscosity` | 4 | 4 |
| `absorbent` | 1 | 1 |

## Concrete plant losses

The resolved concrete data loses these plant-effect probabilities:

- `diethylamine`: `PlantAdjustPests` probability `0.1`.
- `robustharvest`: `PlantAdjustWeeds` probability `0.025` and `PlantAdjustPests` probability `0.025`.

## Validation gaps

- The condition codec currently uses a union allowlist, so fields from other condition variants can be accepted in the wrong variant.
- Contextual shape checks are missing: decoding does not yet enforce the required fields, types, and context for each condition/effect shape.

The following checks currently pass: path IDs, lowercase keys, no `conditions:null`, `metabolismrate`, `Oxygenate`, `innoculate`, and resolution of all references. These passing checks do not establish lossless canonical round-tripping.

## Smallest fix order and gate

1. Preserve the 14 listed top-level fields through decode, inheritance resolution, and canonical re-encoding; add round-trip coverage for their raw/resolved counts. Keep `parent` and `abstract` as resolver metadata, intentionally omitted after resolution.
2. Preserve the three concrete plant-effect probabilities, including their effect types and owning prototypes.
3. Replace the condition union allowlist with per-variant allowlists and contextual required-field/type checks; apply the same shape discipline to effect variants.
4. Make the canonical audit fail on any accepted behavior-bearing field that is not represented and re-emitted, while retaining the passing ID, key, null, codec, and reference checks.

The schema gate passes only when all 406 concrete prototypes decode and canonically re-encode without behavior-bearing loss, the listed plant probabilities survive resolution, condition/effect shapes are contextually valid, and references remain at zero unresolved, case-mismatched, or abstract targets.
