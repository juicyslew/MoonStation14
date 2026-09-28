# M4a — pure power accounting and allocation

## Boundary

This milestone adds only a deterministic, world-independent accounting primitive. It does not
resolve topology, persist device state, schedule simulation, mutate the world, or own equipment,
UI, atmospherics, or players. A future service should resolve the graph and build typed inputs;
it should call the solver only when the component is complete. `graphKnown=false` deliberately
returns zero supply/delivery and leaves storage untouched rather than treating an incomplete
component as disconnected or energized. This is an allocation core, not the M4 station loop.

## Units and cadence

- Rates are watts (joules per second); stored energy and capacity are joules.
- Time advances in fixed server ticks at 20 ticks/second. `elapsedTicks / 20` determines the
  seconds used for charge/discharge. Zero elapsed ticks performs no storage movement.
- Every source declares an output limit and current output; only their nonnegative minimum counts.
  Aggregate source output is capped at `1e12 W` to keep arithmetic bounded.
- Every load declares nonnegative demand. Shortage uses proportional shares (`served / total
  demand`), with stable ID order and a final bounded remainder to avoid order-dependent rounding.
- Generation serves loads first. Storage discharges only to cover remaining demand. Generation
  surplus may charge storage; the solver never charges and discharges the same storage in one
  evaluation. Residual generation is curtailed.
- Storage has explicit energy/capacity, charge and discharge power limits, and separate one-way
  efficiencies. Charge input increases stored energy by `input W * seconds * charge efficiency`;
  discharge output consumes `output W * seconds / discharge efficiency`. This makes losses
  explicit; NaN or zero efficiency is conservatively bounded to `1e-9` to avoid free energy, and
  valid values are clamped to `(0, 1]`.

The solver clamps negative/NaN inputs to zero, positive infinity and large finite inputs to the
numeric ceiling, caps stored energy at capacity, and avoids unbounded/overflowing accounting.
Invalid efficiencies are bounded; use validated configuration in a later service rather than
relying on input coercion as user-facing validation.

## Tier and bridge contract

`Tier` is a closed HV/MV/APC enum. A `Network` has exactly one tier; this solver never merges
network inputs of different tiers. `Bridge` describes two explicit typed ports and supports only
HV→MV substation and MV→APC battery-backed APC kinds. It has an input watt limit, efficiency,
breaker state, and requires both graph sides known for transfer. Transfer reports input, output,
and conversion loss separately. The bridge contract is deliberately directional; the bridge helper
accounts a bounded transfer but does not itself allocate across or join graphs. APC storage is
modeled by storage on an APC-tier network, not by treating the APC bridge as an implicit cable
conversion.

## Deferred service contract

A later server-side service owns graph resolution, identity/lifecycle, cadence, and authoritative
device state. It must pass one fully resolved tier/component at a time, provide a stable unique ID
per source/load, explicitly gate incomplete graph inputs, sequence bridge input/output accounting
without double-counting energy, and persist returned storage energy. It must decide on duplicate
IDs and any policy for incomplete bridge ports before wiring this primitive into gameplay. No
equipment, topology mutation, UI, or world API is part of this contract implementation.

## Evidence and limits

Pure unit tests cover deterministic proportional shortage, 20-tick conversion, source limits,
storage charge/discharge conservation, fail-closed unknown components, bridge direction/breaker/
known-port behavior, and hostile numeric inputs. No manual/in-game tests or performance claims
are made. The API has not yet been integrated with a graph resolver or long-running server cadence;
bridge coupling/order and uniqueness validation remain service responsibilities.
