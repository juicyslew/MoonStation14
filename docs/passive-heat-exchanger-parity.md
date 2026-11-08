# Passive heat exchanger parity

SS14 pinned source (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`), `HeatExchangerSystem.OnAtmosUpdate`: pipe ports equalize before radiator exchange; radiation and convection use the containing environment temperature. There is no cosmic-background fallback. Local flow/profile kernels and paid-flow processing are reused; finite-room exchanges remain transactional. Explicit loaded SPACE exterior retains the local radiation-only sink behavior.

## Exterior thermal boundary extension

The live exchanger accepts only a verified finite cell or an `AtmosphereService.ExteriorBoundary` snapshot. PLANETARY_AMBIENT adapts the existing dimension-fixed ambient mixture as a finite-capacity virtual reservoir: `Result.environmentEnergyDeltaJoules` is the signed receipt (positive means ambient gains heat), without mutating ambient storage. This permits a hot pipe to reject heat to cooler planetary ambient and cooler pipe to receive heat from warmer ambient. This is a deliberate Minecraft adaptation, not exact SS14 parity. Space remains a distinct radiation-only sink; unknown/unloaded/non-exterior cells are not exchange endpoints. Boundary proof is revalidated immediately before pipe commit.
