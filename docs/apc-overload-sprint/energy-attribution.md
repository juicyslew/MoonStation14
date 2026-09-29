# APC energy attribution

The APC tier remains a shared bus for lamp delivery: the solver aggregates known,
breaker-closed APC inputs and stored batteries, then preserves proportional lamp
brownout. Its delivered lamp power is attributed back to APC meters as each APC's
own known MV input first, followed by battery contribution.

Storage accounting is device-local. Source use is assigned in stable APC ID order;
each APC can charge only from its own MV input left after that assigned load
contribution. Shared battery discharge is apportioned by stored energy, subject to
each battery's 10 kW and finite-energy/time limits. The allocated meter contribution
is exactly the energy decrement for that battery. Open-breaker APCs are isolated
from shared loads and discharge, and may charge only from their own known MV input.
Unknown MV/APC ports contribute neither input nor storage movement.

All stored energy changes use joules (`watts × elapsed seconds`); the existing 100 W
lamp demand and shared proportional brownout behavior are unchanged.
