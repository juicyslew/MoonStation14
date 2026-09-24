# Reagent fixed-point migration audit — 2026-09-23

Reagent components and attachments now own integer hundredths (`long` cents) as
their authoritative quantity. Persistent data continues to use numeric units,
encoded as doubles (`cents / 100d`), so legacy float-shaped JSON/NBT numbers
remain readable and are normalized once by truncating sub-cent values. For
example, legacy `0.125` becomes `0.12`. Negative, non-finite, over-range, and
over-total-capacity quantities are rejected. The supported bound is
2,147,483,647 cents (21,474,836.47 units), above the gameplay puddle limit of
200,000 units and below the range where a double cannot distinguish adjacent
cents.

Legacy float and numeric JSON conversion use deterministic decimal
interpretation (`Float.toString` and `BigDecimal.valueOf`, respectively), then
truncate toward zero after multiplying by 100. No epsilon is applied: a genuine
sub-cent input such as `0.00999999f` remains zero even when converted repeatedly.
This intentionally prioritizes the supplied decimal value over recovering
cent-exactness from arbitrary float arithmetic. If earlier float operations have
already rounded a value to an exact cent (for example a result represented as
`0.01f`), its origin cannot be inferred at this API boundary; it converts as one
cent. Likewise, a nearby value such as `0.9999999f` converts according to its
canonical decimal value, not an inferred intended cent. Internal cent-native
operations avoid this conversion policy entirely.
At large magnitudes float spacing itself can cross cent boundaries: for example,
the Java float literal `200_000.01f` has canonical text `200000.02` and therefore
converts to 20,000,002 cents. Numeric JSON doubles do not have this float-spacing
ambiguity and `200000.01` converts to 20,000,001 cents.

The stream payload now contains registry-aware resource keys and varlong cent
amounts. This is wire protocol v2 and is intentionally incompatible with the
previous FLOAT payload; server and client must both use the same version. Install
matching versions on both ends and restart before reconnecting; do not mix old
and new clients. Existing persistent saves are migrated on read;
the next save writes normalized numeric double values, not strings.

`ReagentComponent.contents()` and attachment float adapters remain for UI and
legacy APIs and are immutable approximate snapshots at large quantities. They
must not be used as authoritative internal transfer data; component/attachment
conversion uses exact cents. Float-based public operations, recipes, reaction
schemas, rendering/tint inputs, and older consumers may still perform float
arithmetic before entering cent storage; their boundary conversion follows the
deterministic decimal truncation policy above and remains a precision-loss
boundary outside authoritative storage and codec round trips. No reagent
prototype rates or effect schemas were changed in this migration.

Stomach-to-body non-digestion transfer also now uses exact cents end-to-end:
each eligible reagent requests at most 25 source cents, and half efficacy
produces `floor(source cents / 2)` body cents. Thus 25 source cents admits 12
body cents; the other 13 cents are intentional transfer inefficiency, not
capacity loss or float-rounding drift. Capacity-limited transfers select the
largest smaller source amount whose floored product fits. A source amount
producing zero body cents is retained as terminal stomach residue pending a
wash/flush owner; a full body retains all source cents. Body metabolism runs on
its next due pass. This localized transfer migration does not imply that
recipe/reaction transformations or metabolite generation are globally
conservative. Other float-shaped schema and public compatibility boundaries
remain approximate.

## Metabolism core cent boundary

Ordinary staged metabolism now reads source authority from `snapshotUnits()` and
uses integer cents for available quantity, configured-rate admission, removal,
destination capacity, product admission, retained product, and explicit excess.
Configured prototype rates and metabolite ratios remain float-shaped schema
inputs: a rate is converted through `ReagentUnits.fromFloat`; a positive rate
that truncates below one cent fails clearly instead of creating a zero-progress
attempt. Products are independently floored from `actual removed cents * ratio`
(double is used only for this bounded ratio multiplication, with finite/range
checks). Effect scale continues to be the existing float ratio of actual cents
to configured rate cents, and callback result/source snapshot APIs remain legacy
float adapters. Ordinary source compartments (body/stomach) are bounded at 1000
units/50 units, where this float callback quantity adapter preserves exact cents.
Products are admitted only to the explicitly routed destination using its own
cent capacity; destination overflow remains explicit in the metabolism report
and is never rerouted to the source stomach. Callback exception precedence and
product-finalization behavior are unchanged. Stage order, per-stage cap, shuffle,
and source/destination single-pass behavior are unchanged.

Generic committed reagent transfers and puddle gravity/flow, stomach-to-body
transfer, and metabolism-core committed amounts are cent-based. This does not
make all material transformations exact: recipe inputs/outputs still originate
as float reaction schemas and reaction resolution is a separate float boundary;
metabolite generation uses a ratio-derived, floored cent result and is not a
global conservation guarantee. Puddle reactions and legacy public APIs also
remain outside this metabolism-core migration. Metabolism callbacks/effect transactions still expose
float-shaped values. Hunger/thirst scalar updates and status timing were not
changed. No claim is made that reaction/puddle float arithmetic is free of
material creation/loss.

Reaction resolution now preflights the whole staged cent result using wide
arithmetic. A valid stoichiometric transform is applied only when every final
reagent amount and the final container total fit both the configured capacity
and supported cent maximum. If the complete result does not fit, resolution is
a mutation-free no-op: inputs and catalysts remain, and existing material is
never trimmed to make room. The existing recursive no-progress guard then ends
that same-tick attempt; a later attempt can succeed once capacity is available.
There is intentionally no automatic overflow spill/routing behavior.
