# Stripping backend / UI wire contract

Shared protocol types in `ms14.stripping`:

* `StripViewRequest`: `operation()` OPEN/QUERY/CLOSE, actor `epoch()` and
  `sequence()`, target UUID and an advisory target revision. OPEN captures the
  actual current revision server-side and therefore does not require the client
  to know it beforehand. QUERY refreshes only the existing target+epoch view;
  CLOSE only closes that matching view, and stale sequence requests are ignored.
  Read-only views do not need actor inventory revision. It contains no target
  owner action proof.
* `StripSlotAction`: actor epoch/sequence/current actor inventory revision, target UUID/current target revision,
  and `kind()` plus `slot()` (HAND or EQUIPMENT). The service obtains exact item
  source identity from its own live state; clients never nominate a stack/token.
* `StripViewSnapshot`: epoch/sequence correlation, target UUID, dimension,
  entity incarnation (`entityId`), current `targetRevision`, optional resolved
  `templateId`, actual slot list, status and `canAct`. Use this revision when
  constructing the next `StripSlotAction`. Each `Slot` exposes kind/id, occupied/hidden and
  nullable `ItemPresentation`. Hidden occupied slots must have `item == null`;
  empty slots remain `occupied == false`. `ItemPresentation` removes nested grid
  and container data. The snapshot has no target hand credentials.

Client-side consumer calls `StripNetworking.bindClient(Consumer<StripViewSnapshot>)`
from its sided setup to receive snapshots on the client thread and correlates by
target UUID + epoch + sequence. Server
registration must use the common `RegisterPayloadHandlersEvent` pipeline, MAIN
thread, and delegate requests to the authenticated backend service. No client
class is referenced by these payloads.
