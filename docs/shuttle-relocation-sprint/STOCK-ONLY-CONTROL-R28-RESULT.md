# R28 stock-only control: setup failed, no flight result

## Bound evidence

- Candidate `shuttle-feedback08-stock-only-20261016-r28`, snapshot
  `eb653dcfff034cc43c2bdc366787a6f8fd2d1112891d7ea1b80a51ec00049946`.
- Receipt `shuttle-feedback08-stock-only-20261016-r28-gate-validation.json`,
  SHA-256 `0cb73aaee62c663162f181d41f0accb637b864bd4497b08691ada8d40d381417`.
- Raw `feedback08-stock-only-r28-native.log`, 15,102 bytes, SHA-256
  `f0d659280dd4ac74dd68898ef91f40d3ff6617a12b0c5a8be161aa1fea4bc045`.
  Both files are under `build/agent-workflow/shuttle-relocation-sprint/reports/`.

## Established result

Full **control catalogue** selected 2/2, completed 2, passed 0, failed 2 during
setup (raw lines 113–123). Both 452-cell/452-part vanilla shells had mass **903**;
the harness incorrectly required 452 by analogy with the different MS14 shell.
Both pre/post-transfer state/shape snapshots matched fingerprint
`c6cdd7a22cf9c4098132d63c9e03fca1ef79d50e9022426c238f7ea44d9c4bdd`.
Neither reached grounded admission or velocity launch. These are not collision
failures. Receipt parser null counts do not supersede the explicit native
aggregate; no MS14 893-case suite was run.

Runtime inventory contains Minecraft 1.21.1, NeoForge 21.1.228, Sable 2.0.6,
Sable Companion 1.6.0, Veil 4.3.2 and the control mod, **not MS14** (27–39,
86–90). The loaded Sable JAR hash matches the authenticated official artifact
`9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2`.
The nested source binding was logged; exclusion/version/hash guards passed.
The empty mixin-config query is not proof that Sable has no mixins: its own
plugin activity is explicitly logged. Two-substep guards execute before the
failed mass check; there is no launched SDK/trajectory observation.

The game cases completed in 2.450 seconds. Stopping began at 00:54:02, about
405 seconds after their 00:47:17 aggregate. The last phase is `Saving worlds`
(124–126), without per-dimension completion or normal exit. Receipt exit 124,
owned-tree cleanup `terminated`, overall **FAIL**. No thread stack identifies
the blocked operation. Missing cleanup-error logs do not prove save completion.

The freshness hook incorrectly matched `prepareGameTestServer`; the actual task
was `prepareGameTestServerRun` (16). Thus the guard's execution was not proved.
The path was checked absent before submission, but that does not validate the
broken guard. Preserve the original R28 world, logs and frozen plan; never reuse
its fixture for a new check.

## Next correction and limits

R29 retains the vanilla hull and derives expected mass through stock Sable's
resolved per-cell property getter, comparing before/after transfer and actual
tracker mass. It also corrects the preparation dependency and reuses bounded
shutdown sampling without loading MS14. No Sable property/engine change or
production substep adoption is justified.

R28 establishes an isolated authenticated runtime, **not stock-only flight**,
an engine defect, a speed guarantee, absence of an MS14-side contribution to the
original symptom, or readiness/M1 acceptance. Its shutdown failure remains
undiagnosed, like R26; similarity alone does not establish a common cause.
