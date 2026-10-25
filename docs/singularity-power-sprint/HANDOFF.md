# Singularity power sprint handoff

## STATUS

**Closed at the owner's explicit request.** The owner tested the final world-surface lens, described it as looking “REALLY good,” and chose to retain geometry-based rendering despite visible mesh faceting. Pixel-rendering experiments and subdivision are not current scope. Performance has not been formally benchmarked.

Implemented power-loop machinery, finite plasma fuel, radiation protection, six-face containment, emitter/PA operation, singularity reserve/movement/gravity/contact, collectors, authored machinery bindings, animation/audio assets, readable naming, and the accepted live presentation. This session made no commit, merge, installation, or release.

Accepted test artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256 `886DE421C285B3952C1675290BF8470BB07D292700DB6CCDCEE3E97F953E2CDE`. Final technical candidate: `singularity-world-surface-20261013-r1`; reviewed snapshot `155caa2b754522999f1dc714af66de40aad97f96d0edc1857d174bc4c8b504f6`. Closing documentation does not change that tested JAR. Older documents contain historical pending/superseded claims; start here and verify implementation details when extending them.

## ARCHITECTURE

- Gameplay uses resolved typed profiles and existing power/gas/body services. Singularity reserve is not electrical power. PA particles feed reserve; natural drain changes stage; collectors export actual electrical power using finite plasma.
- `FieldGeometry.center` is the canonical block-center anchor. Containment requires eight corners, twelve edges and six continuous faces. Collision/gameplay remain world-space and server-authoritative.
- The gravity clock advances on each server source tick, even without targets. Consumers reuse its cached sample; force values and the accepted ten-tick cadence were not replaced by a speed clamp.
- Presentation transforms native world vertices **before rasterization**. `Program` wraps preprocessed vertex source; `ShaderInstance.apply` uploads frame-scoped parameters. The `LevelRenderer` scope collects live entities and preview anchors through the existing selection/visibility service and resets in `finally`. No active finished-image color/depth-copy warp remains.
- The source-sightline mapping is rotation-covariant: raw intensity/falloff, 32 SS14 pixels per world unit and resolved 20-unit support drive a monotone forward inverse of the original outer radial response. Foreground attenuation reaches zero over a resolved sprite-half-extent-scaled world distance; source-plane/background response is full. There is no displacement ceiling, viewport edge clamp, source averaging or global strength weakening.
- Native depth resolves surface occlusion. The buffered core uses a dedicated lens-exempt native cutout program with color/depth writes; transparent texels discard. Preview sprites suspend the lens for their immediate draw. Source art retains the shared position-facing, world-up billboard basis.
- Conservative source-cone admission supplements native frustum tests without loading chunks or changing render distance. Directional smart-culling is bypassed only while the lens is active; its graph is invalidated on activation/deactivation/world change, not every source movement.

## IMPORTANT FILES

Java paths below are relative to `src/main/java/com/juicyslew/moonstation14/`:

- `entities/SingularityEntity.java`; `ms14/singularity/SingularityGameplayProfile.java`, `SingularityGravityPulse.java`, `SingularityHorizonGeometry.java`.
- `ms14/singularity/containment/FieldGeometry.java`, `FieldContainmentRuntime.java`; `block/block_entity/SingularityGeneratorBlockEntity.java`, `ParticleAcceleratorPartBlockEntity.java`; `ms14/singularity/pa/`; `ms14/radiation/RadiationCollectorBlockEntity.java`.
- `ms14/singularity/SingularityVisualProfile.java`, `ModSingularityVisuals.java`, `SingularityWorldLensMath.java`, `SingularityRadialResponse.java`.
- `ms14/singularity/client/SingularityWorldShaderSource.java`, `SingularityWorldLensClient.java`, `SingularityWorldLensUniforms.java`, `SingularityWorldLensCulling.java`, `SingularityVisualPreview.java`, `SingularityBillboardBasis.java`.
- Client `ProgramSingularityLensMixin`, `ShaderInstanceSingularityLensMixin`, `LevelRendererSingularityLensMixin`, and frustum hook; `entities/client/SingularityRenderer.java`.
- Resources: `shaders/include/singularity_world_lens.glsl`, `shaders/core/singularity_core.json`; `data/moonstation14/singularity_visual/`; animated source textures and `power-reactor-provenance/` under the asset namespace.
- Operator entry point: `docs/singularity-operator-instructions.md`. Supporting indexes: `DEPTH-LENSING.md`, `GRAVITY-PARITY.md`, `HORIZON-CONTACT.md`, `PA-COMPOSITION.md`, `MODEL-BINDINGS.md`, `ANIMATED-VISUALS.md`, `VALIDATION.md` in this folder.

## SS14 CORRESPONDENCE

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; RobustToolbox pin `3136118b5338ef2d9580178caf5c723e65eb76e7`. Root `Singularity` has no prototype parent. Relevant upstream sources are `singularity.yml`, `SharedSingularitySystem`, `SharedGravityWellSystem`, event-horizon systems, `SingularityOverlay`, and `Resources/Textures/Shaders/singularity.swsl`; supporting documents record machinery parent chains.

The horizon radius remains `level - 0.5`, not a tiny central lethal core. Local living-body sphere/AABB contact kills through native death admission and credits energy only after accepted death. Retained corpses and every-tick contact checks differ from upstream deletion/consume waves. Terrain, loose items and recursive container consumption are not implemented.

The world-vertex lens is an explicit Minecraft 3D adaptation, not literal SS14 screen-image inner-fold parity. It preserves raw profile values and support, but selects one forward branch to avoid duplicate images; the threshold seam and tiny origin extension are continuous local numerical adaptations. `WarpPoint` is ghost navigation, not the lens source.

## DECISIONS / WHY

- Compact commissioning stays Level0 creation → Level2 until reserve 260 → Level0 once. READY stops automation writes; no permanent reserve/stage governor. Compact four-block faces cannot fit stage 3. `inactive_wide` has eight-block faces and is manual-only.
- Auxiliary HV supplies startup machinery; collector export serves a separate real 12 kW load. One ordinary Level2 PA supplies at most 15 reserve/second, below stage-4 drain of 20/second. Additional normal feeding is required; Level3 limiter alteration remains unsupported.
- PA art fixes preserve firing roles/direction: control yaw +180°, Port/Starboard art swapped, normal emitter yaw, all seven blockstates `uvlock:false`. Owner-approved R8 cyan indicators retain authored shading and independent charge/connection states; they are texture color, not selective emission.
- Startup-generator `.noOcclusion()` fixes self-occlusion without changing collision, outline or spawn position. Naming resolves to “Singularity.”
- Rejected image-warp variants duplicated foreground and amplified/clamped at viewport edges. Retain the accepted geometry approach; do not restore them as a smoothing shortcut. No subdivision setting or pixel prototype was added.

## KNOWN GAPS

Sparse native meshes visibly facet; continuous mapping does not prove every finite triangle stays uninverted. The shader uses 24 radial bisections; GPU cost, broad culling cost and all graphics-mode combinations remain unbenchmarked. Earlier raw GPU readback crash `-1073741819` and intermittent historical native failures remain unexplained; green reruns did not classify them. Asset credits/provenance remain intact, including 56 animation frames, 289 machinery resources and eight R8 textures.

## VALIDATION

- Final central gate: **78 filtered tests passed**, zero failures/errors/skips: 77 ordinary JVM tests plus one native fixture covering five actual vanilla shader pairs and a reserved-word negative control. Selected/available totals were not reported; this was not a full suite.
- Assembly passed; the JAR hash matches gate evidence. Packaged world helper/core JSON/mixin configuration matched source; six profiles and 56 animation frames were present. Retired image shader files were absent.
- Isolated client shader startup passed with controlled termination of its owned process tree. This proves startup, not world pixels or performance. Successful disposable client directory was removed; bound logs retained. Owner subsequently tested and retained the rendered result; no screenshots were requested.
- Earlier gameplay checkpoint: 3,381 full JVM tests across 664 suites; 17/864 focused native GameTests; full native 864/864, all passed. Quiet-source gravity selection passed 13/864; maximum native step fell from the reproduced 11.269 blocks to 0.239. These precede the final client-render replacement; no new full server suite was claimed for it.
- Final gate reports are under the shared exchange `gate-reports/singularity-world-surface-20261013-r1*`; archived successes and failures remain in `build/validation-logs/`.

## NEXT WORK

No remaining sprint work. If explicitly requested later, measure baseline versus active-lens frame time first. Optional subdivision as a graphics setting is only a possible future direction if performance permits; it is not approved implementation scope. Preserve the current strong response, single-surface rendering, camera-rotation covariance, gameplay authority and asset attribution.
