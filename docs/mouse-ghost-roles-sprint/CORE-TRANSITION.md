# Core ghost-role takeover API

`PlayerLifecycleRegistry.takeOverGhostRoleDurably(CurrentPrimary, SavedLifecycleProfile,
MobHarnessId ghostId, MobHarnessId targetId, String targetDimension,
SavedLifecycleProfile.Location targetLocation, Map<String,String> targetAppearance,
long expectedGeneration, TargetEligibility eligibleTarget)` is the prepared/commit entry point.

The trusted server adapter supplies resolved target metadata; for non-human bodies the appearance
map should identify the resolved local prototype (for example `prototype=ms14:mouse`) and omit
Human-only appearance fields. The core verifies the registered CHARACTER target, eligibility,
unclaimed ownership, exact active lifecycle GHOST/Mind/epoch, and the exact current durable row.
The descriptor target ID is the same ID reserved and committed by the ownership transition; the
registry does not inspect Minecraft entities or attempt to prove world metadata. `SavedLifecycleProfile`
performs structural validation (nonblank dimension/map keys and values, valid finite location, and
immutable appearance data). The runtime caller remains responsible for ensuring those supplied
dimension/location/appearance values actually describe the target entity.

The facade previews through its one-time `LifecycleCapability`, builds a replacement ACTIVE row
preserving account/profile/Mind/schema/store epoch, with target identity/dimension/location/appearance,
incremented generation and revision, and no offline timestamp. It performs `CurrentPrimary.compareAndSwap`
before the capability-restricted registry commit. Thus CAS/preparation failure preserves ghost and
durable row; commit transfers the stable Mind to the target, removes the ghost harness/claim, forfeits
the previous corpse/death claim and advances the control epoch. No client/runtime entity access is in
the core API.
