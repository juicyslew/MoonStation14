# Early gun model requests

## Current disposition

The owner subsequently instructed use of the completed modeler deliveries. Latest
complete groups imported are `guns-cartridge-handle-20261105-r4`,
`guns-energy-svalinn-barrel-texture-20261105-r3`, and
`guns-manual-feed-20261105-r1`. See `ARTIST-MODELS.md` for exact bindings and
`ROOT-BINDINGS.md` for gameplay integration edits. Earlier claim/hold notes below
are historical, not the current import authorization state.

Submitted through the public asset CLI on **2026-11-05**, before implementation.
The owner was notified immediately after submission so modelers can be launched.
Public `list --json` verification showed all three **open**, with no claimant.
This is submission/status evidence, not delivery, approval or import.

On owner implementation authorization, a fresh public listing verified all three
claimed: `gpt-6.1-sol-guns-magazine-fed-r1`, `gpt-6.1-sol-guns-manual-feed-r1`, and
`gpt-6.1-sol-energy-guns-r1`. Claim status is not delivery or approval evidence.

| Request ID | Assigned content |
|---|---|
| `guns-magazine-fed-20261105-r1` | Mk58, Lecter, pistol and rifle magazines |
| `guns-manual-feed-20261105-r1` | Inspector, Kammerer, Mosin, magnum speedloader |
| `guns-energy-20261105-r1` | retro Laser Gun, Energy Shotgun, Svalinn, small power cell |

The owner can run three independent modeler workers, one per request. Do not
duplicate asset ownership or create additional requests for the same group merely
because an agent has not been launched yet.

All require true 3D held/loose objects using complete native NeoForge OBJ/MTL/
atlas/item-wrapper groups, not sprite-only guns or wearable-interchange models.
Requests bind source pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, source-faithful
geometry, supported static states, both-hand grip previews, NONE-context dropped
placement, editable projects and per-file attribution/provenance. The requests
explicitly distinguish integral batteries from Svalinn's removable cell.

## Published context

The three `model-request-*.json` files are the submitted payloads. Each froze:

- `MODEL-BRIEF.md` and `MODEL-SAMPLES.md`;
- the existing banana-peel OBJ/item-wrapper/MTL format example;
- `WorldItemPresentationRenderer.java` as the actual loose-model route reference.

Do not silently edit published context and treat it as a delivered clarification.
If a substantive contract correction is needed, coordinate through the asset
workflow with the owner and bind an explicit revision; never assume a modeler saw
later local documentation edits. `PLAN.md`/`PARITY.md` are not frozen modeler context.

## Public commands used

From the requester worktree:

```powershell
python ../MoonStation14-shared/tooling/agent_workflow/workflow.py --repo . request --from-json docs/guns-sprint/model-request-magazine-fed.json --context-root .
python ../MoonStation14-shared/tooling/agent_workflow/workflow.py --repo . request --from-json docs/guns-sprint/model-request-manual-feed.json --context-root .
python ../MoonStation14-shared/tooling/agent_workflow/workflow.py --repo . request --from-json docs/guns-sprint/model-request-energy.json --context-root .
python ../MoonStation14-shared/tooling/agent_workflow/workflow.py --repo . list --json
```

These succeeded and returned the three request IDs above. Do not replay them as
new requests. Delivery must later be independently reviewed against frozen bytes;
owner acceptance and exact-digest import authorization are separate steps.
