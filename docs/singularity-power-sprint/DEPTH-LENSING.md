# Singularity depth-aware lensing

## 2026-10-13 owner-directed world-vertex lens (runtime integration; visual acceptance pending)

The rejected finished-image warp kept a foreground copy and produced excessive viewport-edge amplification. The active path now transforms shared native world vertices before rasterization, so each surface is drawn once and native depth resolves opaque occlusion. The original strong resolved response is retained: no strength ceiling, global weakening, viewport-edge fade, or color sampling is added. Gameplay, horizon/contact/death, power/fuel, animated source art (56 frames), and billboard basis are unchanged.

`SingularityWorldLensMath.map(Point, Point, intensity, exponent, cutoffSsPixels, strength, visibility, foregroundRampWorld)` remains the CPU reference; `SingularityRadialResponse.evaluate(q,I,k,R)` owns the response and derivative. The runtime selects existing live entities then manual preview anchors through `SingularityVisualPreview`, consumes resolved `ModSingularityVisuals` profiles (including inherited defaults), and publishes at most five ordered sources. Raw resolved intensity `I` and falloff `k` are passed unchanged; cutoff is `cutoffWorldUnits * PIXELS_PER_METER` (32 SS pixels/world unit), strength is multiplied only by source visibility, and ramp is resolved world units. There is no camera-Z/source-depth ramp or screen-space source admission.

`SingularityWorldShaderSource.wrapVertexSource(List<String>)` wraps only preprocessed native vertex programs. The shared GLSL helper performs the inverse radial branch and is invoked by the `ShaderInstance` uniform bridge. Uniform Count=0 is identity outside an active world frame, under reduced motion, for the dedicated `moonstation14:singularity_core` buffered sprite program, and after frame end. Core sprites use a dedicated ShaderInstance linked against the native entity-cutout programs and COLOR+DEPTH writes; native alpha discard preserves transparent texels, while core depth prevents later geometry from painting over the opaque source. Preview immediate draws use a scoped suspension; buffered core draws are exempt at actual program apply, not merely at enqueue time.

A conservative source-cone AABB test extends native frustum admission only for affected geometry. While active, native directional smart-cull is disabled for that loaded section graph, and native frustum filtering is refreshed each frame. Graph invalidation occurs on lens activation/deactivation or world change, not every interpolated source movement. Native distance/render-distance checks and chunk loading remain unchanged.

The local mapping is a Minecraft 3D adaptation, not literal SS14 2D inner-fold parity. It preserves a single forward branch and avoids the source's literal inner image copies. CPU map regularity is not a guarantee that every finite vanilla triangle remains non-inverted: sparse meshes can facet, and camera-facing screen geometry is remapped in world geometry. Twenty-four bisections per radial solve have measurable GPU cost; only central native compilation and later owner GPU review can establish runtime performance and appearance. No live appearance, pixel acceptance, or GPU result is claimed by JVM tests.

### Radial branch monotonicity proof and tested range

For any positive profile values `I`, `k`, and `R`, let `u=q/R` and `d_raw(q)=I*q^-k*(1-u^4)`. On `0<q<R`,
`d_raw'(q)=-I*q^(-k-1)*(k*(1-u^4)+4*u^4)<0`.
The response seam is `C=(1-h)*raw+h*raw^.3`, with smoothstep `h` from 0 to 1 over raw `[.795,.805]`. There `raw<1`, `h' >= 0`, and
`dC/draw=(1-h)+h*.3*raw^(-.7)+h'*(raw^.3-raw)>0`.
Thus the raw branch, powered branch, and seam all have `C'(q)<0`, for arbitrary positive `k` (not just the six local falloffs). With `F(q)=q*(1-a*C(q))` for any nonnegative finite `a`, whenever `F(q)>0`, `F'(q)=F(q)/q-a*q*C'(q)>F(q)/q>0`. Therefore there is exactly one positive-`F` outer root for every positive input radius below support, independently of the accepted strength being greater than 1. The finite origin extension uses `b=G(.01)` and `m=.01/(b*F'(b))`; it is positive for positive radius and joins value and first derivative at `.01`.

`SingularityWorldLensMathTest` sweeps the six resolved local profiles across both raw seam radii and their amount-dependent forward-input radii, origin, epsilon join, outer support, strength `{0,.01,.25,.5,1,2,8,100}`, visibility `{.5,1}`, and foreground weight `{0,.25,.5,.75,1}`. These numerical checks exercise implementation and precision only; they are not a proof of GPU floating-point behavior or finite triangle orientation. The bisection q-bracket after 24 iterations is at most `(R-r)/2^24`; residual tolerance is derivative-amplified using a dense sampled derivative envelope plus floating-point allowance. The symbolic monotonicity proof above, rather than this sampled envelope, establishes uniqueness.

## 2026-10-12 startup-generator source-occlusion correction

Startup entity remains at generator block center and the startup block remains
present after spawning. The accepted block model's nine authored cuboids occupy
partial volumes (e.g. base y=0..2/16 and narrow stem/ring geometry), but unlike
the already-partial PA and field-generator blocks, its registration inherited
the default full-cube opaque visual occluder. This made its own nine source rays
hit the containing voxel and yielded visibility zero. The scoped correction is
`.noOcclusion()` on this startup block only; default collision/outline, entity,
power, spawn and gameplay behavior are unchanged. No block-ID exception or
general opaque-wall change was introduced.

Visibility unit coverage compares original full-cube stone's zero fraction with
glass under source-centered level-1 probe geometry at cardinal and pitched
views. Registered startup state and unchanged spawn position are GameTest
assertions. This validates the root cause/policy boundary and fixed registration,
not actual GPU pixels. A raw-GL draw attempt terminated its Gradle executor with
exit `-1073741819`; no testcase-level GPU result or matching newly generated
`hs_err_pid` report was found. The exact first-attempt log is retained at
`build/validation-logs/reactor-warp-draw-native.log`; its native crash cause is
unclassified. A subsequent existing compile-only negative-control regression
passes. GPU readback and owner live-warp acceptance remain open.

Current visual status reports active world-frame state, declared source IDs/levels/visibility, and bridge source Count. It does not claim geometry issued or pixels accepted.

The pinned SS14 checkout (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`) was verified. Its root `singularity` uses the 2D client overlay; the overlay consumes `SingularityDistortion` sources without a scene depth buffer. `WarpPoint` plus `GhostOnlyWarp` belongs to ghost navigation, not the distortion-source pipeline. This depth handling is therefore a Minecraft 3D adaptation, not upstream parity.

Prototype audit: `Resources/Prototypes/Entities/Structures/Power/Generation/Singularity/singularity.yml` defines root `Singularity` with no prototype parent. Its composed components include `Singularity` (energy/level/radiation-leveling defaults), `SingularityDistortion` (falloff/intensity), Physics/air movement, EventHorizon, GravityWell, Fixtures, RadiationSource, visualizer/sprite, and auxiliary tags/light/sound/warp/help components. `SharedSingularitySystem` owns level updates and refreshes `SingularityDistortion` (`Content.Shared/Singularity/EntitySystems/SharedSingularitySystem.cs:290-318`); `SingularityOverlay` consumes that component (`Content.Client/Singularity/SingularityOverlay.cs:66-100,152-155`) and `SharedSingularitySystem` does not define the distortion shader. The local resolved `SingularityVisualProfile` carries only presentation fields and live rendering reads it through `ModSingularityVisuals`; Minecraft 3D vertex mapping, eye-space projection, source visibility, and conservative culling are deliberate local adaptations. Gameplay horizon/gravity/power composition is untouched.

The prior framebuffer/camera-Z/depth-capture implementation described in earlier records is retired and is not the active runtime. Its old native draw crash remains historical and unclassified; it was not rerun. Current diagnostics report world-frame source counts and issued/applied uniform counts, not pixels accepted by the GPU. The focused JVM suite verifies CPU mapping, frame packing, source/culling policy, JSON binding and method signatures only; central native compile/client boot and connected-client appearance review remain pending.

Source visibility remains a global 0..1 scalar from the existing nine VISUAL block-ray probes across billboard center/edges/corners, smoothed in the UUID-keyed short-lived cache. It is not alpha-texture-aware or physical exact-layer reconstruction. Pinned SS14 root singularity has no prototype parent; its 2D client overlay consumes `SingularityDistortion` without a scene depth buffer. `WarpPoint`/`GhostOnlyWarp` belong to ghost navigation, not distortion-source rendering. The existing resolved local visual profile remains presentation-only; gameplay horizon, gravity, power, and mortality composition are unchanged. Source references: pinned `Resources/Textures/Shaders/singularity.swsl:15-45`, `Content.Client/Singularity/SingularityOverlay.cs:40,152-155`, and prototype/system audit above.
