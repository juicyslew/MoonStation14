# Sable 2.0.6 exact-artifact inventory (M0, partial)

This records archive inspection and the isolated startup evidence listed below.
It does not establish license clearance, runtime compatibility below the declared
minimum, plot-backed simulation acceptance or readiness to distribute.

## Primary artifact and source

- Official Modrinth release `fg9dTRz9`, Sable NeoForge 2.0.6 for Minecraft 1.21.1,
  dated 2026-10-03. Downloaded from the official release CDN URL:
  `https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar`
- Archived outside the repository at
  `%LOCALAPPDATA%\Temp\opencode\sable-feasibility\artifacts\sable-neoforge-1.21.1-2.0.6.jar`.
  Size: 12,986,135 bytes. SHA-1:
  `cf46f7e8c1511f170abcdbee739944f0540549bf` (matches supplied release digest).
  Locally computed SHA-256:
  `9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2`.
  SHA-512:
  `76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8`.
- Digest correction (2026-10-15): a fresh official download matched the published
  SHA-1, SHA-512 and byte count above. The previously recorded SHA-256 ending
  `6996bbbc8a617c2` transposed two characters; the corrected value ends
  `6996bbcb8a617c2`. This authenticates the reference download, not an installed
  runtime jar, and does not establish a reproducible source build. Historical
  bound reports are not rewritten.
- Archive `META-INF/neoforge.mods.toml` declares Sable `2.0.6`, Minecraft
  `[1.21.1]`, loader `[4,)`, and required NeoForge `[21.1.228,)` on BOTH sides.
  Pre-integration NeoForge 21.1.224 does not satisfy that requirement. The project
  is upgraded to 21.1.228; startup and focused power evidence are recorded below.
  Broader subsystem/client compatibility remains to be validated.
- GitHub tag `mc1.21.1-2.0.6-neoforge` resolves directly to commit
  `ad8b4d377741fbd923f4d863b2cf9e462daff9a6` (commit message `2.0.6`). The
  previously inspected source pin `d501fb3103c3247e20cb2151a99182ad5dd61adb`
  is two commits ahead of this tag (`compare` API says `behind`, 0 ahead/2
  behind); do not assert those two source trees are identical. The release
  version/tag correspondence is consistent, but binary-to-source build
  correspondence is **not established**, and this is not a reproducible-build
  claim.

## Embedded archive contents

`META-INF/jarjar/metadata.json` and nested archive inspection identify:

| Embedded jar | Version | Initial license/source note |
| --- | --- | --- |
| `sable-companion-common-1.21.1-1.6.0.jar` | 1.6.0 | Companion is separately MIT-licensed; inspect exact embedded notices before redistribution. |
| `dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.6.jar` | 2.0.6 | Native component; full Rust dependency/license/notices audit pending. |
| `veil-neoforge-1.21.1-4.3.2.jar` | 4.3.2 | Veil listing indicates LGPL-3.0-only; verify exact embedded license texts and obligations. |

The outer jar contains `LICENSE.md` (PolyForm Shield). The Rapier nested jar
contains `native/sable_rapier/LICENSE-RAPIER` and `README.md`, plus
`sable_rapier_binaries.zip.l4z` (9,072,872 bytes). This LZ4 frame was decoded
using locally cached `org.lz4:lz4-java:1.8.0` (no new dependency or Gradle run)
to a ZIP of six platform libraries:

| Native | Bytes | SHA-256 |
| --- | ---: | --- |
| `sable_rapier_x86_64_macos.dylib` | 4,693,160 | `679467db5c6d0030ca77c4d1f87cfcf6d5b9b7856c5f2674b9b819fd2f9daaca` |
| `sable_rapier_aarch64_macos.dylib` | 4,257,344 | `d006232bf40e993a5825d7b2edc101a7fad3ab28b997377434cf88341a9ae7bf` |
| `sable_rapier_x86_64_linux.so` | 1,887,608 | `442c1ffb2f13250caab0e9e26d8e0aa507bad7d401474a723670f8f76b26832f` |
| `sable_rapier_aarch64_linux.so` | 1,594,408 | `d6ddf44ebe1ecfe6d177da1c1a3768b04e06c5b704b3f4025c095cd79509d8c6` |
| `sable_rapier_x86_64_windows.dll` | 1,658,368 | `51a06dc79fcb77de72c104ad582a63299515e1565200a9fa2bd3e3ff1609305b` |
| `sable_rapier_aarch64_windows.dll` | 1,340,416 | `1e2824ae1533da6190de760f8cc864f9340bedd3e4fc65c0855256a9bb7ea69e` |

The official tag's `sable_rapier/build.gradle` independently names these same
platform naming patterns and uses LZ4 frame packing. PE imports and complete
third-party license/notice/source obligations are still not audited. Static PE
import-table inspection of `sable_rapier_x86_64_windows.dll` found
`kernel32.dll`, `api-ms-win-core-synch-l1-2-0.dll`, `ntdll.dll`,
`bcryptprimitives.dll`, `VCRUNTIME140.dll`, and UCRT runtime/math/heap DLLs.
This is only the x86_64 Windows import table, not a complete dependency or
redistribution audit. Inspection itself executed no Sable/native code; subsequent
server startup executed Sable and extracted the native payload as recorded below.
Actual plot/native-physics use is a separate pending proof. No Sable assets or
implementation were copied into MoonStation.

Sable's archive marks Flywheel and Create as optional CLIENT dependencies;
they are not mandatory for the planned Sable-only integration. Other runtime
dependencies of the Modrinth artifact have not been exhaustively resolved.
No Create/Aeronautics/Create assets or dependency are being added.

## Licensing status and integration decision

The owner authorized required Sable integration and technical runtime work,
without an opt-in/permission gate, and explicitly does not authorize contacting
the licensor or publishing. This is not legal clearance: PolyForm Shield's
permitted-purpose/noncompete question and public distribution readiness remain
unresolved. Do not claim private work is automatically exempt, contact the
licensor, or publish/distribute based on this authorization. Keep third-party
materials separate and provide required notices/source/relinking materials if
distribution is later authorized and obligations require them.

The official Modrinth Maven coordinate is verified: repository
`https://api.modrinth.com/maven`, `maven.modrinth:sable:fg9dTRz9`; its POM is
metadata-only (no declared transitive dependencies). The isolated 21.1.228
GameTest startup below confirms that this coordinate resolves and NeoForge
discovers the embedded JarJar companions. Sable's 2.0.6 source build
declares Companion compile-only as
`dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0`, while the
release embeds that exact Companion jar. The companion class `Pose3d` appears
in Sable's exposed method signatures and is directly needed for the stated
plot-allocation API. No current MoonStation source imports either Sable or
Companion API classes, so no separate compile dependency is currently justified.
If an adapter uses signatures such as `Pose3d`, test whether Gradle's Sable/
JarJar development classpath exposes it; otherwise add only a precise
compile-time artifact (potentially a generated artifact extracted from the
pinned Sable jar), not full Create. The exact Companion coordinate returned
404 from Maven Central, NeoForged releases, and Modrinth Maven. Do not invent a
repository or assume nested jars are on compile classpaths. Required mod
metadata pins `sable` to `[2.0.6]`, BOTH sides, with AFTER ordering and no
optional flag.

The minimum loader increase from project 21.1.224 to 21.1.228 is artifact
metadata-driven. It must not silently raise Minecraft or introduce
Create/Aeronautics. Build configuration changes are artifact-driven and applied.

## Archive API inspection (no code/runtime execution)

### Entity SAT precision audit — stationary retest

Direct `javap -p -c` inspection of `dev.ryanhcode.sable.api.math.OrientedBoundingBox3d`
in the cached official `sable-fg9dTRz9.jar` confirms `sat` and `satToleranced`
use Java double arithmetic, not a JNI/Rapier facade. Their OBB fields, vertices,
`genChecks` and `checkSeparation` use `Vector3d`/`Vector2d` and `Quaterniond`;
the inspected path has no float vectors or double-to-float conversion. Retained
bytecode: `build/shuttle-sat-precision-audit.txt`. Earlier attribution of this
specific API class to the separate Companion artifact was not established.
Pinned `collide` uses `sat`; `satToleranced` does not read its third double argument
in this artifact and has separate MTV-axis selection behavior. That argument is
not a spatial skin allowance; the port's body inflation supplies its touch margin.
This is distinct from stock Rapier's single-precision body simulation.
It does **not** certify every conversion before OBB construction or establish
the cause of the owner contact corrections. The outline's premature float
conversion is independently proven; do not infer the same defect in entity SAT
merely from the large storage origin or Rapier's precision.

`javap` against the downloaded archives confirms `RigidBodyHandle.of(ServerSubLevel)`,
`getLinearVelocity`, `getAngularVelocity`, `addLinearAndAngularVelocity`,
`applyLinearImpulse`, `applyLinearAndAngularImpulse`, and `teleport` are present.
There is no `setVelocity` method on this handle. `SubLevelContainer` exposes
`getContainer(Level/ServerLevel)`, `allocateNewSubLevel(Pose3d)`, plot lookup,
and `getSubLevel(UUID)`; the concrete server sublevel exposes `getPlot()` and
`getLevel()`. `Pose3d` is defined in the embedded Companion archive. API
presence alone does not establish API stability or block-placement frame
semantics. The future adapter must verify placement through the actual
parent-level/setBlock path and inspect `ServerLevelPlot` delegation before
creating plot contents; no global/local coordinate claim is made here.

## Pending inventory/validation

- `./gradlew test compileGametestJava assemble` passed after dependency and loader
  changes. Isolated server boot with the existing `power` selector in a fresh
  directory passed **60/60 selected, 60/784 available**. Logs:
  `build/shuttle-sable-integration-gametest.log` and
  `build/shuttle-sable-integration-build.log`. The startup log confirms Sable
  2.0.6, Companion 1.6.0 JarJar discovery, and NeoForge 21.1.228; Create was not
  declared. Sable extracted
  `build/gametest-shuttle-sable-fixture/.sable/natives/sable_rapier_x86_64_windows.dll`,
  whose SHA-256 matches the archive hash above. This proves extraction, not DLL
  execution or physics use; the fixture did not allocate a sublevel.
- If future adapter source needs Companion API types, establish its narrow
  compile classpath rather than assuming nested jar visibility.
- Complete nested notices/licenses and remaining native/transitive obligations;
  x86_64 Windows PE imports are recorded above. Distribution inventory remains
  incomplete.
- Extend runtime proof to instantiate/operate a Sable sublevel and verify native
  loading after a fixture consumer invokes the physics API.
