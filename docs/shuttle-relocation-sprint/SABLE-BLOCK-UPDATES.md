# Sable nested chunk block updates

Sable 2.0.6's `dev.ryanhcode.sable.mixin.plot.LevelChunkMixin` keeps the current
block position in its `sable$blockSet` field. Its HEAD injection assigns the
position and its RETURN injection clears it after `onBlockChange`. The section
write wrapper invokes vanilla first and then reads this field. A legitimate
same-chunk nested `LevelChunk.setBlockState` can therefore clear the outer
context before the outer section wrapper resumes.

`SableLevelChunkBlockChangeContextMixin` wraps the whole vanilla method and
restores the prior field reference in `finally`. Nested writes retain their own
HEAD/RETURN lifecycle, then restore the enclosing position before the outer
section wrapper resumes. The field is Sable-owned; typed Mixin accessors bind its
exact 2.0.6 name after target mixins are applied. Sable is a mandatory runtime
dependency. No Sable plot tracking or block
change implementation is duplicated. The wrapper also restores context on
early return and exceptions. Its selector includes the full JVM return
descriptor (`BlockState`), matching `LevelChunk.setBlockState`'s nullable
`BlockState` return. The wrapper uses priority 900. An initial `@Shadow` approach
failed during pre-merge field lookup despite that priority; late-generated
accessors avoid that failure. No reflection, parallel context stack or copied
physics notification logic is introduced.

Source used: Sable tag commit `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`,
`common/src/main/java/dev/ryanhcode/sable/mixin/plot/LevelChunkMixin.java`.
The release artifact/source correspondence is not claimed reproducible (see
`SABLE-INVENTORY.md`).

`SableNestedBlockChangeGameTests` adds ordinary-world and Sable-plot cases under
`shuttle-block-updates`. Both reuse the real visual pipe and its placement
callback: placing beside an existing primary pipe updates the outer connection
state and the distinct neighbor's reciprocal state, then verifies both BEs,
lane ownership and component membership. The plot case uses the existing
`SableFixturePlot` placement path.

## Validation

- `compileGametestJava assemble` passed:
  `build/shuttle-block-updates-build.log`.
- Filtered `shuttle-block-updates`: **2 selected / 792 discovered, 2 passed**,
  zero failed/incomplete; `build/shuttle-block-updates-native.log`.
- Filtered `ventilation,atmosphere`: **155 selected / 792 discovered, 155 passed**,
  zero failed/incomplete, including both previously failing original pipe cases;
  `build/shuttle-block-updates-originals-native.log`.
- Lead independently checked selected totals, final required-test summaries and
  successful task exits in both retained native logs. This validates startup,
  accessor binding and exercised nested updates, not an unfiltered suite pass.

The earlier failed shadow startup log remains `build/shuttle-envelope-native.log`.
Full-suite certification is handled by the combined stock-engine gate.
