# Atmos machinery asset consumer contract

Authoritative code is the **unmerged MoonStation14-agent-01 worktree**, not MAIN. This document records the local mapping and current asset delivery/import state; it does not establish GPU-render validation, technical gate completion, or owner acceptance.

## Current TEG R6 runtime delivery (2026-11-06)

Owner-authorized normal delivery/import request `power-teg-pipefit-correction-20261105-r6`, digest `7cb9fa96a1501894420f838e569af8f65cdd44d4f6080a0ebbfd9cb3ffdbd38c`, is imported. The canonical public receipt is `../Asset Building Workspace/work/agent_exchange/requests/power-teg-pipefit-correction-20261105-r6/import_receipt.json`; the owner receipt and verification plan are under ignored `build/agent-workflow/teg-sprint/owner-feedback-r24/r6-import/`. Receipt reports 113 destinations (19 copied, 94 unchanged, no conflicts); all target hashes were verified against R6 and all 405 R5 source guards remain unchanged. R6 changes measured pipe-fit geometry, not bindings: the existing 13 core and 4 circulator live selectors still consume the same paths. Attribution and exact license must follow the delivery manifest; this import is owner-approved/imported, not GPU rendering or manual visual certification.

R6 measurement contract: primary square pipe spans x=6..10/16 (center x=8/16), centered y=8/16, width/height 4/16; its measured clearance is 4.5 square units. The other two-unit lanes remain distinct. These are art-fit measurements and do not alter gas-port logical volumes, power physics, or cadence.

The frozen `pipefit_r6/export/measured_ports.json` gives socket authoring centers `[0,8,-8]` and `[0,8,8]`, openings 4.5×4.5 (Y 5.75..10.25), and 0.25-unit per-side allowance. It reports 16 facing/endpoint contacts, each with zero boundary error. The focused geometry test parses the runtime OBJ's actual face-backed `inlet_bore` and `outlet_bore` groups across all display selectors and checks their centers, 4.5/16 section and clearance against the 4/16 PRIMARY section; face-backed collars are checked at the block boundaries. The model bounds reach the R6 measured 13.16-unit maximum (0.8225 block), replacing the R5-only 7.16-unit test ceiling. R6 runtime provenance/license is recorded at `src/main/resources/assets/moonstation14/power-provenance/teg-r6-runtime-notice.md`. These are model/resource checks, not in-game pipe fit or GPU proof.

## Historical TEG R5 runtime delivery (2026-11-06)

Owner-authorized normal delivery/import request `power-teg-r5-runtime-files-20261105`, delivery digest `5497c982d448dcda52f54114627e08d6b6f1fb723c1dcca91b8a5691052e7c71`, is imported into the agent-01 target. The canonical public import receipt is `../Asset Building Workspace/work/agent_exchange/requests/power-teg-r5-runtime-files-20261105/import_receipt.json`; the scoped owner receipt and byte/hash verification report are in ignored `build/agent-workflow/teg-sprint/owner-feedback-r21/r5-import/`. Exactly 113 runtime destinations were copied without conflicts; all 405 frozen R5 source guards remain unchanged. The older archive-only request/digest is not the installed package. The owner instruction authorizes this R5 import only; it is not sprint, code or GPU acceptance.

R5 OBJ origin/centering normalization is baked once. Live blockstate FACING supplies NORTH/EAST/SOUTH/WEST yaw 0/90/180/270 at render; do not bake yaw into OBJ and apply it twice. Core `DISPLAY` 0 is unpowered and 1–12 are powered output levels; circulator 26 is unpowered/still, 27 powered/still, 28 powered/slow and 29 powered/fast. Existing shared `AtmosMachineGeometry` remains the vertical-Y/collision consumer. Focused R5 checks passed 49 tests across 9 classes and all three compiles; source-derived face-backed OBJ bounds cover all 30 display states and four facings, including fallback/cache paths, with asymmetric 180/270 expectations corrected. The scoped TEG generator check bound two resolved profiles and 720 native blockstate selectors. A broader generator check still has a non-TEG pressure-pump mismatch; it is not represented as fixed by the scoped TEG result. R5 source-derived runtime-binding review is complete for scoped checks; independent final R21 code review is active; the final gate and GPU/renderer validation remain pending.

## Local consumers

Definitions: `src/main/resources/data/moonstation14/moonstation14/atmos_machine/`.
Registration: `ModBlocks`; runtime: `AtmosMachineBlockEntity`; state selection: `AtmosMachineAppearance`.
The block state has horizontal `FACING`, integer `DISPLAY` (0–29), and derived `PIPE_LAYER`.

| Local block/profile ID | Pinned SS14 entity | Live DISPLAY meaning | Art correspondence |
|---|---|---|---|
| `teg_center` | `TegCenter` | 0 unpowered; 1–12 powered, output level = DISPLAY−1 | Current normal R5 `generation_teg` import |
| `teg_circulator` | `TegCirculator` | 26 unpowered/still; 27 powered/still; 28 powered/slow; 29 powered/fast | Current normal R5 `generation_teg__tegcirculator` import |
| `gas_pressure_pump` | `GasPressurePump` | 13 enabled and powered; 14 otherwise | Existing SS14 panels; V03 audit is not import |
| `gas_volume_pump` | `GasVolumePump` | 15 off/unpowered; 16 blocked; 17 running | Same boundary |
| `gas_mixer` | `GasMixer` | 18 enabled; 19 disabled | Same boundary |
| `gas_filter` | `GasFilter` | 20 enabled; 21 disabled | Same boundary |
| `gas_valve` | `GasValve` | 22 open; 23 closed | Same boundary |
| `gas_thermo_heater` | `GasThermoMachineHeater` | 24 powered; 25 unpowered | Same boundary |
| `gas_thermo_freezer` | `GasThermoMachineFreezer` | 24 powered; 25 unpowered | Same boundary |
| `passive_heat_exchanger` | `HeatExchanger` | 0 passive appearance | Same boundary |

These are profile-specific selectors, **not** a Cartesian product. Do not wire all 30 values to every machine.
Heater/freezer appearance does not establish actual heating/cooling; pump/mixer appearances do not independently prove committed transfer.

Core output levels use the resolved visual thresholds and persistent ramp state. Circulator speed uses the **previous accepted core transfer**, with positive transferred moles and pressure differential; its fast threshold is resolved prototype data. No live moving-unpowered circulator state exists. R1's two such diagnostic configurations must not be selected as gameplay states.

## Facing, ports, and model ownership

`TegAssembly.layout(center, facing)` owns the three-block assembly:
circulator A is `center.relative(facing)`, facing `facing.getClockWise()`;
circulator B is opposite, facing `facing.getOpposite().getClockWise()`.
For the fixture's NORTH core this means north/EAST and south/WEST circulators.
Gas directions come from resolved profile ports and `AtmosMachineBlockEntity.gasPorts()`; do not derive gas movement from mesh fittings.

R1's original source contract declares +Y up, front −Z and 16 units/block; it is historical geometry context. Current R5 OBJ facing normalization is already applied once (N0/E90/S180/W270). Render one complete composed snapshot, never a base plus the same snapshot. Do not apply a second centering translation or yaw. The scoped source/resource test verifies selector coverage, not visual alignment in Minecraft.

The imported R5 group supplies the core/circulator runtime model states, not a gas simulation, networking clock, collision policy, material/fullbright installation, or ten-family runtime integration. Preserve source animation timing and provenance; do not claim late-join/GPU behavior from resource checks. Scoped R5 binding checks pass, but they do not prove GPU rendering or the complete R21 feature gate.

## Asset delivery and runtime boundary

The owner-approved R3 taller-heater delivery is imported through the normal asset workflow: request `atmos-thermomachine-taller-heater-20261027-r3`, delivery digest `2d3b21f1633be56e816eae0f0fb85e5e2bda8c6da37f36749ebb3ddacee4be19`, with the canonical receipt at `../Asset Building Workspace/work/agent_exchange/requests/atmos-thermomachine-taller-heater-20261027-r3/import_receipt.json`. Heater R3 and freezer R2 are unchanged dependencies. Both machine blockstates (`assets/moonstation14/blockstates/gas_thermo_heater.json` and `gas_thermo_freezer.json`) bind facing/display/pipe-layer variants into delivered models under `assets/moonstation14/models/block/thermomachines/`; item parents are `assets/moonstation14/models/item/gas_thermo_heater.json` and `gas_thermo_freezer.json`. OBJ/MTL, texture and animation dependencies use the standard `neoforge:obj` model loader. The imported package contains 300 files; the normal runtime asset set now wires all 720 selectors. Structural binding/resource checks are distinct from GPU rendering: native view/render validation remains unverified, so no blanket model-render PASS is claimed.

The following earlier “no delivery/import” statements are **historical and superseded** by the approved R3 import above; retain their history, not as the current asset status.

## Other imported interface assets

The frozen interface revision `source_interfaces_r1_complete_runtime_r1` (request `atmos-3d-pipe-interface-family-20261028-r1`, digest `2d76afcc3958f1073c85064851df468765db7800d3d3adf02a1fea571e636e46`) and manifold revision `source_manifold_r1` (request `atmos-gas-pipe-manifold-20261028-r1`, digest `0e55df51f7a8841adf32c1ff8dd9339788780f21bdec4a8803c06c3b0398fb94`) remain imported: 1,035 + 34 runtime files. Eight interface families remain blocked on blockstate/items in their new namespace. The manifold base and six mask BER resources are present; native GPU rendering remains unverified. Preserve original modeling requests, accepted heater R3/miner/TEG requests, and attribution at original source paths; do not copy bulk assets into docs. This records asset/runtime status only, not technical completion or owner acceptance.

Owner import-only authorization for the connector internal-contact-cap export correction: request `atmos-pipe-interface-internal-caps-20261101-r2`, delivery digest `d1c5b45e15ebdb4cbe5907f62ca5af505903ab2acf2e9ef1d0cd6fbc4a428448`, candidate `../Asset Building Workspace/assets/Structures/Atmospherics/PipeInterfaces/export_compatibility/internal_caps_r2/`. The owner said “you can import yes” while explicitly uncertain whether the referenced model actually has z-fighting. Scope is only the invisible internal contact-cap export correction; this is not new art approval, owner visual acceptance, or Minecraft/native-GPU verification. Owner's exact import-only authorization is recorded at ignored `build/agent-workflow/teg-sprint/owner-feedback-complete-r4/assets/internal_caps_r2_owner_import_receipt.json`; public queue receipt is `../Asset Building Workspace/work/agent_exchange/requests/atmos-pipe-interface-internal-caps-20261101-r2/import_receipt.json`. Normal owner-bound dry-run recognized all 1,035 existing destinations as exact frozen R1 hashes (903 unchanged; 132 corrected OBJ copies); authorized apply copied only those 132 OBJ resources. Post-import verification matched all 1,035 target hashes to the R2 delivery and all 1,035 frozen R1 source hashes remain unchanged. This is import-only authorization, not art approval, owner visual acceptance, or Minecraft/native-GPU verification.

`power-teg-circulator-finish-20261026-r1` was reported **delivered** by the public queue. The asset handoff binds digest `871afb7d592892d9713e656bda1db991680a05a4292c8fbe2ae8c2b610f88f9e`. Its public gallery is `../Asset Building Workspace/assets/Structures/Power/TEG/candidates/port_finish_r1/index.html`. Preserve its attribution and original path; do not reimport the already-delivered accepted heater R3, miner, or R5 TEG runtime group.

The new `gas_miner_oxygen`, `gas_miner_nitrogen`, and `gas_miner_plasma` blocks are **outside the ten-machine table**. Their current iron-block model is a placeholder. Preserve the accepted miner art delivery/source attribution; do not reimport it here.

Owner presentation-feedback audit: V03 custom candidates for both pumps, mixer, filter, valve and exchanger retain their existing source attribution. Older heater/freezer R1 “no delivery/import” notes and requests are historical, superseded by the approved R3 heater / R2 freezer dependencies stated above. R5 TEG runtime files are now imported under the normal request above; this does not install other pending art or establish full R21 feature readiness. See `PRESENTATION_FEEDBACK.md` for remaining runtime boundaries.
