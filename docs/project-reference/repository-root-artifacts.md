# Repository root artifact housekeeping

This index records limited housekeeping; it does not certify other root entries as disposable. Preserve `README.md`, `AGENTS.md`, configuration, licenses, tooling, and current build/JAR/source directories. Do not purge unassessed directories based on this index.

## Relocated notes

- Medical-treatment root versions are at `docs/basic-medical-treatment-sprint/root-notes/`: `NEWPATIENT-COMPOSITION.md`, `CORRECTIONS-TREATMENT.md`, `SYRINGE-ASSETS.md`, `SYRINGE-MANUAL.md`, `SYRINGE-PARITY.md`.
- Singularity-power root versions are at `docs/singularity-power-sprint/root-notes/`: `CANISTER-COMPOSITION.md`, `TANK-COMPOSITION.md`, `PA-OPERATOR-MANUAL.md`, `pa-particle-rendering.md`.
- The five same-named medical sprint files differed from the root versions; both versions were retained and the canonical sprint copies were not overwritten.
- Root authored drafts `generic.json`, `grenade.json`, and `jug.json` are archived at `docs/root-artifact-archive/loose-objects/`. These are original root drafts, **not live prototypes**. Their ownership/parity is unverified. Canonical resource source is `src/main/resources`; do not install these drafts into live source without separate review.
- Historical `powered-ventilation-sprint/asset-attribution.md` is present in Git HEAD's tree but absent from the working tree and was not restored. The handoff now marks attribution as missing/unverified; no provenance or license is inferred.

## Validation evidence archive

- Six root `.log` files are under `build/validation-logs/root-cleanup/`; 168 `.log`/`.txt` files formerly under `capturebuild/` are under its `capturebuild/` subdirectory, preserving relative paths.
- `reports/registry_order.json` is under `build/validation-logs/root-cleanup/reports/`. Its producer and consumers are unknown; it is archived, not declared disposable.
- SHA-256 was checked across moves for the six root logs, all 168 capturebuild evidence files, the three data drafts, and registry dump. The nine moved Markdown notes were moved without edits; hashes match recorded pre-move hashes for all nine.
- `build/` is gitignored: these are saved working-tree artifacts, **not a version-controlled backup**. Before purging `build/`, preserve/archive `build/validation-logs/root-cleanup/` elsewhere.

## Unassessed root entries

`assets/`, `bin/`, `com/`, `data/` (other than the three moved drafts), `logbuild/`, `reports/` (other than the archived registry dump), `run/`, `src/`, `tooling/`, and `build/` remain unassessed. Root `forge.exc`, `forge.sas`, `forge.srg`, `pack.mcmeta`, `neoforge.mixins.json`, and PNGs remain untouched; provenance and consumers are unverified. A runtime dependency on NeoForge mixins does not establish that this root mixins file is used. `.idea/`, `.git/`, historical status metadata, and remaining generated directories were not modified or assessed for purge safety.
