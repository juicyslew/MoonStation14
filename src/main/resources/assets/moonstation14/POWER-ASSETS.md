# Accepted reactor machinery render resources (import only)

Source package: `Asset Building Workspace/assets/Structures/Power/candidates/terminal_support_r6` (user accepted current R6, complete 56 appearances / 86 editable projects). The import manifest records each selected runtime file, its original export source and SHA-256, accepted master locations, prototype aliases and bounds. Editable masters remain immutable in the external source workspace; none were regenerated or modified.

Imported identities: Particle Accelerator control box, end cap, fuel chamber, power box, fore/port/starboard emitters; standalone emitter; containment-field generator; singularity generator; radiation collector. This is an asset import only, not block registration or runtime rendering.

The export `binding_contract.json` distinguishes whole-state snapshots from independent component composition: generic whole snapshots do not provide the Cartesian product of independent layers. No missing components are fabricated. Collector shell/panel and accepted whole-pose OBJ candidates, including ca_active/ca_deactive and motion_activate_00..16 / motion_deactivate_00..16 where exported, plus `collector_motion_contract.json`, are retained for future native OBJ rendering. Native OBJ/MTL loading and dynamic collector motion are not validated here.

No containment-field effect geometry, singularity effects, gameplay, blockstates, Java bindings, or runtime props are included. R6 emitter and Tesla corrections are preserved in the accepted source; this import does not include Tesla assets. Attribution, license/source records, acceptance evidence, complete export contracts and registries are retained under `power-reactor-provenance/`.
