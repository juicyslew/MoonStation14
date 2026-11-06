# Native physics coordinate precision — source audit

## Finding

The inspected Sable/Rapier source uses single-precision (`f32`) Rapier coordinates for the dynamic sublevel world pose; it does not establish a native floating-origin or per-island relative-frame scheme. This is a source-level finding, not proof of which exact native binary was loaded at runtime.

At the pinned Sable source revision [`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`](https://github.com/ryanhcode/sable/tree/ad8b4d377741fbd923f4d863b2cf9e462daff9a6), `sable_rapier/src/main/rust/rapier/Cargo.toml` pins RyanHCode's Rapier fork revision `38e92f117590862481a53df6fc69a5d893e29186` with `simd-nightly` and `parallel`. That fork's [`rapier3d/Cargo.toml`](https://github.com/ryanhcode/rapier/blob/38e92f117590862481a53df6fc69a5d893e29186/crates/rapier3d/Cargo.toml) declares default features `dim3`, `f32`, `std` and requires `dim3`, `f32` for its library.

The native entrypoints in [`rapier/src/lib.rs`](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/sable_rapier/src/main/rust/rapier/src/lib.rs) provide concrete path evidence:

- `createSubLevel` reads Java `jdouble`s and casts pose translation components to `Real` for `RigidBodyBuilder.translation`.
- `teleportObject` similarly casts incoming world `jdouble` coordinates to `Real` before assigning `pose.translation`.
- `getPose` widens `rb.translation()` values back to Java doubles; that widening cannot recover discarded precision.
- `lib.rs` imports `marten::Real`; paired with the pinned Rapier `f32` feature this path is float precision.
- Scene initialization, sublevel creation and pose get/set show direct global coordinates. No origin subtraction/rebasing is performed on these paths. The sublevel's voxel octree/local bounds are geometry-local, not a rebased rigid-body world pose.

The matching Java [`RapierPhysicsPipeline`](https://github.com/ryanhcode/sable/blob/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/RapierPhysicsPipeline.java) sends the pose position directly in `add`, passes direct coordinates to `teleportObject`, and reads pose directly in `readPose`. Its initial mass update changes `logicalPose().rotationPoint()` to center of mass; it does not rebase native world coordinates. Thus there is no source-supported public relative-frame/floating-origin API to rely on for this failure.

At world-coordinate magnitudes around 5.4 million and 10.9 million, binary32 spacing is respectively 0.5 and 1.0 world units (block/metre units here). Small horizontal per-step displacement can therefore quantize away, and pose error may accumulate or manifest as large hull/pivot discrepancies. This is consistent with—but does not independently prove the cause of—the observed 7.95 m cabin projected-origin drift over 500-block placement in [ENGINE-PROOF.md](ENGINE-PROOF.md). The observed vertical support behavior and a test applying only vertical motion do not demonstrate horizontal precision at those coordinates.

## Decision gate

**Owner scope decision (2026-10-10):** prebuilt lunar map, approximately 5,000
blocks of useful radius around the moonbase, relatively slow ground-adjacent
shuttles, efficiency prioritized. Keep official f32 Sable/Rapier. Double-precision
derivative/toolchain preparation is cancelled; no candidate native build or Rust
installation exists. The million-block diagnostic is not a normal-flight
acceptance requirement. Validate stock physics at explicitly recorded authored
base/5,000-radius positions; do not impose station-construction bounds or move
owner worlds. Deep-space travel has its own later gate.

Arbitrary-coordinate global flight is not established by this source/runtime path
and is not the current owner requirement. Keep the far-coordinate drift/movement
evidence rather than claiming it is fixed by moving tests. A legitimate one-time
authored COM alignment does not repair ongoing far-coordinate quantization.
No new floating-origin framework, native fork or coordinate clamp is warranted
for the currently stated operating scope; use representative stock-engine tests
and measured limits instead.

The source audit establishes what this source revision builds/configures, not the exact loaded DLL's build provenance. Runtime binary identity/rebuild correspondence remains unverified; validate it before treating this audit as conclusive attribution of the observed runtime symptom.
