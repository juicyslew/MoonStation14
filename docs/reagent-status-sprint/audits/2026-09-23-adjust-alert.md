# AdjustAlert implementation boundary

Alert state is stored per living character in a synced/persistent attachment.
The key is `ResourceKey<AlertData>`; presentation metadata is resolved from the
alert prototype catalog. A zero-time clear removes immediately, a positive-time
clear displays until an absolute server tick deadline, and non-clear zero-time
state is persistent. Positive durations are converted at 20 ticks per second;
effect scale is deliberately ignored. Expiry runs in the centralized entity
activity hook and only timed entries activate that activity.

Alerts with equal nonempty prototype categories replace each other. This uses
the server prototype catalog; a missing target metadata definition is rejected,
and a missing definition for an already-stored alert causes a safe failed
adjustment without changing the attachment. Prototype metadata controls HUD
order, localized name and color. HUD presentation is local-player/client-only,
asset-free, and omitted while player, catalog, or prototype data is unavailable.

No death/clone semantics are claimed. The attachment does not opt into
copy-on-death, so behavior follows NeoForge's default attachment clone policy.
Alert icons/assets and category grouping presentation are not implemented.
