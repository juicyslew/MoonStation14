# Sable / optional Aeronautics — licensing review

**2026-10-10. Repository/release-metadata review, not legal advice or permission from a rights holder.** No dependencies installed, code/assets copied, maintainers contacted or distributable integration produced. The owner has separately authorized bounded M0–M3 Sable feasibility work while accepting the uncertainty as project risk; see [Instructions.md](Instructions.md). This does not change the assessment below.

## Bottom line

**Sable's PolyForm Shield noncompete clause is the material unresolved risk.** It is not MIT or a general-purpose permissive open-source license. MoonStation being free, MIT, SS14-inspired, an addon, or separately downloaded does **not** by itself settle whether the intended use is permitted. The available evidence establishes neither a prohibition nor clearance for this project.

Owner decision (2026-10-10): proceed with bounded Sable integration/feasibility through M0–M3 despite unresolved interpretation. This accepts the risk for those prototypes only; it does not establish permission, legal clearance, or automatic approval for public gameplay or redistribution. No written permission has been received, and no author contact is to be made on the owner's behalf. Project-specific clarification remains recommended when public release is considered. Complete the exact artifact/dependency/native/notice audit and applicable distribution review before distributing; keep asset restrictions in force.

Prefer Sable alone initially. Optional Create/Aeronautics adds separate reserved-asset and dependency obligations. Their licenses do not solve Sable's permitted-purpose question.

## Established terms and sources

| Material | Verified terms | Consequence / limit |
| --- | --- | --- |
| Sable | [Pinned LICENSE.md](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/LICENSE.md), **PolyForm Shield 1.0.0**; [README](https://github.com/ryanhcode/sable#licensing) identifies RyanHCode as licensor and repository coverage unless otherwise stated. | Use, distribution and new works are conditioned on permitted purposes. Preserve terms/URL and any actual `Required Notice:` lines when distributing covered material. Do not relicense Sable as MIT. |
| Sable Companion | Separate [repository](https://github.com/ryanhcode/sable-companion) and [MIT license](https://github.com/ryanhcode/sable-companion/blob/main/LICENSE), copyright 2026 RyanHCode. | MIT notices apply to copied/distributed Companion material. This does **not** relicense Sable or establish permission for the combined product. Current main was reviewed, not a release-artifact audit. |
| Aeronautics / Simulated / Offroad | [Official LICENSE.md](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/LICENSE.md): MIT outside the listed asset directories; **All Rights Reserved** under `aeronautics`, `simulated` and `offroad` `common/src/main/resources/assets/`. | Code permission is not permission to extract/repackage sprites/models/sounds or redistribute the complete asset-bearing jar. Verify artifact-specific redistribution permission before bundling; consuming a legitimately obtained dependency is a different activity from copying its resources into MoonStation. |
| Create | [Official LICENSE.md](https://github.com/Creators-of-Create/Create/blob/HEAD/LICENSE.md): MIT outside `src/main/resources/assets/`; that asset directory is **All Rights Reserved**. | Same code/asset separation. Main/HEAD terms are an orientation source, not proof for an exact installed release. |
| MoonStation / SS14 assets | MoonStation's own MIT code license does not cover third-party materials. SS14 resources retain their individual attribution/licenses. | Keep code and third-party notices scoped correctly. Do not put a blanket MIT label on a redistributed dependency/asset package. Use SS14 sprites/sounds with their verified attribution, not optional-mod assets as a replacement pool. |

### Why Shield needs clarification

The [license text](https://polyformproject.org/licenses/shield/1.0.0/) says:

> Any purpose is a permitted purpose, except for providing any product that competes with the software or any product the licensor or any of its affiliates provides using the software.

It explicitly includes competition across technical platforms/interfaces and **goods/services provided free of charge**. Marketing something as a practical substitute definitely competes; absence of that marketing does not itself establish noncompetition. "Affiliates" is a defined control/ownership relationship, not everyone collaborating on a mod; this review has not established legal affiliation or the full covered-product set.

MoonStation proposes independently authored spacecraft controls, damage, docking and FTL using a moving-block engine. That might be complementary integration, or raise questions about overlap with a covered vehicle product. Public library/addon documentation shows intended integration use, but is not project-specific permission or an exception to the written terms. **Do not infer prohibition merely from similar functionality; do not infer permission merely from addon status.**

The "New Products" clause permits continued use of earlier versions when a previously noncompeting product becomes competing because the licensor/affiliate introduces a new product/version. It is not a cure for an unresolved initial permitted purpose. Pinning a version is useful evidence, not automatic clearance.

## Release/dependency audit status

Release metadata confirms [Sable NeoForge 2.0.6](https://api.modrinth.com/v2/version/fg9dTRz9), Minecraft 1.21.1, release `fg9dTRz9`, 2026-10-03; filename `sable-neoforge-1.21.1-2.0.6.jar`. Metadata identifies **Veil as embedded**, with optional compatibility projects. It does not enumerate all jar contents/native licenses or prove binary correspondence to the inspected source revision. [Veil's listing](https://modrinth.com/mod/veil) identifies LGPL-3.0-only; verify the actual embedded version's terms and notices, not just this listing.

The [pinned NeoForge build](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/neoforge/build.gradle) and [properties](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/gradle.properties) give this **source-declared**, not binary-verified, inventory:

| Declaration | Source version / audit status |
| --- | --- |
| `jarJar`: Sable Companion | `sable-companion-common-1.21.1`, preferred 1.6.0; separate MIT license. Verify resolved version/notice in jar. |
| `jarJar`: Veil NeoForge | 4.3.2; listing identifies LGPL-3.0-only. Verify this version's full terms and included components. |
| `jarJar`: `:sable_rapier` | Project-native component; builds Windows/Linux/macOS native binaries into a compressed resource archive. Not a license-complete physics bundle just because it is embedded in Sable. |
| `implementation`: Create, Ponder, Flywheel, Registrate | 6.0.10-280, 1.0.82, 1.0.6, MC1.21-1.3.0+67 respectively. Unlike the explicit `jarJar` entries, these declarations alone establish neither final packaging nor mandatory runtime requirements. Verify selected runtime metadata before calling the fixture Sable-only. |
| Native Rust dependencies | [Cargo manifest](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/sable_rapier/src/main/rust/rapier/Cargo.toml) pins RyanHCode's Rapier fork `38e92f117590862481a53df6fc69a5d893e29186`, with `simd-nightly` and `parallel`; also Marten, Fern, JNI, Colored, log, humantime, Rayon and DashMap. Audit fork license plus lockfile-resolved transitive crate licenses/notices. Upstream Rapier attribution alone is insufficient. |

See [native build configuration](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/sable_rapier/build.gradle). Build tools/mappings, compile-only compatibility integrations and LZ4 Java used for archive packing are not presumed runtime contents without artifact evidence.

**The exact jar's complete license/native inventory remains unverified.** M0 must examine jar-in-jar metadata and native resources, transitive components, actual license texts, notice files, any `Required Notice:` / `Licensor Line of Business:` statements, and whether source/relinking obligations apply to the chosen distribution. Native physics libraries/bindings and embedded rendering code do not automatically inherit Shield or MIT. LGPL is not automatically incompatible with MoonStation's MIT code, but bundling or modifying LGPL material requires its own compliance plan.

Create/Aeronautics release 1.3.2 (`44pLdPGg`) was identified, but its exact license-bearing artifact and bundled-component inventory were not audited. Do not add optional dependencies simply to avoid this review.

## Practical integration and distribution policy

1. Keep independently authored MoonStation adapters/data separate; reuse supported APIs rather than copy engine internals. This minimizes imported material, not the Shield permitted-purpose question.
2. Prefer pinned dependencies installed separately for feasibility. Do not shade/fork/bundle dependencies, ship their assets in MoonStation, or assume an installer/modpack is exempt from redistribution obligations.
3. No project-specific clarification has been received. The owner permits bounded M0–M3 feasibility to proceed with the Shield issue documented as an accepted project risk. Consider clarification when public release is contemplated; this decision is not permission or automatic approval for redistribution. If a response is obtained, record who granted it, the covered project/use/versions/distribution, any conditions, and whether future changes need renewed review.
4. Before distributing any third-party material, verify the exact version/hash and each included component's license. Supply applicable licenses/copyright/required notices and any source/relinking materials the actual licenses require. Verify platform-native redistribution as well as Java jars.
5. Keep proprietary/reserved optional-mod resources out unless specific rights are established. Preserve individual SS14 resource provenance/share-alike obligations; unclear audio belongs in `docs/audio-unknown-provenance.md`.

### Suggested clarification request — not sent

> We are developing MoonStation14, a free MIT-licensed Minecraft 1.21.1 NeoForge mod inspired by Space Station 14. We want to use Sable as an external dependency, initially for a private feasibility cabin, then for a bounded fleet of playable, destructible spacecraft with independently written pilot/thruster controls, docking, and FTL/dimension transport. We do not intend to replace/relicense Sable, copy your restricted assets, or implement Create's aircraft/progression catalog. Is this use permitted under PolyForm Shield, including any covered products you or your affiliates provide? Please clarify private evaluation, publishing our adapter source/mod jar, requiring a separately installed Sable dependency, and whether a future modpack may redistribute the unmodified Sable jar. If separate permission is needed, what scope/conditions would cover this project?

Until clarified, record licensing as **unresolved**, not "free use therefore safe" or "definitely illegal." A licensor response or qualified legal review, not this document, resolves the material ambiguity.
