# Stomach recipe reactions

Ingested doses are added to the live, character-owned stomach solution and then
passed through `ReagentAttachment.recursiveReaction`, the same stoichiometry and
catalog engine used by other solution destinations. Recipes therefore combine
across ingestions and products are present before the next ordinary digestion
transaction. Reaction sounds are emitted only after the stomach attachment
commit, at the body position.

This change integrates recipes only; ingestion does not dispatch reactive Touch.
Recipe outputs remain subject to the existing strict 50u stomach capacity;
recipes that would exceed it are deferred intact by the shared engine. The
existing recipe model does not implement reaction heat, gas, or explosions; this
change does not claim those outcomes. Per-reagent digestion/effects remain
the existing metabolism-stage responsibility. Tagged native coverage uses the
production Dexalin recipe on a spawned mouse and human, then verifies that the
contained mouse's scheduled stomach-metabolism pass consumes reaction product.
Reaction codecs do not validate reagent quantities; expected invalid-quantity
arithmetic fails closed while the dose remains staged, leaving both holders
unchanged.

SS14 reference: pinned `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, mouse
`MobMouse` inherits animal stomach composition. Local implementation maps that
composition to the resolved `StomachPrototypeComponent` and existing stomach
attachment; the shared generic ingestion consumer applies to any eligible body.
