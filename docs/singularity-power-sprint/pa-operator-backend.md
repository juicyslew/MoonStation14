# PA operator backend handoff

Backend protocol/session work is present under `ms14/singularity/pa/control`. Body activation is routed only through `BodyHandRequestService.admitBlockUse` / `BodyWorldItemActions.activate`; control changes call the existing `ParticleAcceleratorPartBlockEntity` state methods and assembly rescan. Resolved profile data supplies strength bounds and power demand. The protocol exposes session/target/body identity, authority epoch, profile/revision, assembly and part presence, enabled/strength and power readings. Client registration remains an intentionally installed receiver; no UI/client implementation or connected-client acceptance is claimed.

Operator access currently follows authenticated controlled-body authority, reach and fresh APC power. Upstream `AccessReaderEngineering` presentation is deliberately deferred because the local runtime has no credentials/access-reader consumer; no fake Engineering gate or unsupported component was added. Native AP controls and connected client handshakes are unverified. Existing SS14 reference is the pinned `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; this local body-action adaptation does not implement wire locks, emag/access behavior, or client countdown UI.

## Session and client contract

- Sessions bind viewer, machine placement, profile, body, authority epoch and
  dimension. Live authority/reach/power checks remain server-side.
- Opening snapshots echo the admitted block-use correlation. Action IDs are a
  separate sequence; periodic snapshots repeat the last action ID every five ticks.
- A stale revision consumes a fresh action ID without mutation; replaying that ID
  remains rejected even after the revision changes. Owned CLOSE precedes sequence
  admission. The live service uses `PaControlSessionPolicy.actionAdmission`.
- Client installation uses `PaControlNetworking.installClientReceiver`; actions
  use `requestAction`. Periodic snapshots must never open a window without a
  matching pending activation. Physical part presence comes from the shared
  assembly projection, separately from assembly readiness.

The backend lifecycle build passed 3,234 unit tests before the latest policy
extraction. Source review accepts the new policy and stale-revision/replay tests;
their execution and combined client validation remain pending. Client workspace
implementation is underway. Connected-body/network acceptance is not established.

The subsequent integrated operator build passed **3,241 fresh unit tests**,
assemble and GameTest compilation; **4/4 focused PA** and **823/823 full native**
fixtures passed. Logs: `build/validation-logs/pa-operator-unit-*`,
`pa-operator-focused-*`, `pa-operator-native-full-*`. Workspace, receiver and
admitted activation correlation are implemented and pass source review. These
headless results do not establish connected-client opening/rendering or visual
acceptance; use `root-notes/PA-OPERATOR-MANUAL.md` for the remaining human check.
